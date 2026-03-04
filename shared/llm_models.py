"""
Centralized LLM provider and model configuration.

DESIGN PHILOSOPHY
-----------------
- User configures ONE .env at the repo root. All services read from it.
- User plugs in ONE API key. Service figures out the rest.
- Default model = latest stable for that provider (updated here, nowhere else).
- Universal override: set LLM_MODEL=<model-id> in .env.
- Provider-specific override: set <PROVIDER>_MODEL=<model-id> (e.g. OPENAI_MODEL=gpt-4o-mini).

SUPPORTED PROVIDERS
-------------------
  claude      → ANTHROPIC_API_KEY   → Anthropic SDK
  openai      → OPENAI_API_KEY      → OpenAI SDK
  groq        → GROQ_API_KEY        → OpenAI-compatible (Groq base URL)
  gemini      → GEMINI_API_KEY      → OpenAI-compatible (Google AI base URL)
  openrouter  → OPENROUTER_API_KEY  → OpenAI-compatible (openrouter base URL)

USAGE
-----
  from shared.llm_models import get_active_provider, get_default_model, get_provider_config

  provider = get_active_provider()         # auto-detect from env keys
  model    = get_default_model(provider)   # latest stable for that provider
  cfg      = get_provider_config(provider) # full config dict
"""
import logging
import os
from typing import Any, Dict, Optional

# ---------------------------------------------------------------------------
# Load .env from repository root (one level above shared/)
# All services share this single .env file.
# ---------------------------------------------------------------------------
_repo_root = os.path.dirname(os.path.abspath(__file__))  # shared/
_repo_root = os.path.dirname(_repo_root)                  # stockvaluation_io_devops/
try:
    from dotenv import load_dotenv
    load_dotenv(os.path.join(_repo_root, '.env'), override=False)
except ImportError:
    pass  # python-dotenv not installed in this env; env vars must be set externally

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Master provider registry
# ---------------------------------------------------------------------------
# Each entry defines:
#   api_key_env   : environment variable for the API key
#   default_model : latest stable model (update this when providers release new ones)
#   model_env     : env var the user can set to override just this provider's model
#   backend       : 'anthropic' | 'openai' | 'openai_compat'
#   base_url      : base URL for openai_compat providers (None = use SDK default)
#   base_url_env  : env var to override the base URL
#
# VERIFIED MODEL IDs (March 2026)
# - claude-sonnet-4-6       : lower-cost Anthropic default for local runs
# - gpt-4o                : OpenAI flagship (widely available, stable)
# - llama-3.3-70b-versatile : Groq's recommended default (Llama 4 when released)
# - gemini-2.5-flash      : Google GA since June 2025 (fast, cost-efficient)
# - anthropic/claude-sonnet-4 : via OpenRouter
#
PROVIDER_REGISTRY: Dict[str, Dict[str, Any]] = {
    "claude": {
        "api_key_env": "ANTHROPIC_API_KEY",
        "default_model": "claude-sonnet-4-6",
        "model_env": "ANTHROPIC_MODEL",
        "backend": "anthropic",
        "base_url": None,
        "base_url_env": None,
        "display_name": "Anthropic Claude",
    },
    "openai": {
        "api_key_env": "OPENAI_API_KEY",
        "default_model": "gpt-4o",
        "model_env": "OPENAI_MODEL",
        "backend": "openai",
        "base_url": None,
        "base_url_env": "OPENAI_BASE_URL",
        "display_name": "OpenAI",
    },
    "groq": {
        "api_key_env": "GROQ_API_KEY",
        "default_model": "llama-3.3-70b-versatile",
        "model_env": "GROQ_MODEL",
        "backend": "openai_compat",
        "base_url": "https://api.groq.com/openai/v1",
        "base_url_env": None,
        "display_name": "Groq (Llama)",
    },
    "gemini": {
        "api_key_env": "GEMINI_API_KEY",
        "default_model": "gemini-2.5-flash",
        "model_env": "GEMINI_MODEL",
        "backend": "openai_compat",
        "base_url": "https://generativelanguage.googleapis.com/v1beta/openai/",
        "base_url_env": None,
        "display_name": "Google Gemini",
    },
    "openrouter": {
        "api_key_env": "OPENROUTER_API_KEY",
        "default_model": "anthropic/claude-sonnet-4",
        "model_env": "OPENROUTER_MODEL",
        "backend": "openai_compat",
        "base_url": "https://openrouter.ai/api/v1",
        "base_url_env": "OPENROUTER_BASE_URL",
        "display_name": "OpenRouter",
    },
}

