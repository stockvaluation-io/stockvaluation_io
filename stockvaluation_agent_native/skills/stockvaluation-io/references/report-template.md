# Educational Valuation Report Template

Use this structure after `stockvaluation.value_ticker` returns `ok: true`. Keep the wording specific to the JSON you received.

## Educational-Use Framing

This report is for educational use only and is not financial advice. It explains one local DCF model output and the assumptions that drive it.

## Company And Model Snapshot

- Company:
- Ticker:
- Currency:
- Model:
- Projection pattern:
- Service/model/data versions:

## Valuation Output

- Market price:
- Model intrinsic value per share:
- Equity value:
- Main gap between price and model value:

Describe the gap as a model result, not as a recommendation.

## Key Assumptions

### Growth

State revenue growth assumptions, rationale, and growth-anchor confidence.

### Margins

State current and target operating margin assumptions. Explain what operating leverage must be true.

### Reinvestment

State sales-to-capital assumptions. Explain how much growth depends on capital efficiency.

### Cost Of Capital

State risk-free rate, initial cost of capital, terminal cost of capital, and source notes.

### Terminal Value

State terminal growth and terminal cost of capital. Explain terminal-value sensitivity.

### Tax And Accounting Adjustments

Explain tax-rate assumptions, R&D capitalization, lease conversion, and option/warrant adjustments when returned.

## Growth Anchor And Reference Data

Summarize mapped entity, region, year, confidence, percentile band, source date, and warnings.

## Scenario Table

Only include scenarios that were recalculated through `stockvaluation.recalculate`.

| Scenario | Changed assumptions | Model intrinsic value | Notes |
| --- | --- | --- | --- |
| Base | Baseline MCP output |  |  |
| Conservative | Recalculated by MCP |  |  |
| Optimistic | Recalculated by MCP |  |  |
| Market-implied | Recalculated or returned by MCP |  |  |

## Data Quality And Limitations

- Yahoo Finance coverage notes:
- Missing or stale data:
- Unsupported-company warnings:
- Growth-anchor confidence warnings:

## What Would Change The Model

List the assumptions that matter most. Keep this educational and non-prescriptive.
