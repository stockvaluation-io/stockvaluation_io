"""
LLM routing for valuation-agent.

No agent hardcodes provider/model. Provider and model are selected from shared
role-aware configuration:
- role=agent for normal agent calls
- role=judge for adjudication/validation calls
"""
import json
import logging
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, TypedDict

logger = logging.getLogger(__name__)


# Make repo root importable so valuation-agent can use shared/llm_models.py.
_repo_root = Path(__file__).resolve().parents[2]
if str(_repo_root) not in sys.path:
    sys.path.insert(0, str(_repo_root))

from shared.llm_models import (  # noqa: E402
    get_provider_for_role,
    get_model_for_role,
    list_configured_providers,
)


class ResolvedLLMConfig(TypedDict):
    agent: str
    role: str
    provider: str
    model: str
    use_case: str
    temperature: float
    model_kwargs: Dict[str, Any]
    max_tokens: Optional[int]


DEFAULT_USE_CASE_CONFIG: Dict[str, Dict[str, Any]] = {
    "structured_json": {
        "temperature": 0.1,
        "model_kwargs": {
            "response_format": {"type": "json_object"},
        },
    },
    "strict_json": {
        "temperature": 0.0,
        "model_kwargs": {
            "response_format": {"type": "json_object"},
        },
    },
}

DEFAULT_AGENT_USE_CASE: Dict[str, str] = {
    "analyst": "structured_json",
    "analyzer": "strict_json",
    "narrative": "structured_json",
    "segments": "strict_json",
    "segments_judge": "strict_json",
    "news_judge": "strict_json",
    "earnings_query_generator": "strict_json",
    "news_query_generator": "strict_json",
    "macro_query_generator": "strict_json",
    "segments_query_generator": "strict_json",
}

DEFAULT_AGENT_ROLE: Dict[str, str] = {
    "analyst": "agent",
    "analyzer": "agent",
    "narrative": "agent",
    "segments": "agent",
    "segments_judge": "judge",
    "news_judge": "judge",
    "earnings_query_generator": "agent",
    "news_query_generator": "agent",
    "macro_query_generator": "agent",
    "segments_query_generator": "agent",
}


def _load_json_dict(env_name: str) -> Dict[str, Any]:
    raw = os.getenv(env_name, "").strip()
    if not raw:
        return {}
    try:
        parsed = json.loads(raw)
    except Exception as exc:
        logger.warning("Ignoring %s: invalid JSON (%s)", env_name, exc)
        return {}
    if not isinstance(parsed, dict):
        logger.warning("Ignoring %s: expected JSON object", env_name)
        return {}
    return parsed


def _merge_dict_of_dict(
    base: Dict[str, Dict[str, Any]],
    override: Dict[str, Any],
    source_name: str,
) -> Dict[str, Dict[str, Any]]:
    merged: Dict[str, Dict[str, Any]] = {k: dict(v) for k, v in base.items()}
    for key, value in override.items():
        if isinstance(value, dict):
            merged[key] = {**merged.get(key, {}), **value}
        else:
            logger.warning("Ignoring %s override for %s: expected object", source_name, key)
    return merged


def _normalize_str_map(raw: Dict[str, Any], source_name: str) -> Dict[str, str]:
    normalized: Dict[str, str] = {}
    for key, value in raw.items():
        if isinstance(value, str) and value.strip():
            normalized[str(key)] = value.strip()
        else:
            logger.warning("Ignoring %s override for %s: expected non-empty string", source_name, key)
    return normalized


USE_CASE_CONFIG: Dict[str, Dict[str, Any]] = _merge_dict_of_dict(
    DEFAULT_USE_CASE_CONFIG,
    _load_json_dict("USE_CASE_CONFIG_JSON"),
    "USE_CASE_CONFIG_JSON",
)

