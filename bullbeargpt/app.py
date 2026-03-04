"""
Main Flask application entry point.
Jupyter-style Investment Reasoning API with SSE streaming.
"""
import logging
import os
import secrets
from flask import Flask, jsonify, request
from flask_cors import CORS

from config import get_config

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def create_app():
    """Application factory pattern."""
    app = Flask(__name__)
    
    # Load configuration
    config = get_config()
    app.config.from_object(config)
    
    # Enable CORS
    cors_kwargs = {
        "supports_credentials": False,
        "allow_headers": ["Content-Type", "Authorization", "X-Requested-With"],
        "methods": ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    }
    CORS(app, resources={r"/*": {"origins": config.CORS_ORIGINS}}, **cors_kwargs)
    
    # Register blueprints
    from routes import register_routes
    register_routes(app)

    internal_api_key = os.getenv("INTERNAL_API_KEY", "").strip()

    @app.before_request
    def _optional_internal_auth():
        # Keep health unauthenticated for container healthchecks.
        if request.path == "/health":
            return None

        if not internal_api_key:
            return None

        header_key = (request.headers.get("X-Internal-API-Key") or "").strip()
        if header_key and secrets.compare_digest(header_key, internal_api_key):
            return None

        auth_header = (request.headers.get("Authorization") or "").strip()
        if auth_header.startswith("Bearer "):
            bearer_token = auth_header[7:].strip()
            if bearer_token and secrets.compare_digest(bearer_token, internal_api_key):
                return None

        return jsonify({"error": "unauthorized"}), 401
    
    # Health check endpoint
    @app.route('/health')
    def health():
        return {'status': 'healthy', 'service': 'bullbeargpt-api'}
    
    logger.info("BullBearGPT API initialized (SSE streaming, CORS origins=%s)", config.CORS_ORIGINS)
    
    return app


# Create app instance
app = create_app()


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=bool(app.config.get("DEBUG", False)))
