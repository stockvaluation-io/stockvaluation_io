"""
Configuration module for the stockvaluation.io application.
"""
import os
from dotenv import load_dotenv

load_dotenv()

# API Configuration
class APIConfig:
    HOST = '0.0.0.0'
    PORT = 5000
    DEBUG = os.getenv("FLASK_DEBUG", "false").lower() == "true"
    
    
# Feature Flags
class FeatureFlags:
    ENABLE_REAL_OPTIONS = os.getenv("ENABLE_REAL_OPTIONS", "false").lower() == "true"
    
# Cache Configuration
class CacheConfig:
    TYPE = os.getenv("CACHE_TYPE", "SimpleCache")
    DEFAULT_TIMEOUT = 604800 * 2  # 14 days in seconds
    THRESHOLD = 1000  # Maximum cached items
    SQLITE_PATH = "yfinance.cache"
    TTL_HOURS = int(os.getenv("CACHE_TTL_HOURS", "24"))

# Rate Limiting Configuration
class RateLimitConfig:
    REQUESTS_PER_SECOND = int(os.getenv("RATE_LIMIT_REQUESTS_PER_SECOND", "2"))
    DURATION_SECONDS = int(os.getenv("RATE_LIMIT_DURATION_SECONDS", "1"))

# Logging Configuration
class LoggingConfig:
    LEVEL = os.getenv("LOG_LEVEL", "INFO")
    FORMAT = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
