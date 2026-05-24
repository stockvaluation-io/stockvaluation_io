"""StockValuation MCP tool contracts and implementation."""

from __future__ import annotations

import json
import re
from typing import Any, Callable

from . import __version__
from .security import sanitize_for_agent
from .service_client import (
    DEFAULT_SERVICE_URL,
    NonJsonServiceResponse,
    ServiceHTTPError,
    ServiceUnavailable,
    ValuationServiceClient,
    ValuationServiceError,
)

TICKER_RE = re.compile(r"^[A-Z0-9][A-Z0-9.\-]{0,14}$")

SUPPORTED_OVERRIDE_FIELDS = {
    "revenue_growth",
    "operating_margin",
    "sales_to_capital",
    "wacc",
    "terminal_growth",
    "tax_rate",
}

TOOL_NAMES = [
    "stockvaluation.health",
    "stockvaluation.value_ticker",
    "stockvaluation.recalculate",
    "stockvaluation.get_assumptions",
    "stockvaluation.get_growth_anchor",
    "stockvaluation.get_reference_data_status",
    "stockvaluation.explain_failure",
]

SUPPORTED_PROTOCOL_VERSIONS = (
    "2025-11-25",
    "2025-06-18",
    "2025-03-26",
    "2024-11-05",
    "2024-10-07",
)


def _object_schema(properties: dict[str, Any] | None = None, required: list[str] | None = None) -> dict[str, Any]:
    schema: dict[str, Any] = {
        "type": "object",
        "properties": properties or {},
        "additionalProperties": False,
    }
    if required:
        schema["required"] = required
    return schema


def _output_schema() -> dict[str, Any]:
    return {
        "type": "object",
        "properties": {
            "ok": {"type": "boolean"},
            "tool": {"type": "string"},
            "error": {"type": "object"},
        },
        "required": ["ok", "tool"],
        "additionalProperties": True,
    }


