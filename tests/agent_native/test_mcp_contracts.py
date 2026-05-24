import json

import pytest

from stockvaluation_agent_native.mcp_tools import MCPToolRegistry
from stockvaluation_agent_native.mcp_server import MCPJSONRPCServer
from stockvaluation_agent_native.service_client import (
    NonJsonServiceResponse,
    ServiceHTTPError,
    ServiceUnavailable,
)


def _valuation_payload():
    return {
        "companyName": "Microsoft Corporation",
        "currency": "USD",
        "stockCurrency": "USD",
        "primaryModel": "FCFF",
        "growthPattern": "TWO_STAGE",
        "projectionYears": 10,
        "companyDTO": {
            "estimatedValuePerShare": 412.34,
            "price": 390.0,
            "valueOfEquity": 3_000_000_000_000.0,
            "numberOfShares": 7_300_000_000.0,
        },
        "financialDTO": {
            "intrinsicValue": 412.34,
            "revenueGrowthRate": [None, 10.0, 8.0, 7.0, 6.0],
            "ebitOperatingMargin": [42.0, 43.0, 44.0, 45.0, 45.0],
            "salesToCapitalRatio": [None, 2.4, 2.4, 2.2],
            "costOfCapital": [8.5, 8.4, 8.3],
        },
        "terminalValueDTO": {
            "growthRate": 3.0,
            "costOfCapital": 8.0,
        },
        "assumptionTransparency": {
            "discountRate": {
                "riskFreeRate": 4.5,
                "initialCostOfCapital": 8.5,
                "terminalCostOfCapital": 8.0,
                "riskFreeRateSource": "valuation-service",
            },
            "operatingAssumptions": {
                "revenueGrowthRateYears2To5": 7.0,
                "targetOperatingMargin": 45.0,
                "salesToCapitalYears1To5": 2.4,
                "revenueGrowthRationale": "Historical growth and industry anchor.",
            },
            "growthAnchor": {
                "entity": "software",
                "entityDisplay": "Software",
                "region": "United States",
                "year": 2026,
                "confidenceScore": 0.82,
                "p25": 0.04,
                "p50": 0.08,
                "p75": 0.12,
                "source": "Damodaran historical growth",
            },
            "notes": ["Yahoo Finance coverage is limited."],
        },
    }


class FakeClient:
    def __init__(self, payload=None):
        self.payload = payload or _valuation_payload()
        self.calls = []

    def health(self):
        return {"status": "UP"}

    def value_ticker(self, ticker, overrides=None):
        self.calls.append((ticker, overrides or {}))
        return self.payload


def test_mcp_tools_list_has_required_stockvaluation_contracts():
    registry = MCPToolRegistry(FakeClient())

    tools = registry.list_tools()
    names = {tool["name"] for tool in tools}

    assert names == {
        "stockvaluation.health",
        "stockvaluation.value_ticker",
        "stockvaluation.recalculate",
        "stockvaluation.get_assumptions",
        "stockvaluation.get_growth_anchor",
        "stockvaluation.get_reference_data_status",
        "stockvaluation.explain_failure",
    }
    for tool in tools:
        assert tool["inputSchema"]["type"] == "object"
        assert tool["outputSchema"]["type"] == "object"