AGENT_USE_CASE: Dict[str, str] = {
    **DEFAULT_AGENT_USE_CASE,
    **_normalize_str_map(_load_json_dict("AGENT_USE_CASE_JSON"), "AGENT_USE_CASE_JSON"),
}

AGENT_ROLE: Dict[str, str] = {
    **DEFAULT_AGENT_ROLE,
    **_normalize_str_map(_load_json_dict("AGENT_ROLE_JSON"), "AGENT_ROLE_JSON"),
}


def _coerce_int(value: Any) -> Optional[int]:
    try:
        if value is None:
            return None
        parsed = int(value)
        return parsed if parsed > 0 else None
    except (TypeError, ValueError):
        return None


MODEL_MAX_TOKENS: Dict[str, int] = {
    str(model): parsed
    for model, value in _load_json_dict("MODEL_MAX_TOKENS_JSON").items()
    for parsed in [_coerce_int(value)]
    if parsed is not None
}


def get_default_provider() -> str:
    return get_provider_for_role("agent")


def get_use_case_config(use_case: str) -> Dict[str, Any]:
    default_cfg = USE_CASE_CONFIG["structured_json"]
    selected = USE_CASE_CONFIG.get(use_case, default_cfg)
    return {
        "temperature": selected.get("temperature", default_cfg.get("temperature", 0.1)),
        "model_kwargs": dict(selected.get("model_kwargs", {})),
    }


def resolve_agent_llm(
    agent_name: str,
    provider: Optional[str] = None,
    default_provider: Optional[str] = None,
) -> Optional[ResolvedLLMConfig]:
    role = AGENT_ROLE.get(agent_name, "agent").strip().lower()
    selected_provider = (provider or default_provider or get_provider_for_role(role)).strip().lower()
    selected_model = get_model_for_role(role, selected_provider)
    if not selected_model:
        logger.error("No model resolved for agent=%s role=%s provider=%s", agent_name, role, selected_provider)
        return None

    use_case = AGENT_USE_CASE.get(agent_name, "structured_json")
    use_case_cfg = get_use_case_config(use_case)

    return {
        "agent": agent_name,
        "role": role,
        "provider": selected_provider,
        "model": selected_model,
        "use_case": use_case,
        "temperature": use_case_cfg["temperature"],
        "model_kwargs": use_case_cfg["model_kwargs"],
        "max_tokens": MODEL_MAX_TOKENS.get(selected_model),
    }


def resolve_judge_llm(
    use_case: str = "strict_json",
    provider: Optional[str] = None,
) -> Optional[ResolvedLLMConfig]:
    selected_provider = (provider or get_provider_for_role("judge")).strip().lower()
    selected_model = get_model_for_role("judge", selected_provider)
    if not selected_model:
        logger.error("No model resolved for judge provider=%s", selected_provider)
        return None

    use_case_cfg = get_use_case_config(use_case)
    return {
        "agent": "judge",
        "role": "judge",
        "provider": selected_provider,
        "model": selected_model,
        "use_case": use_case,
        "temperature": use_case_cfg["temperature"],
        "model_kwargs": use_case_cfg["model_kwargs"],
        "max_tokens": MODEL_MAX_TOKENS.get(selected_model),
    }


def list_available_agents() -> List[str]:
    return sorted(AGENT_USE_CASE.keys())


def list_available_providers(agent_name: str) -> List[str]:
    del agent_name
    configured = list_configured_providers()
    return configured or [get_default_provider()]


def validate_agent_llm_config() -> bool:
    is_valid = True
    for agent_name, use_case in AGENT_USE_CASE.items():
        if use_case not in USE_CASE_CONFIG:
            logger.error("Agent %s references unknown use_case=%s", agent_name, use_case)
            is_valid = False
        role = AGENT_ROLE.get(agent_name, "agent")
        if role not in {"agent", "judge"}:
            logger.error("Agent %s has unsupported role=%s", agent_name, role)
            is_valid = False
    return is_valid