# Auto-detect priority: first key present wins
_AUTO_DETECT_ORDER = ["claude", "openai", "groq", "gemini", "openrouter"]

# Fallback when no key is present (will fail gracefully with a clear error)
_FALLBACK_PROVIDER = "openai"

_ROLE_PROVIDER_ENV = {
    "agent": "AGENT_LLM_PROVIDER",
    "judge": "JUDGE_LLM_PROVIDER",
}

_ROLE_MODEL_ENV = {
    "agent": "AGENT_LLM_MODEL",
    "judge": "JUDGE_LLM_MODEL",
}


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def get_active_provider() -> str:
    """
    Return the active LLM provider.

    Resolution order:
      1. DEFAULT_LLM_PROVIDER env var (explicit user choice)
      2. Auto-detect from which API key is present (priority order: claude → openai → groq → gemini → openrouter)
      3. Fall back to 'openai' (will show a clear error when used without a key)
    """
    # 1. Explicit override
    explicit = os.getenv("DEFAULT_LLM_PROVIDER", "").strip().lower()
    if explicit and explicit in PROVIDER_REGISTRY:
        return explicit
    if explicit:
        logger.warning(
            "DEFAULT_LLM_PROVIDER='%s' is unknown. Valid: %s. Falling back to auto-detect.",
            explicit, ", ".join(PROVIDER_REGISTRY.keys())
        )

    # 2. Auto-detect
    for provider in _AUTO_DETECT_ORDER:
        key_env = PROVIDER_REGISTRY[provider]["api_key_env"]
        if os.getenv(key_env, "").strip():
            logger.debug("Auto-detected provider '%s' from %s", provider, key_env)
            return provider

    # 3. Fallback
    logger.warning(
        "No LLM API key found. Configure one of: %s. Defaulting to '%s'.",
        ", ".join(cfg["api_key_env"] for cfg in PROVIDER_REGISTRY.values()),
        _FALLBACK_PROVIDER,
    )
    return _FALLBACK_PROVIDER


def get_default_model(provider: Optional[str] = None) -> str:
    """
    Return the default model for a provider.

    Resolution order:
      1. Universal override: LLM_MODEL env var
      2. Provider-specific override: e.g. ANTHROPIC_MODEL, OPENAI_MODEL
      3. Built-in latest default from PROVIDER_REGISTRY

    Args:
        provider: Provider name (e.g. 'claude', 'openai'). Defaults to active provider.
    """
    provider = (provider or get_active_provider()).strip().lower()
    cfg = PROVIDER_REGISTRY.get(provider)

    if not cfg:
        raise ValueError(f"Unknown provider '{provider}'. Valid providers: {', '.join(PROVIDER_REGISTRY.keys())}")

    # 1. Universal override (highest priority)
    universal = os.getenv("LLM_MODEL", "").strip()
    if universal:
        return universal

    # 2. Provider-specific override
    model_env = cfg.get("model_env", "")
    if model_env:
        specific = os.getenv(model_env, "").strip()
        if specific:
            return specific

    # 3. Built-in latest default
    return cfg["default_model"]


