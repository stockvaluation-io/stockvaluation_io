"""
Main Flask application entry point.
Jupyter-style Investment Reasoning API with SSE streaming.
"""
import logging
import os
from flask import Flask
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
        "supports_credentials": True,
        "allow_headers": ["Content-Type", "Authorization", "X-Requested-With"],
        "methods": ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
    }
    if config.CORS_ORIGINS == ["*"]:
        CORS(app, resources={r"/*": {"origins": "*"}}, **cors_kwargs)
    else:
        CORS(app, resources={r"/*": {"origins": config.CORS_ORIGINS}}, **cors_kwargs)
    
    # Register blueprints
    from routes import register_routes
    register_routes(app)
    
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
