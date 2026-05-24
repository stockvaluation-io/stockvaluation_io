"""HTTP client for the local deterministic valuation service."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Any
from urllib import error, parse, request

from .security import sanitize_for_agent

DEFAULT_SERVICE_URL = "http://localhost:8081/api/v1/automated-dcf-analysis"
DEFAULT_TIMEOUT_SECONDS = 180


class ValuationServiceError(Exception):
    """Base class for valuation-service failures."""


class ServiceUnavailable(ValuationServiceError):
    """Raised when the local service cannot be reached."""


class NonJsonServiceResponse(ValuationServiceError):
    """Raised when the local service responds with non-JSON content."""


@dataclass
class ServiceHTTPError(ValuationServiceError):
    """Raised when valuation-service returns an error status."""

    status: int
    message: str
    payload: dict[str, Any] | None = None

    def __str__(self) -> str:
        return self.message


class ValuationServiceClient:
    """Small stdlib-only client around the local valuation-service API."""

    def __init__(self, base_url: str | None = None, timeout: int | None = None):
        configured = base_url or os.getenv("STOCKVALUATION_SERVICE_URL") or DEFAULT_SERVICE_URL
        self.base_url = configured.rstrip("/")
        configured_timeout = os.getenv("STOCKVALUATION_SERVICE_TIMEOUT_SECONDS", "").strip()
        if timeout is None and configured_timeout:
            try:
                timeout = int(configured_timeout)
            except ValueError:
                timeout = DEFAULT_TIMEOUT_SECONDS
        self.timeout = timeout or DEFAULT_TIMEOUT_SECONDS

    def health(self) -> dict[str, Any]:
        return self._get_json(self._health_url())

    def value_ticker(self, ticker: str, overrides: dict[str, Any] | None = None) -> dict[str, Any]:
        url = f"{self.base_url}/{parse.quote(ticker)}/valuation"
        payload = self._post_json(url, overrides or {})
        data = payload.get("data") if isinstance(payload, dict) else None
        if not isinstance(data, dict):
            raise NonJsonServiceResponse("valuation-service response missing JSON data object")
        return data

    def _health_url(self) -> str:
        explicit = os.getenv("STOCKVALUATION_HEALTH_URL", "").strip()
        if explicit:
            return explicit
        parsed = parse.urlsplit(self.base_url)
        return parse.urlunsplit((parsed.scheme, parsed.netloc, "/actuator/health", "", ""))

    def _get_json(self, url: str) -> dict[str, Any]:
        req = request.Request(url, method="GET")
        return self._send(req)

    def _post_json(self, url: str, body: dict[str, Any]) -> dict[str, Any]:
        encoded = json.dumps(body).encode("utf-8")
        req = request.Request(
            url,
            data=encoded,
            method="POST",
            headers={"Content-Type": "application/json", "Accept": "application/json"},
        )
        return self._send(req)

    def _send(self, req: request.Request) -> dict[str, Any]:
        try:
            with request.urlopen(req, timeout=self.timeout) as response:
                raw = response.read().decode("utf-8")
                return self._decode_json(raw, response.status)
        except error.HTTPError as exc:
            raw = exc.read().decode("utf-8", errors="replace")
            payload: dict[str, Any] | None
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                payload = None
            message = "valuation-service error"
            if isinstance(payload, dict):
                message = str(payload.get("message") or payload.get("error") or message)
            raise ServiceHTTPError(
                status=exc.code,
                message=str(sanitize_for_agent(message)),
                payload=sanitize_for_agent(payload) if isinstance(payload, dict) else None,
            ) from exc
        except error.URLError as exc:
            raise ServiceUnavailable(str(sanitize_for_agent(str(exc.reason)))) from exc
        except TimeoutError as exc:
            raise ServiceUnavailable("valuation-service request timed out") from exc

    @staticmethod
    def _decode_json(raw: str, status: int) -> dict[str, Any]:
        try:
            data = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise NonJsonServiceResponse(f"valuation-service non-JSON response (status={status})") from exc
        if not isinstance(data, dict):
            raise NonJsonServiceResponse("valuation-service response was not a JSON object")
        return data
