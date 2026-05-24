---
name: stockvaluation-io
description: Use local StockValuation MCP tools to fetch deterministic DCF JSON, critique assumptions, and write educational valuation reports in the user's workspace.
version: 2.0.0-agent-native
homepage: https://github.com/stockvaluation-io/stockvaluation_io
---

# StockValuation.io Agent Valuation

Use this skill when the user asks you to value a public company, critique DCF assumptions, build scenarios, explain valuation drivers, or troubleshoot the local StockValuation agent-native service.

The product surface is the user's agent. The deterministic valuation math comes from local MCP tools. You write the educational report from the returned JSON.

## Required Workflow

1. Call `stockvaluation.health`.
2. Call `stockvaluation.value_ticker` with the ticker symbol.
3. Inspect the returned DCF JSON, assumptions, growth anchor, reference-data status, warnings, and failure shape.
4. Apply the method checks in `{baseDir}/references/damodaran-method.md` and `{baseDir}/references/assumption-checks.md`.
5. If the user asks for changed assumptions, ask for confirmation before calling `stockvaluation.recalculate`.
6. Use `stockvaluation.recalculate` for every scenario value. Do not hand-compute valuation math.
7. Write the report using `{baseDir}/references/report-template.md`.
8. Apply `{baseDir}/references/no-advice-policy.md` before finalizing.

## Tool Rules

- Use the MCP tools documented in `{baseDir}/references/mcp-tools.md`.
- Treat MCP JSON as the source of truth for valuation output.
- Do not invent missing service fields, missing financial data, growth-anchor confidence, or scenario math.
- If a tool returns `ok: false`, use `stockvaluation.explain_failure` and explain the failure plainly.
- If reference data is missing, stale, weak, or low confidence, say so in the report.
- For financial-sector companies, unsupported companies, or insufficient data, stop and explain the limitation. Do not produce a synthetic valuation.

## Report Rules

- Frame the report as educational use only and not financial advice.
- Avoid buy, sell, hold, target-price, and personalized recommendation language.
- Separate market price, model intrinsic value, and assumptions. Do not turn model output into an instruction.
- Explain key drivers: growth, margins, reinvestment, cost of capital, terminal value, tax rate, and accounting adjustments.
- Include data-quality notes and service/version metadata when returned.
- Use clear uncertainty language when Yahoo Finance coverage or reference-data matching is weak.

## References

- `{baseDir}/references/mcp-tools.md`
- `{baseDir}/references/damodaran-method.md`
- `{baseDir}/references/report-template.md`
- `{baseDir}/references/no-advice-policy.md`
- `{baseDir}/references/assumption-checks.md`
- `{baseDir}/references/accounting-adjustments.md`
- `{baseDir}/references/troubleshooting.md`