def get_provider_config(provider: Optional[str] = None) -> Dict[str, Any]:
    """
    Return the full config dict for a provider, with resolved api_key and model.

    Returns a dict with:
        provider    : provider name
        backend     : 'anthropic' | 'openai' | 'openai_compat'
        api_key     : resolved API key (may be empty string — caller should check)
        model       : resolved model name
        base_url    : base URL (may be None for native SDK providers)
        display_name: human-readable provider name
    """
    provider = (provider or get_active_provider()).strip().lower()
    cfg = PROVIDER_REGISTRY.get(provider, PROVIDER_REGISTRY[_FALLBACK_PROVIDER])

    api_key = os.getenv(cfg["api_key_env"], "").strip()
    model = get_default_model(provider)

    # Resolve base_url (env var override takes precedence)
    base_url = cfg["base_url"]
    if cfg.get("base_url_env"):
        base_url = os.getenv(cfg["base_url_env"], "").strip() or base_url

    if not api_key:
        logger.warning(
            "Provider '%s': %s is not set. LLM calls will fail.",
            provider, cfg["api_key_env"]
        )

    return {
        "provider": provider,
        "backend": cfg["backend"],
        "api_key": api_key,
        "api_key_env": cfg["api_key_env"],
        "model": model,
        "base_url": base_url,
        "display_name": cfg.get("display_name", provider),
    }


def list_configured_providers() -> list:
    """Return list of providers that have an API key set."""
    return [
        p for p in _AUTO_DETECT_ORDER
        if os.getenv(PROVIDER_REGISTRY[p]["api_key_env"], "").strip()
    ]


def is_provider_configured(provider: str) -> bool:
    """Return True if the given provider has an API key set."""
    cfg = PROVIDER_REGISTRY.get(provider.strip().lower())
    if not cfg:
        return False
    return bool(os.getenv(cfg["api_key_env"], "").strip())


def get_provider_for_role(role: str = "agent") -> str:
    """
    Resolve provider for a logical role.

    Roles:
      - agent: default provider for primary agent calls
      - judge: provider for adjudication/validation calls

    Resolution:
      1) <ROLE>_LLM_PROVIDER (explicit role override)
      2) DEFAULT_LLM_PROVIDER (global explicit provider)
      3) auto-detect configured providers from keys
      4) for judge only: if multiple configured providers, use one different from agent
      5) fallback provider
    """
    role = (role or "agent").strip().lower()
    role_env = _ROLE_PROVIDER_ENV.get(role)
    explicit_role = os.getenv(role_env, "").strip().lower() if role_env else ""
    if explicit_role:
        if explicit_role in PROVIDER_REGISTRY:
            return explicit_role
        logger.warning("Ignoring %s='%s' (unknown provider)", role_env, explicit_role)

    explicit_global = os.getenv("DEFAULT_LLM_PROVIDER", "").strip().lower()
    if explicit_global and explicit_global in PROVIDER_REGISTRY:
        if role == "judge":
            configured = list_configured_providers()
            if len(configured) >= 2:
                for provider in configured:
                    if provider != explicit_global:
                        return provider
        return explicit_global

    configured = list_configured_providers()
    if role == "judge":
        agent_provider = get_provider_for_role("agent")
        for provider in configured:
            if provider != agent_provider:
                return provider
        return agent_provider

    if configured:
        return configured[0]
    return _FALLBACK_PROVIDER


def get_model_for_role(role: str = "agent", provider: Optional[str] = None) -> str:
    """
    Resolve model for a logical role and provider.

    Resolution:
      1) <ROLE>_LLM_MODEL (role-specific model override)
      2) LLM_MODEL (global model override)
      3) provider-specific model env (e.g. OPENAI_MODEL)
      4) default model from provider registry
    """
    role = (role or "agent").strip().lower()
    provider = (provider or get_provider_for_role(role)).strip().lower()

    role_model_env = _ROLE_MODEL_ENV.get(role)
    role_model = os.getenv(role_model_env, "").strip() if role_model_env else ""
    if role_model:
        return role_model

    return get_default_model(provider)


def get_provider_config_for_role(role: str = "agent") -> Dict[str, Any]:
    """
    Return provider config with role-aware provider/model selection.
    """
    provider = get_provider_for_role(role)
    cfg = get_provider_config(provider)
    cfg["role"] = role
    cfg["model"] = get_model_for_role(role, provider)
    return cfg