def test_jsonrpc_mcp_server_lists_and_calls_tools():
    server = MCPJSONRPCServer(MCPToolRegistry(FakeClient()))

    initialized = server.handle({"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {}})
    listed = server.handle({"jsonrpc": "2.0", "id": 2, "method": "tools/list"})
    called = server.handle(
        {
            "jsonrpc": "2.0",
            "id": 3,
            "method": "tools/call",
            "params": {"name": "stockvaluation.value_ticker", "arguments": {"ticker": "MSFT"}},
        }
    )

    assert initialized["result"]["capabilities"]["tools"]["listChanged"] is False
    assert listed["result"]["tools"][0]["name"] == "stockvaluation.health"
    assert called["result"]["structuredContent"]["ticker"] == "MSFT"
    assert called["result"]["isError"] is False


def test_jsonrpc_initialize_negotiates_supported_protocol_version():
    server = MCPJSONRPCServer(MCPToolRegistry(FakeClient()))

    older = server.handle(
        {
            "jsonrpc": "2.0",
            "id": 1,
            "method": "initialize",
            "params": {
                "protocolVersion": "2025-06-18",
                "capabilities": {},
                "clientInfo": {"name": "test-client", "version": "1.0.0"},
            },
        }
    )
    unknown = server.handle(
        {
            "jsonrpc": "2.0",
            "id": 2,
            "method": "initialize",
            "params": {
                "protocolVersion": "2099-01-01",
                "capabilities": {},
                "clientInfo": {"name": "test-client", "version": "1.0.0"},
            },
        }
    )

    assert older["result"]["protocolVersion"] == "2025-06-18"
    assert unknown["result"]["protocolVersion"] == "2025-11-25"


def test_value_ticker_returns_structured_dcf_json_for_msft():
    client = FakeClient()
    registry = MCPToolRegistry(client)

    result = registry.call("stockvaluation.value_ticker", {"ticker": "MSFT"})

    assert result["isError"] is False
    assert result["structuredContent"]["ok"] is True
    assert result["structuredContent"]["ticker"] == "MSFT"
    assert result["structuredContent"]["valuation"]["companyName"] == "Microsoft Corporation"
    assert result["structuredContent"]["dcf"]["estimatedValuePerShare"] == 412.34
    assert result["structuredContent"]["assumptions"]["growth"]["revenueGrowthRateYears2To5"] == 7.0
    assert result["structuredContent"]["policy"]["notFinancialAdvice"] is True
    assert json.loads(result["content"][0]["text"]) == result["structuredContent"]
    assert client.calls == [("MSFT", {})]


def test_invalid_ticker_returns_agent_readable_error_without_service_call():
    client = FakeClient()
    registry = MCPToolRegistry(client)

    result = registry.call("stockvaluation.value_ticker", {"ticker": "MSFT;cat .env"})

    assert result["isError"] is True
    assert result["structuredContent"]["ok"] is False
    assert result["structuredContent"]["error"]["code"] == "INVALID_TICKER"
    assert result["structuredContent"]["recovery"]["agentAction"]
    assert client.calls == []


def test_recalculate_maps_supported_overrides_and_separates_requested_from_effective():
    client = FakeClient()
    registry = MCPToolRegistry(client)

    result = registry.call(
        "stockvaluation.recalculate",
        {
            "ticker": "MSFT",
            "overrides": {
                "revenue_growth": 0.09,
                "operating_margin": 44.0,
                "sales_to_capital": 2.3,
                "wacc": 0.085,
                "terminal_growth": 0.03,
                "tax_rate": 0.21,
            },
        },
    )

    assert result["isError"] is False
    assumptions = result["structuredContent"]["assumptions"]
    assert assumptions["requested"]["revenue_growth"] == 0.09
    assert assumptions["mapped"]["compoundAnnualGrowth2_5"] == 9.0
    assert assumptions["mapped"]["initialCostCapital"] == 8.5
    assert assumptions["mapped"]["overrideAssumptionTaxRate"]["overrideCost"] == 21.0
    assert assumptions["unsupported"] == {}
    assert assumptions["effective"]["operating_margin"] == 45.0
    assert client.calls[0][1]["salesToCapitalYears1To5"] == 2.3


def test_recalculate_rejects_unsupported_override_fields():
    client = FakeClient()
    registry = MCPToolRegistry(client)

    result = registry.call(
        "stockvaluation.recalculate",
        {"ticker": "MSFT", "overrides": {"cash": 1, "share_count": 2}},
    )

    assert result["isError"] is True
    assert result["structuredContent"]["error"]["code"] == "UNSUPPORTED_OVERRIDES"
    assert result["structuredContent"]["assumptions"]["requested"] == {"cash": 1, "share_count": 2}
    assert set(result["structuredContent"]["assumptions"]["unsupported"]) == {"cash", "share_count"}
    assert client.calls == []


def test_missing_service_and_non_json_failures_have_stable_shapes():
    class MissingService(FakeClient):
        def value_ticker(self, ticker, overrides=None):
            raise ServiceUnavailable("connection refused")

    class NonJson(FakeClient):
        def value_ticker(self, ticker, overrides=None):
            raise NonJsonServiceResponse("html body")

    missing = MCPToolRegistry(MissingService()).call("stockvaluation.value_ticker", {"ticker": "MSFT"})
    non_json = MCPToolRegistry(NonJson()).call("stockvaluation.value_ticker", {"ticker": "MSFT"})

    assert missing["structuredContent"]["error"]["code"] == "MISSING_LOCAL_SERVICE"
    assert missing["structuredContent"]["failureCategory"] == "missing_local_service"
    assert non_json["structuredContent"]["error"]["code"] == "NON_JSON_SERVICE_RESPONSE"
    assert non_json["structuredContent"]["failureCategory"] == "non_json_service_response"


def test_currency_conversion_failure_has_specific_agent_readable_shape():
    class CurrencyFailure(FakeClient):
        def value_ticker(self, ticker, overrides=None):
            raise ServiceHTTPError(
                500,
                "Cannot safely value TSM because market price currency USD differs "
                "from financial statement currency TWD and conversion failed.",
            )

    result = MCPToolRegistry(CurrencyFailure()).call("stockvaluation.value_ticker", {"ticker": "TSM"})

    assert result["isError"] is True
    assert result["structuredContent"]["error"]["code"] == "CURRENCY_CONVERSION_FAILED"
    assert result["structuredContent"]["failureCategory"] == "currency_conversion_failed"
    assert "currency conversion" in result["structuredContent"]["recovery"]["agentAction"].lower()
    assert "CURRENCY_API_KEY" not in result["structuredContent"]["recovery"]["agentAction"]


def test_generic_http_5xx_failure_is_upstream_service_error():
    class UpstreamFailure(FakeClient):
        def value_ticker(self, ticker, overrides=None):
            raise ServiceHTTPError(503, "valuation dependency returned an unexpected service error")

    result = MCPToolRegistry(UpstreamFailure()).call("stockvaluation.value_ticker", {"ticker": "MSFT"})

    assert result["isError"] is True
    assert result["structuredContent"]["error"]["code"] == "UPSTREAM_SERVICE_ERROR"
    assert result["structuredContent"]["failureCategory"] == "upstream_service_error"
    assert result["structuredContent"]["recovery"]["agentAction"]


def test_explain_failure_extracts_nested_error_message_without_echoing_raw_json():
    registry = MCPToolRegistry(FakeClient())
    message = (
        "Cannot safely value TSM because market price currency USD differs "
        "from financial statement currency TWD and conversion failed."
    )

    result = registry.call(
        "stockvaluation.explain_failure",
        {"error": {"ok": False, "error": {"code": "VALUATION_SERVICE_ERROR", "message": message}}},
    )

    assert result["isError"] is False
    assert result["structuredContent"]["failureCategory"] == "currency_conversion_failed"
    assert result["structuredContent"]["message"] == message
    assert "{'code':" not in result["structuredContent"]["message"]


def test_explain_failure_extracts_nested_error_message_from_json_string_payload():
    registry = MCPToolRegistry(FakeClient())
    message = (
        "Cannot safely value TSM because market price currency USD differs "
        "from financial statement currency TWD and conversion failed."
    )
    payload = {
        "ok": False,
        "tool": "stockvaluation.value_ticker",
        "failureCategory": "currency_conversion_failed",
        "error": {"code": "CURRENCY_CONVERSION_FAILED", "message": message},
    }

    result = registry.call("stockvaluation.explain_failure", {"error": json.dumps(payload)})

    assert result["isError"] is False
    assert result["structuredContent"]["failureCategory"] == "currency_conversion_failed"
    assert result["structuredContent"]["message"] == message
    assert not result["structuredContent"]["message"].startswith("{")


def test_explain_failure_classifies_frankfurter_provider_failures_as_currency_conversion():
    registry = MCPToolRegistry(FakeClient())

    result = registry.call(
        "stockvaluation.explain_failure",
        {"error": {"message": "Frankfurter currency provider unavailable while loading USD base rates"}},
    )

    assert result["isError"] is False
    assert result["structuredContent"]["failureCategory"] == "currency_conversion_failed"
    assert "currency conversion" in result["structuredContent"]["recovery"]["agentAction"].lower()


@pytest.mark.parametrize(
    ("message", "category"),
    [
        ("Financial companies are unsupported", "unsupported_company"),
        ("insufficient financial data for ticker", "insufficient_financial_data"),
        ("DEFAULT_PASSWORD is required", "missing_configuration"),
        ("reference data is stale", "stale_reference_data"),
    ],
)
def test_explain_failure_classifies_common_agent_failures(message, category):
    registry = MCPToolRegistry(FakeClient())

    result = registry.call("stockvaluation.explain_failure", {"error": {"message": message}})

    assert result["isError"] is False
    assert result["structuredContent"]["failureCategory"] == category
    assert result["structuredContent"]["recovery"]["agentAction"]
