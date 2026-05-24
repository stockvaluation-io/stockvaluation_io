# MCP Tools

All StockValuation tools return MCP `structuredContent` with JSON. Read `structuredContent` first. The text block is a serialized copy for clients that do not expose structured output.

## `stockvaluation.health`

Checks the local MCP adapter and valuation service.

Input:

```json
{}
```

Expected output:

```json
{
  "ok": true,
  "tool": "stockvaluation.health",
  "service": {
    "name": "stockvaluation-service",
    "status": "UP"
  },
  "mcp": {
    "name": "stockvaluation-agent-native",
    "version": "0.1.0"
  }
}
```

## `stockvaluation.value_ticker`

Fetches the baseline local DCF JSON.

Input:

```json
{
  "ticker": "MSFT"
}
```

Use these output sections:

- `valuation`: full valuation-service payload.
- `dcf`: compact DCF summary for reporting.
- `assumptions`: grouped assumptions and rationales.
- `growthAnchor`: Damodaran growth-anchor mapping, confidence, percentile band, source date, and warnings.
- `referenceData`: market-data and reference-data status.
- `warnings`: service and data-quality notes.
- `policy`: educational-use and no-advice guardrails.

## `stockvaluation.recalculate`

Recalculates deterministic DCF output with governed scenario overrides. Ask the user before calling it.

Input:

```json
{
  "ticker": "MSFT",
  "overrides": {
    "revenue_growth": 8.5,
    "operating_margin": 42.0,
    "sales_to_capital": 2.4,
    "wacc": 8.25,
    "terminal_growth": 3.0,
    "tax_rate": 21.0
  }
}
```

Supported override keys:

- `revenue_growth`
- `operating_margin`
- `sales_to_capital`
- `wacc`
- `terminal_growth`
- `tax_rate`

The response separates:

- `assumptions.requested`: what the user or agent requested.
- `assumptions.mapped`: fields sent to the valuation service.
- `assumptions.unsupported`: rejected fields.
- `assumptions.effective`: what the service actually used.

Do not pass debt, cash, share count, market price, option value, terminal value, equity value, or other direct valuation-output fields.

## `stockvaluation.get_assumptions`

Returns the current assumption transparency slice for a ticker.

Input:

```json
{
  "ticker": "MSFT"
}
```

Use it when the user asks for assumption critique without requiring the full valuation payload again.

## `stockvaluation.get_growth_anchor`

Returns the mapped growth anchor:

- mapped entity
- region
- year
- confidence
- percentile band
- source date
- warnings

## `stockvaluation.get_reference_data_status`

Returns service/reference-data status. With a ticker, it can include ticker-specific growth-anchor metadata.

## `stockvaluation.explain_failure`

Classifies structured errors into agent-readable categories:

- `unsupported_company`
- `insufficient_financial_data`
- `missing_local_service`
- `missing_configuration`
- `stale_reference_data`
- `non_json_service_response`
- `currency_conversion_failed`
- `upstream_service_error`
- `unknown_failure`

Use it before explaining failures to the user.
