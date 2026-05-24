# Assumption Checks

Use these checks after reading MCP JSON.

## Growth

- Compare requested and effective growth assumptions.
- Compare growth to the growth-anchor percentile band.
- Challenge growth above the anchor's upper band unless the user supplied a clear reason.
- If confidence is weak, say the anchor is directional.

## Margins

- Compare target margins to current margins.
- Explain whether expansion requires pricing power, scale, mix, cost reduction, or operating leverage.
- Flag abrupt margin expansion.

## Reinvestment

- Review sales-to-capital assumptions.
- Lower sales-to-capital means higher reinvestment needs.
- Do not let a high-growth scenario ignore reinvestment.

## Cost Of Capital

- Explain the risk-free rate, equity risk premium, and convergence when returned.
- Challenge low discount-rate assumptions if growth and business risk are also high.

## Terminal Growth

- Terminal growth should be mature and bounded.
- Flag terminal growth above long-run mature-economy logic.

## Scenario Discipline

- Ask before recalculating with overrides.
- Use only supported `stockvaluation.recalculate` override keys.
- Report requested, mapped, unsupported, and effective assumptions separately.
