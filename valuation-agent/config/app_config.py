"""
Configuration module for the stockvaluation.io application.
"""
import os
from dotenv import load_dotenv

load_dotenv()

# API Configuration
class APIConfig:
    HOST = '0.0.0.0'
    PORT = 5001
    DEBUG = os.getenv("FLASK_DEBUG", "false").lower() == "true"

# LLM Configuration
class LLMConfig:
    MAX_NEWS_RESULTS = int(os.getenv("MAX_NEWS_RESULTS", "5"))