def tool_definitions() -> list[dict[str, Any]]:
    ticker_property = {
        "ticker": {
            "type": "string",
            "description": "Public equity ticker symbol, e.g. MSFT. No company names or shell syntax.",
        }
    }
    return [
        {
            "name": "stockvaluation.health",
            "title": "StockValuation Health",
            "description": "Check whether the local valuation service is reachable.",
            "inputSchema": _object_schema(),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.value_ticker",
            "title": "Value Ticker",
            "description": "Fetch deterministic local DCF JSON for a supported ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.recalculate",
            "title": "Recalculate Valuation",
            "description": "Recalculate local DCF JSON using governed scenario overrides.",
            "inputSchema": _object_schema(
                {
                    **ticker_property,
                    "overrides": {
                        "type": "object",
                        "description": "Supported keys: revenue_growth, operating_margin, sales_to_capital, wacc, terminal_growth, tax_rate.",
                        "additionalProperties": True,
                    },
                },
                ["ticker", "overrides"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": False, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_assumptions",
            "title": "Get Assumptions",
            "description": "Return the assumption transparency slice for a ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_growth_anchor",
            "title": "Get Growth Anchor",
            "description": "Return mapped Damodaran growth-anchor context for a ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_reference_data_status",
            "title": "Get Reference Data Status",
            "description": "Return service and reference-data status used for reproducibility notes.",
            "inputSchema": _object_schema({**ticker_property}, []),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.explain_failure",
            "title": "Explain Failure",
            "description": "Classify an MCP or valuation-service failure into an agent-readable recovery path.",
            "inputSchema": _object_schema(
                {
                    "error": {
                        "description": "Error string or structured error object from another StockValuation tool.",
                    }
                },
                ["error"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
    ]


class MCPToolRegistry:
    """Callable registry for StockValuation MCP tools."""

    def __init__(self, service_client: Any | None = None):
        self.service_client = service_client or ValuationServiceClient()
        self._handlers: dict[str, Callable[[dict[str, Any]], dict[str, Any]]] = {
            "stockvaluation.health": self._health,
            "stockvaluation.value_ticker": self._value_ticker,
            "stockvaluation.recalculate": self._recalculate,
            "stockvaluation.get_assumptions": self._get_assumptions,
            "stockvaluation.get_growth_anchor": self._get_growth_anchor,
            "stockvaluation.get_reference_data_status": self._get_reference_data_status,
            "stockvaluation.explain_failure": self._explain_failure,
        }

    def list_tools(self) -> list[dict[str, Any]]:
        return tool_definitions()

    def call(self, name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        args = arguments or {}
        if name not in self._handlers:
            content = error_payload(name, "UNKNOWN_TOOL", "Unknown StockValuation tool.", "unknown_tool")
            return tool_result(content, is_error=True)
        content = self._handlers[name](args)
        return tool_result(content, is_error=not bool(content.get("ok")))

    def _health(self, _: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.health"
        try:
            health = self.service_client.health()
            return {
                "ok": True,
                "tool": tool,
                "service": {
                    "name": "stockvaluation-service",
                    "status": health.get("status", "unknown"),
                    "raw": sanitize_for_agent(health),
                },
                "mcp": mcp_metadata(),
                "policy": policy_metadata(),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc)

    def _value_ticker(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.value_ticker"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            return valuation_success_payload(tool, ticker, valuation)
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _recalculate(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.recalculate"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        requested = args.get("overrides")
        if not isinstance(requested, dict):
            return error_payload(
                tool,
                "INVALID_OVERRIDES",
                "overrides must be a JSON object.",
                "invalid_overrides",
                extra={"assumptions": {"requested": requested, "mapped": {}, "unsupported": {}, "effective": {}}},
            )

        mapped, unsupported = map_recalculate_overrides(requested)
        assumption_meta = {
            "requested": sanitize_for_agent(requested),
            "mapped": mapped,
            "unsupported": unsupported,
            "effective": {},
        }
        if unsupported:
            return error_payload(
                tool,
                "UNSUPPORTED_OVERRIDES",
                "One or more override fields are not governed by the MCP contract.",
                "unsupported_overrides",
                extra={"ticker": ticker, "assumptions": assumption_meta},
            )
        try:
            valuation = self.service_client.value_ticker(ticker, mapped)
            assumption_meta["effective"] = effective_assumptions(valuation)
            payload = valuation_success_payload(tool, ticker, valuation)
            payload["assumptions"] = assumption_meta
            return payload
        except ValuationServiceError as exc:
            payload = service_exception_payload(tool, exc, ticker=ticker)
            payload["assumptions"] = assumption_meta
            return payload

    def _get_assumptions(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_assumptions"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            return {
                "ok": True,
                "tool": tool,
                "ticker": ticker,
                "assumptions": extract_assumptions(valuation),
                "policy": policy_metadata(),
                "version": version_metadata(valuation),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _get_growth_anchor(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_growth_anchor"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            return {
                "ok": True,
                "tool": tool,
                "ticker": ticker,
                "growthAnchor": extract_growth_anchor(valuation),
                "version": version_metadata(valuation),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _get_reference_data_status(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_reference_data_status"
        ticker_value = args.get("ticker")
        if ticker_value:
            ticker, error = normalize_ticker(ticker_value)
            if error:
                return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
            try:
                valuation = self.service_client.value_ticker(ticker)
                return {
                    "ok": True,
                    "tool": tool,
                    "ticker": ticker,
                    "referenceData": reference_data_status(valuation),
                    "version": version_metadata(valuation),
                }
            except ValuationServiceError as exc:
                return service_exception_payload(tool, exc, ticker=ticker)
        health_payload = self._health({})
        return {
            "ok": health_payload.get("ok", False),
            "tool": tool,
            "service": health_payload.get("service"),
            "referenceData": reference_data_status({}),
            "version": {"mcp": mcp_metadata()},
        }

    def _explain_failure(self, args: dict[str, Any]) -> dict[str, Any]:
        return explain_failure(args.get("error"))


def normalize_ticker(raw: Any) -> tuple[str, str | None]:
    if not isinstance(raw, str):
        return "", "ticker must be a string."
    ticker = raw.strip().upper()
    if not ticker or not TICKER_RE.fullmatch(ticker):
        return "", "ticker must be 1-15 characters using letters, numbers, dots, or hyphens only."
    return ticker, None


def map_recalculate_overrides(requested: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any]]:
    mapped: dict[str, Any] = {}
    unsupported: dict[str, Any] = {}
    for key, value in requested.items():
        if key not in SUPPORTED_OVERRIDE_FIELDS:
            unsupported[key] = sanitize_for_agent(value)
            continue
        number = _number_or_none(value)
        if number is None:
            unsupported[key] = {"value": sanitize_for_agent(value), "reason": "not_numeric"}
            continue
        if key == "revenue_growth":
            mapped["compoundAnnualGrowth2_5"] = round(normalize_percent(number), 2)
        elif key == "operating_margin":
            mapped["targetPreTaxOperatingMargin"] = round(normalize_percent(number), 2)
        elif key == "sales_to_capital":
            mapped["salesToCapitalYears1To5"] = round(normalize_sales_to_capital(number), 2)
            mapped["salesToCapitalYears6To10"] = round(normalize_sales_to_capital(number), 2)
        elif key == "wacc":
            mapped["initialCostCapital"] = round(normalize_percent(number), 2)
        elif key == "terminal_growth":
            mapped["terminalGrowthRate"] = round(normalize_percent(number), 2)
        elif key == "tax_rate":
            mapped["overrideAssumptionTaxRate"] = {
                "overrideCost": round(normalize_percent(number), 2),
                "isOverride": True,
                "additionalInputValue": 0.0,
                "additionalRadioValue": None,
            }
    return mapped, unsupported


def normalize_percent(value: float) -> float:
    if abs(value) <= 1.0:
        return value * 100.0
    return value


def normalize_sales_to_capital(value: float) -> float:
    if abs(value) > 50.0:
        return value / 100.0
    return value


def valuation_success_payload(tool: str, ticker: str, valuation: dict[str, Any]) -> dict[str, Any]:
    return {
        "ok": True,
        "tool": tool,
        "ticker": ticker,
        "valuation": sanitize_for_agent(valuation),
        "dcf": extract_dcf_summary(valuation),
        "assumptions": extract_assumptions(valuation),
        "growthAnchor": extract_growth_anchor(valuation),
        "referenceData": reference_data_status(valuation),
        "version": version_metadata(valuation),
        "policy": policy_metadata(),
        "warnings": extract_warnings(valuation),
    }


def extract_dcf_summary(valuation: dict[str, Any]) -> dict[str, Any]:
    company = _dict(valuation.get("companyDTO"))
    financial = _dict(valuation.get("financialDTO"))
    terminal = _dict(valuation.get("terminalValueDTO"))
    return {
        "companyName": valuation.get("companyName"),
        "currency": valuation.get("currency"),
        "stockCurrency": valuation.get("stockCurrency"),
        "primaryModel": valuation.get("primaryModel"),
        "growthPattern": valuation.get("growthPattern"),
        "projectionYears": valuation.get("projectionYears"),
        "estimatedValuePerShare": company.get("estimatedValuePerShare") or financial.get("intrinsicValue"),
        "marketPrice": company.get("price"),
        "valueOfEquity": company.get("valueOfEquity"),
        "numberOfShares": company.get("numberOfShares"),
        "terminalGrowthRate": terminal.get("growthRate"),
        "terminalCostOfCapital": terminal.get("costOfCapital"),
    }


def extract_assumptions(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    operating = _dict(transparency.get("operatingAssumptions"))
    discount = _dict(transparency.get("discountRate"))
    terminal = _dict(valuation.get("terminalValueDTO"))
    financial = _dict(valuation.get("financialDTO"))
    tax_rate = _first_present(operating.get("taxRate"), _last_number(financial.get("taxRate")))
    return {
        "growth": {
            "revenueGrowthRateYears2To5": _first_present(
                operating.get("revenueGrowthRateYears2To5"),
                _last_number(financial.get("revenueGrowthRate")),
            ),
            "source": operating.get("revenueGrowthSource"),
            "rationale": operating.get("revenueGrowthRationale"),
        },
        "margin": {
            "operatingMarginNextYear": operating.get("operatingMarginNextYear"),
            "targetOperatingMargin": _first_present(
                operating.get("targetOperatingMargin"),
                _last_number(financial.get("ebitOperatingMargin")),
            ),
            "source": operating.get("operatingMarginSource"),
            "rationale": operating.get("operatingMarginRationale"),
        },
        "salesToCapital": {
            "years1To5": _first_present(
                operating.get("salesToCapitalYears1To5"),
                _last_number(financial.get("salesToCapitalRatio")),
            ),
            "years6To10": operating.get("salesToCapitalYears6To10"),
            "source": operating.get("salesToCapitalSource"),
            "rationale": operating.get("salesToCapitalRationale"),
        },
        "costOfCapital": {
            "riskFreeRate": discount.get("riskFreeRate"),
            "initialCostOfCapital": _first_present(
                discount.get("initialCostOfCapital"),
                _first_number(financial.get("costOfCapital")),
            ),
            "terminalCostOfCapital": _first_present(
                discount.get("terminalCostOfCapital"),
                terminal.get("costOfCapital"),
            ),
            "source": {
                "riskFreeRate": discount.get("riskFreeRateSource"),
                "equityRiskPremium": discount.get("equityRiskPremiumSource"),
                "initialCostOfCapital": discount.get("initialCostOfCapitalSource"),
            },
        },
        "terminalGrowth": {
            "rate": terminal.get("growthRate"),
            "limitNote": "Compare terminal growth to inflation and mature economy growth before presenting scenarios.",
        },
        "taxRate": tax_rate,
        "accountingAdjustments": {
            "rdCapitalization": valuation.get("rdCapitalization") or valuation.get("rdCapitalized"),
            "operatingLeaseConversion": valuation.get("operatingLeaseConversion"),
            "optionsOrWarrants": valuation.get("optionValueResultDTO") or valuation.get("valueOfOptions"),
        },
        "source": "valuation-service",
        "rationale": {
            "templateSelection": valuation.get("templateSelectionReason")
            or transparency.get("templateSelectionReason"),
            "modelSelection": valuation.get("modelSelectionRationale"),
        },
    }


def effective_assumptions(valuation: dict[str, Any]) -> dict[str, Any]:
    assumptions = extract_assumptions(valuation)
    return {
        "revenue_growth": assumptions["growth"]["revenueGrowthRateYears2To5"],
        "operating_margin": assumptions["margin"]["targetOperatingMargin"],
        "sales_to_capital": assumptions["salesToCapital"]["years1To5"],
        "wacc": assumptions["costOfCapital"]["initialCostOfCapital"],
        "terminal_growth": assumptions["terminalGrowth"]["rate"],
        "tax_rate": assumptions["taxRate"],
    }


def extract_growth_anchor(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    anchor = _dict(transparency.get("growthAnchor") or valuation.get("growthSkillContext"))
    warnings = []
    confidence = anchor.get("confidenceScore")
    if confidence is None:
        warnings.append("No growth-anchor confidence score was returned by valuation-service.")
    elif isinstance(confidence, (int, float)) and confidence < 0.5:
        warnings.append("Growth-anchor confidence is weak; treat industry comparison as directional.")
    return {
        "mappedEntity": anchor.get("entity"),
        "mappedEntityDisplay": anchor.get("entityDisplay"),
        "region": anchor.get("region"),
        "year": anchor.get("year"),
        "confidence": confidence,
        "percentileBand": {
            "p25": anchor.get("p25"),
            "p50": anchor.get("p50"),
            "p75": anchor.get("p75"),
        },
        "sourceDate": anchor.get("sourceDate") or anchor.get("year"),
        "source": anchor.get("source") or "valuation-service growth anchor",
        "warnings": warnings,
    }


def reference_data_status(valuation: dict[str, Any]) -> dict[str, Any]:
    anchor = extract_growth_anchor(valuation)
    return {
        "valuationServiceUrl": DEFAULT_SERVICE_URL,
        "marketData": {
            "provider": "Yahoo Finance via local yfinance service",
            "status": "queried_by_valuation_service" if valuation else "unknown_until_ticker_request",
            "warnings": ["Yahoo Finance coverage can be missing, stale, or insufficient for some companies."],
        },
        "damodaranReferenceData": {
            "status": "available_when_growth_anchor_present" if anchor.get("mappedEntity") else "not_returned",
            "mappedEntity": anchor.get("mappedEntity"),
            "region": anchor.get("region"),
            "year": anchor.get("year"),
            "sourceDate": anchor.get("sourceDate"),
            "confidence": anchor.get("confidence"),
            "warnings": anchor.get("warnings", []),
        },
    }


def version_metadata(valuation: dict[str, Any]) -> dict[str, Any]:
    return {
        "mcp": mcp_metadata(),
        "valuationService": {
            "serviceVersion": valuation.get("serviceVersion"),
            "dataVersion": valuation.get("dataVersion"),
            "modelVersion": valuation.get("modelVersion"),
        },
    }


def mcp_metadata() -> dict[str, Any]:
    return {
        "name": "stockvaluation-agent-native",
        "version": __version__,
        "protocolVersion": SUPPORTED_PROTOCOL_VERSIONS[0],
        "supportedProtocolVersions": list(SUPPORTED_PROTOCOL_VERSIONS),
    }


def policy_metadata() -> dict[str, Any]:
    return {
        "educationalUseOnly": True,
        "notFinancialAdvice": True,
        "reportWriter": "user-agent",
        "prohibitedRecommendationLanguage": ["buy", "sell", "hold", "target price", "should invest"],
    }


def extract_warnings(valuation: dict[str, Any]) -> list[str]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    notes = transparency.get("notes")
    if isinstance(notes, list):
        return [str(item) for item in notes]
    return []


def service_exception_payload(tool: str, exc: Exception, ticker: str | None = None) -> dict[str, Any]:
    if isinstance(exc, ServiceUnavailable):
        return error_payload(
            tool,
            "MISSING_LOCAL_SERVICE",
            "Local valuation service is not reachable.",
            "missing_local_service",
            extra={"ticker": ticker, "detail": sanitize_for_agent(str(exc))},
        )
    if isinstance(exc, NonJsonServiceResponse):
        return error_payload(
            tool,
            "NON_JSON_SERVICE_RESPONSE",
            "Local valuation service returned a non-JSON response.",
            "non_json_service_response",
            extra={"ticker": ticker, "detail": sanitize_for_agent(str(exc))},
        )
    if isinstance(exc, ServiceHTTPError):
        category = classify_failure(exc.message)
        if category == "unknown_failure" and exc.status >= 500:
            category = "upstream_service_error"
        return error_payload(
            tool,
            failure_code_for_category(category),
            exc.message,
            category,
            extra={"ticker": ticker, "status": exc.status, "upstream": sanitize_for_agent(exc.payload or {})},
        )
    if isinstance(exc, ValuationServiceError):
        category = classify_failure(str(exc))
        return error_payload(
            tool,
            failure_code_for_category(category),
            str(sanitize_for_agent(str(exc))),
            category,
            extra={"ticker": ticker},
        )
    return error_payload(tool, "VALUATION_SERVICE_ERROR", str(sanitize_for_agent(str(exc))), "unknown_failure")


def explain_failure(error: Any) -> dict[str, Any]:
    message = extract_failure_message(error)
    category = classify_failure(message)
    return {
        "ok": True,
        "tool": "stockvaluation.explain_failure",
        "failureCategory": category,
        "message": sanitize_for_agent(message),
        "recovery": recovery_for_category(category),
    }


def extract_failure_message(error: Any) -> str:
    if isinstance(error, str):
        stripped = error.strip()
        if stripped:
            try:
                return extract_failure_message(json.loads(stripped))
            except json.JSONDecodeError:
                return stripped
        return str(error)
    if isinstance(error, dict):
        direct = error.get("message")
        if isinstance(direct, str) and direct.strip():
            return direct
        nested = error.get("error")
        if isinstance(nested, dict):
            nested_message = nested.get("message") or nested.get("error")
            if isinstance(nested_message, str) and nested_message.strip():
                return nested_message
            nested_code = nested.get("code")
            if isinstance(nested_code, str) and nested_code.strip():
                return nested_code
        if isinstance(nested, str) and nested.strip():
            return nested
        code = error.get("code")
        if isinstance(code, str) and code.strip():
            return code
    return str(error)


def classify_failure(message: str) -> str:
    lowered = message.lower()
    if (
        "currency" in lowered
        and any(term in lowered for term in ("conversion failed", "differs", "convert", "exchange-rate"))
    ):
        return "currency_conversion_failed"
    if (
        ("frankfurter" in lowered or "currency provider" in lowered)
        and any(term in lowered for term in ("unavailable", "failed", "missing", "rate", "loading"))
    ):
        return "currency_conversion_failed"
    if any(term in lowered for term in ("financial company", "financial sector", "bank", "insurance", "unsupported")):
        return "unsupported_company"
    if "insufficient" in lowered or "missing financial" in lowered or "not enough financial" in lowered:
        return "insufficient_financial_data"
    if any(term in lowered for term in ("configuration", "environment variable", "required")):
        return "missing_configuration"
    if "stale" in lowered and "reference" in lowered:
        return "stale_reference_data"
    if "non-json" in lowered or "non json" in lowered or "html" in lowered:
        return "non_json_service_response"
    if "connection" in lowered or "refused" in lowered or "unreachable" in lowered or "timed out" in lowered:
        return "missing_local_service"
    if "upstream" in lowered or "dependency" in lowered or "service error" in lowered:
        return "upstream_service_error"
    return "unknown_failure"


def failure_code_for_category(category: str) -> str:
    return {
        "unsupported_company": "UNSUPPORTED_COMPANY",
        "insufficient_financial_data": "INSUFFICIENT_FINANCIAL_DATA",
        "missing_configuration": "MISSING_CONFIGURATION",
        "stale_reference_data": "STALE_REFERENCE_DATA",
        "non_json_service_response": "NON_JSON_SERVICE_RESPONSE",
        "missing_local_service": "MISSING_LOCAL_SERVICE",
        "currency_conversion_failed": "CURRENCY_CONVERSION_FAILED",
        "upstream_service_error": "UPSTREAM_SERVICE_ERROR",
    }.get(category, "VALUATION_SERVICE_ERROR")


def recovery_for_category(category: str) -> dict[str, Any]:
    recovery = {
        "unsupported_company": "Explain that the company type is unsupported and do not invent a DCF.",
        "insufficient_financial_data": "Tell the user which data is missing and avoid filling gaps with invented values.",
        "missing_configuration": "Ask the user to run `sv check-env` and configure the missing local environment variable.",
        "stale_reference_data": "Show the stale-data warning and treat growth anchors as directional.",
        "non_json_service_response": "Ask the user to run `sv service status`; the service may be returning an error page.",
        "missing_local_service": "Ask the user to run `sv service start` and then retry the MCP call.",
        "currency_conversion_failed": "Explain that valuation-service could not safely complete currency conversion. Ask the user to verify Frankfurter currency provider availability, then retry; do not manually convert and invent the valuation.",
        "upstream_service_error": "Tell the user the local valuation service returned an upstream error. Ask them to run `sv service status`, retry once, and preserve the failure category if it repeats.",
        "invalid_ticker": "Ask for a valid public ticker symbol.",
        "unsupported_overrides": "Ask before retrying with only governed scenario override fields.",
        "unknown_failure": "Summarize the failure and ask the user whether to run service status checks.",
    }
    return {
        "agentAction": recovery.get(category, recovery["unknown_failure"]),
        "canRetry": category in {"missing_local_service", "non_json_service_response", "missing_configuration"},
    }


def error_payload(
    tool: str,
    code: str,
    message: str,
    category: str,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "ok": False,
        "tool": tool,
        "failureCategory": category,
        "error": {
            "code": code,
            "message": sanitize_for_agent(message),
        },
        "recovery": recovery_for_category(category),
        "policy": policy_metadata(),
    }
    if extra:
        payload.update({key: sanitize_for_agent(value) for key, value in extra.items() if value is not None})
    return payload


def tool_result(payload: dict[str, Any], is_error: bool) -> dict[str, Any]:
    safe_payload = sanitize_for_agent(payload)
    return {
        "content": [
            {
                "type": "text",
                "text": json.dumps(safe_payload, sort_keys=True, separators=(",", ":")),
            }
        ],
        "structuredContent": safe_payload,
        "isError": is_error,
    }


def _dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _number_or_none(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _first_number(values: Any) -> float | None:
    if not isinstance(values, list):
        return None
    for value in values:
        if isinstance(value, (int, float)):
            return float(value)
    return None


def _last_number(values: Any) -> float | None:
    if not isinstance(values, list):
        return None
    for value in reversed(values):
        if isinstance(value, (int, float)):
            return float(value)
    return None


def _first_present(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value
    return None
