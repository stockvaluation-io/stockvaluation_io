"""
Configuration module for the Flask application.
Handles environment variables and app configuration.
Local-first: no Supabase, no account-service.

LLM PROVIDER SETUP
------------------
Set ONE API key in your .env:
  ANTHROPIC_API_KEY=...     → Claude (auto-selected first if present)
  OPENAI_API_KEY=...        → GPT-4o
  GROQ_API_KEY=...          → Llama 3.3 70B (fast, cheap)
  GEMINI_API_KEY=...        → Gemini 2.5 Pro
  OPENROUTER_API_KEY=...    → Any model via openrouter

The service auto-detects which provider to use.
Model defaults = latest stable (defined in shared/llm_models.py).

Optional overrides:
  AGENT_LLM_PROVIDER=<provider-id>   (force a provider)
  AGENT_LLM_MODEL=gpt-4o-mini        (force a specific model)
"""
import os
import secrets
import sys
from dotenv import load_dotenv

load_dotenv()

# Add shared/ to path so we can import shared.llm_models
_repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from shared.llm_models import get_provider_for_role

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
        return ["*"]
    origins = [origin.strip() for origin in cleaned.split(",") if origin.strip()]
    return origins or list(DEFAULT_CORS_ORIGINS)


def _resolve_secret_key() -> str:
    configured = os.getenv("SECRET_KEY", "").strip()
    if configured:
        return configured
    return secrets.token_urlsafe(48)


class Config:
    """Base configuration class."""

    # Flask
    SECRET_KEY = _resolve_secret_key()
    DEBUG = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'

    # LLM — resolved via shared/llm_models.py (no hardcoded model names here)
    DEFAULT_LLM_PROVIDER = get_provider_for_role("agent")

    # Prompt Dumping (for debugging and cost analysis)
    DUMP_PROMPTS = os.getenv('DUMP_PROMPTS', 'false').lower() == 'true'
    PROMPT_DUMP_DIR = os.getenv('PROMPT_DUMP_DIR', 'prompt_dump')

    # CORS
    CORS_ORIGINS = _parse_cors_origins(os.getenv('CORS_ORIGINS', ""))

    # Valuation service API
    VALUATION_SERVICE_URL = (
        os.getenv('VALUATION_SERVICE_URL')
        or os.getenv('JAVA_DCF_API_URL')
        or 'http://valuation-service:8081/api/v1/automated-dcf-analysis'
    )
    # Backward compatibility for older references.
    JAVA_DCF_API_URL = VALUATION_SERVICE_URL

    # Valuation Agent API (source of persisted valuations / orchestration output)
    VALUATION_AGENT_URL = os.getenv('VALUATION_AGENT_URL', 'http://valuation-agent:5001')

    # Local data directory for SQLite persistence
    LOCAL_DATA_DIR = os.getenv('LOCAL_DATA_DIR', 'local_data')


class DevelopmentConfig(Config):
    """Development configuration."""
    DEBUG = os.getenv('FLASK_DEBUG', 'false').lower() == 'true'


class ProductionConfig(Config):
    """Production configuration."""
    DEBUG = False


def get_config():
    """Return the appropriate config based on environment."""
    env = os.getenv('FLASK_ENV', 'production')
    if env == 'production':
        return ProductionConfig()
    return DevelopmentConfig()

