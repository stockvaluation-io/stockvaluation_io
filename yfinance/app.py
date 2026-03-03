"""
Refactored Flask application for stockvaluation.io - Yfinance Service
"""
import json
import logging
import os
import secrets
from typing import Any, Callable, Dict
from flask import Flask, Response, abort, jsonify, request
from flask_caching import Cache
from flask_cors import CORS

from config import APIConfig, CacheConfig
from service import YFinanceService

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

DEFAULT_CORS_ORIGINS = [
    "http://localhost:4200",
    "http://127.0.0.1:4200",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
]


def _parse_cors_origins(raw: str) -> list[str]:
    cleaned = (raw or "").strip()
    if not cleaned:
        return list(DEFAULT_CORS_ORIGINS)
    if cleaned == "*":
        allow_all = os.getenv("CORS_ALLOW_ALL", "false").lower() == "true"
        if allow_all:
            return ["*"]
        logger.warning("CORS_ORIGINS='*' ignored unless CORS_ALLOW_ALL=true; using localhost defaults")
        return list(DEFAULT_CORS_ORIGINS)
    origins = [origin.strip() for origin in cleaned.split(",") if origin.strip()]
    return origins or list(DEFAULT_CORS_ORIGINS)


def _resolve_secret_key() -> str:
    configured = os.getenv("SECRET_KEY", "").strip()
    if configured:
        return configured
    logger.warning("SECRET_KEY is not set; using ephemeral process-local key")
    return secrets.token_urlsafe(48)

def make_cache_key() -> str:
    """Generate cache key for API endpoints."""
    ticker = request.args.get('ticker', '').upper()
    freq = request.args.get('freq', 'yearly').lower()
    return f"{request.path}|{ticker}|{freq}"

class YFinanceApp:
    """Main application class for YFinance Service."""
    
    def __init__(self):
        self.app = Flask(__name__)
        self.setup_config()
        self.cache = Cache(self.app)

        # Setup CORS with explicit allowlist.
        cors_origins = _parse_cors_origins(os.getenv("CORS_ORIGINS", ""))
        CORS(
            self.app,
            resources={r"/*": {"origins": cors_origins}},
            supports_credentials=False,
            allow_headers=["Content-Type", "Authorization", "X-Requested-With"],
            methods=["GET", "POST", "OPTIONS"],
        )
        
        self.yfinance_service = YFinanceService()
        self.setup_routes()
    
    def setup_config(self):
        """Configure Flask application."""
        self.app.config.update({
            'CACHE_TYPE': CacheConfig.TYPE,
            'CACHE_DEFAULT_TIMEOUT': CacheConfig.DEFAULT_TIMEOUT,
            'CACHE_THRESHOLD': CacheConfig.THRESHOLD,
            'SECRET_KEY': _resolve_secret_key(),
        })
    
    def setup_routes(self):
        """Setup Flask routes."""
        @self.app.route('/health')
        def health_check():
            """Health check endpoint for Docker."""
            return jsonify({
                "status": "healthy",
                "service": "stockvaluation.io.yfinance",
                "version": "2.0.0"
            }), 200

        @self.app.get('/api-s/<endpoint>')
        def respond(endpoint: str) -> Response:
            ticker: str = request.args.get('ticker')
            freq: str = request.args.get('freq', 'yearly')

            if ticker is None:
                abort(400, description='Ticker not given')

            endpoint_map: Dict[str, Callable] = self.yfinance_service.get_endpoint_map()

            if endpoint not in endpoint_map:
                abort(404, description='Endpoint not found')

            # Bypass cache for 'fast-info' and 'info' endpoints
            if endpoint in ['fast-info', 'info']:
                result: Any = endpoint_map[endpoint](ticker.upper(), freq)
            else:
                # Use cached result for other endpoints
                @self.cache.cached(key_prefix=make_cache_key)
                def cached_response():
                    return endpoint_map[endpoint](ticker.upper(), freq)

                result: Any = cached_response()

            if isinstance(result, str):
                result = json.loads(result)

            return jsonify(result)

# For running via WSGI server
app_instance = YFinanceApp()
app = app_instance.app

if __name__ == '__main__':
    port = int(os.environ.get("PORT", 5000))
    app.run(host='0.0.0.0', port=port, debug=APIConfig.DEBUG)
