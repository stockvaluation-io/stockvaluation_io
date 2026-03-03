"""
LLM client construction and caching.
"""
import json
import logging
import os
import re
import sys
from pathlib import Path
from typing import Any, Dict, Optional, Tuple

from llm.routing import get_use_case_config

logger = logging.getLogger(__name__)

# Make repo root importable so valuation-agent can use shared/llm_models.py.
_repo_root = Path(__file__).resolve().parents[2]
if str(_repo_root) not in sys.path:
    sys.path.insert(0, str(_repo_root))

class LLMClientManager:
    """Creates provider clients lazily and caches them by settings tuple."""

    def __init__(self):
        self._client_cache: Dict[Tuple[str, str, str], Any] = {}

    def get_client(
        self,
        provider: str,
        model: str,
        use_case: str,
        max_tokens: Optional[int] = None,
    ) -> Optional[Any]:
        if not provider or not model:
            logger.error("Provider and model are required to build an LLM client")
            return None

        cache_key = (provider, model, use_case)
        if cache_key in self._client_cache:
            return self._client_cache[cache_key]

        use_case_cfg = get_use_case_config(use_case)
        temperature = use_case_cfg.get("temperature", 0.1)
        model_kwargs = dict(use_case_cfg.get("model_kwargs", {}))

        client = self._create_client(
            provider=provider,
            model=model,
            temperature=temperature,
            model_kwargs=model_kwargs,
            max_tokens=max_tokens,
        )
        if client is None:
            return None

        self._client_cache[cache_key] = client
        logger.debug(
            "Created LLM client provider=%s model=%s use_case=%s temp=%s",
            provider,
            model,
            use_case,
            temperature,
        )
        return client

    def _create_client(
        self,
        provider: str,
        model: str,
        temperature: float,
        model_kwargs: Dict[str, Any],
        max_tokens: Optional[int],
    ) -> Optional[Any]:
        try:
            from shared.llm_models import get_provider_config
            spec = get_provider_config(provider)
        except ImportError:
            logger.error("Could not import shared.llm_models")
            return None

        backend = str(spec.get("backend", "openai")).strip().lower()

        if backend in ("openai", "openai_compat"):
            return self._build_openai_compatible_client(
                model=model,
                temperature=temperature,
                model_kwargs=model_kwargs,
                max_tokens=max_tokens,
                spec=spec,
            )
        if backend == "groq":
            return self._build_groq_client(
                model=model,
                temperature=temperature,
                model_kwargs=model_kwargs,
                max_tokens=max_tokens,
                spec=spec,
            )
        if backend == "anthropic":
            return self._build_anthropic_client(
                model=model,
                temperature=temperature,
                model_kwargs=model_kwargs,
                max_tokens=max_tokens,
                spec=spec,
            )

        logger.error("Unsupported backend '%s' for provider '%s'", backend, provider)
        return None

    def _build_openai_compatible_client(
        self,
        model: str,
        temperature: float,
        model_kwargs: Dict[str, Any],
        max_tokens: Optional[int],
        spec: Dict[str, Any],
    ) -> Optional[Any]:
        api_key = spec.get("api_key", "")
        if not api_key:
            logger.error("%s not configured for provider=%s", spec.get("api_key_env", "API_KEY"), spec.get("provider"))
            return None

        try:
            from langchain_openai import ChatOpenAI

            sanitized_model_kwargs = self._sanitize_openai_compat_model_kwargs(
                provider=str(spec.get("provider", "")).strip().lower(),
                model_kwargs=model_kwargs,
            )

            params: Dict[str, Any] = {
                "model": model,
                "temperature": temperature,
                "openai_api_key": api_key,
            }

            base_url = spec.get("base_url")
            if base_url:
                params["openai_api_base"] = base_url

            # Explicit http_client to bypass httpx version proxy incompatibility
            try:
                import httpx
                params["http_client"] = httpx.Client()
            except Exception:
                pass

            if sanitized_model_kwargs:
                params["model_kwargs"] = sanitized_model_kwargs
            if max_tokens:
                params["max_tokens"] = max_tokens

            return ChatOpenAI(**params)
        except Exception as exc:
            logger.error("Failed to create %s (openai backend) client for model=%s: %s", spec.get("provider"), model, exc)
            return None

    @staticmethod
    def _sanitize_openai_compat_model_kwargs(
        provider: str,
        model_kwargs: Dict[str, Any],
    ) -> Dict[str, Any]:
        if not isinstance(model_kwargs, dict):
            return {}

        sanitized = dict(model_kwargs)
        if provider == "gemini":
            # Gemini OpenAI-compatible endpoint rejects some OpenAI-only fields.
            for key in ("frequency_penalty", "presence_penalty", "logit_bias", "response_format"):
                sanitized.pop(key, None)
        return sanitized

    def _build_groq_client(
        self,
        model: str,
        temperature: float,
        model_kwargs: Dict[str, Any],
        max_tokens: Optional[int],
        spec: Dict[str, Any],
    ) -> Optional[Any]:
        api_key = spec.get("api_key", "")
        if not api_key:
            logger.error("%s not configured for provider=%s", spec.get("api_key_env", "API_KEY"), spec.get("provider"))
            return None

        try:
            from langchain_groq import ChatGroq

            params: Dict[str, Any] = {
                "model": model,
                "temperature": temperature,
                "groq_api_key": api_key,
                "model_kwargs": model_kwargs or {},
            }
            if max_tokens:
                params["max_tokens"] = max_tokens

            return ChatGroq(**params)
        except Exception as exc:
            logger.error("Failed to create %s (groq backend) client for model=%s: %s", spec.get("provider"), model, exc)
            return None

    def _build_anthropic_client(
        self,
        model: str,
        temperature: float,
        model_kwargs: Dict[str, Any],
        max_tokens: Optional[int],
        spec: Dict[str, Any],
    ) -> Optional[Any]:
        api_key = spec.get("api_key", "")
        if not api_key:
            logger.error("%s not configured for provider=%s", spec.get("api_key_env", "API_KEY"), spec.get("provider"))
            return None

        try:
            from langchain_anthropic import ChatAnthropic

            sanitized_model_kwargs = self._sanitize_anthropic_model_kwargs(model_kwargs)

            params: Dict[str, Any] = {
                "model": model,
                "temperature": temperature,
                "anthropic_api_key": api_key,
            }
            if sanitized_model_kwargs:
                params["model_kwargs"] = sanitized_model_kwargs
            if max_tokens:
                params["max_tokens"] = max_tokens
            return ChatAnthropic(**params)
        except Exception as exc:
            logger.error("Failed to create %s (anthropic backend) client for model=%s: %s", spec.get("provider"), model, exc)
            return None

    @staticmethod
    def _sanitize_anthropic_model_kwargs(model_kwargs: Dict[str, Any]) -> Dict[str, Any]:
        if not isinstance(model_kwargs, dict):
            return {}

        sanitized = dict(model_kwargs)
        # OpenAI-style JSON mode payload causes Anthropic SDK/endpoint failures.
        sanitized.pop("response_format", None)
        return sanitized
