"""
Client for the Java valuation-service (DCF engine + raw data provider facade).
"""
import logging
import os
from typing import Any, Dict, Optional

import requests

logger = logging.getLogger(__name__)


class ValuationServiceClientError(Exception):
    """Raised when the valuation-service request fails."""


class ValuationServiceClient:
    """Thin HTTP client for Java valuation-service endpoints."""

    def __init__(self, base_url: Optional[str] = None, timeout: int = 60):
        explicit = base_url or os.getenv("VALUATION_SERVICE_URL")
        if explicit:
            self.base_url = explicit.rstrip("/")
        else:
            service_base = (
                os.getenv("VALUATION_SERVICE_BASE_URL")
                or "http://valuation-service:8081"
            ).rstrip("/")
            self.base_url = f"{service_base}/api/v1/automated-dcf-analysis"
        configured_timeout = os.getenv("VALUATION_SERVICE_TIMEOUT_SECONDS", "").strip()
        if configured_timeout:
            try:
                timeout = int(configured_timeout)
            except ValueError:
                logger.warning(
                    "Invalid VALUATION_SERVICE_TIMEOUT_SECONDS='%s'; using default timeout=%s",
                    configured_timeout,
                    timeout,
                )
        self.timeout = timeout

    def get_baseline_valuation(
        self,
        ticker: str,
        auth_header: Optional[str] = None,
        overrides: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """
        Fetch baseline valuation from Java without AI callback.

        Uses POST /{ticker}/valuation with empty overrides. That path runs with
        enableDCFAnalysis=false in Java and avoids circular callbacks.
        """
        return self._valuation_output(
            ticker=ticker,
            overrides=overrides or {},
            auth_header=auth_header,
        )

    def recalculate_valuation(
        self,
        ticker: str,
        overrides: Dict[str, Any],
        auth_header: Optional[str] = None,
    ) -> Dict[str, Any]:
        """Recalculate DCF using Java valuation-service with override payload."""
        return self._valuation_output(ticker=ticker, overrides=overrides, auth_header=auth_header)

    def _valuation_output(
        self,
        ticker: str,
        overrides: Dict[str, Any],
        auth_header: Optional[str] = None,
    ) -> Dict[str, Any]:
        url = f"{self.base_url}/{ticker}/valuation"
        headers = {
            "Content-Type": "application/json",
        }
        if auth_header:
            headers["Authorization"] = auth_header

        try:
            resp = requests.post(
                url,
                json=overrides or {},
                headers=headers,
                timeout=self.timeout,
            )
        except requests.RequestException as exc:
            raise ValuationServiceClientError(f"valuation-service unavailable: {exc}") from exc

        try:
            payload = resp.json()
        except ValueError as exc:
            raise ValuationServiceClientError(
                f"valuation-service non-JSON response (status={resp.status_code})"
            ) from exc

        if resp.status_code >= 400:
            msg = payload.get("message") or payload.get("error") or f"HTTP {resp.status_code}"
            raise ValuationServiceClientError(msg)

        data = payload.get("data")
        if not isinstance(data, dict):
            raise ValuationServiceClientError("valuation-service response missing data payload")

        return data
