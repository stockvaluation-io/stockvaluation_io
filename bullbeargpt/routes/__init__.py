"""
Routes package - registers all API blueprints.
All routes prefixed with /bullbeargpt for nginx proxy routing.
Local-first: notebook API only.
"""
from flask import Flask

from .notebook import notebook_bp

# Base prefix for all bullbeargpt routes (for nginx routing)
API_PREFIX = '/bullbeargpt/api'


def register_routes(app: Flask):
    """Register all route blueprints with the app."""
    # Primary API - unified notebook (conversation + cells + saved scenarios)
    app.register_blueprint(notebook_bp, url_prefix=f'{API_PREFIX}/notebook')
