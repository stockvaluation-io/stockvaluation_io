"""Secret redaction helpers for agent-visible payloads."""

from __future__ import annotations

import os
import re
from collections.abc import Mapping, Sequence
from typing import Any

SECRET_NAME_PARTS = (
    "api_key",
    "apikey",
    "secret",
    "password",
    "token",
    "authorization",
)

SECRET_ASSIGNMENT_RE = re.compile(
    r"(?i)\b([A-Z0-9_]*(?:API_KEY|SECRET|PASSWORD|TOKEN|AUTHORIZATION)[A-Z0-9_]*)=([^\s,;]+)"
)


def _is_secret_name(name: str) -> bool:
    lowered = name.lower()
    return any(part in lowered for part in SECRET_NAME_PARTS)


def _known_secret_values() -> list[str]:
    values: list[str] = []
    for key, value in os.environ.items():
        if _is_secret_name(key) and len(value.strip()) >= 4:
            values.append(value)
    return values


def _sanitize_string(value: str) -> str:
    cleaned = value
    for secret in _known_secret_values():
        cleaned = cleaned.replace(secret, "[REDACTED]")
    return SECRET_ASSIGNMENT_RE.sub(lambda match: f"{match.group(1)}=[REDACTED]", cleaned)


def sanitize_for_agent(value: Any) -> Any:
    """Return a copy of value with secret-looking keys and known secret values redacted."""
    if isinstance(value, Mapping):
        redacted: dict[str, Any] = {}
        for key, item in value.items():
            key_text = str(key)
            if _is_secret_name(key_text):
                redacted[key_text] = "[REDACTED]"
            else:
                redacted[key_text] = sanitize_for_agent(item)
        return redacted

    if isinstance(value, str):
        return _sanitize_string(value)

    if isinstance(value, Sequence) and not isinstance(value, (bytes, bytearray)):
        return [sanitize_for_agent(item) for item in value]

    return value
