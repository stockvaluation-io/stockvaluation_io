# PRD: Agent-Native StockValuation Product

## Problem Statement

StockValuation.io is a local-first valuation product for agent users. The user should value a public company from Claude Code, Codex, or another MCP-capable agent. StockValuation provides the governed skill pack, the local MCP server, and the deterministic valuation service that returns fresh DCF JSON.

The product must not ask users to learn or operate a separate application surface. The agent is the interface. The local services provide valuation math and structured evidence; the installed skill teaches the agent how to interpret that evidence and write an educational report.

## Product Surface

1. Install or update the StockValuation skill pack.
2. Install or update local MCP configuration.
3. Start, stop, and inspect the Docker-backed valuation service stack.
4. Expose StockValuation MCP tools over stdio.
5. Return structured valuation JSON, assumptions, growth anchors, reference-data status, recalculation output, and explainable failures.
6. Guide the user's agent to write educational, no-advice valuation reports from MCP JSON.

## Runtime Model

The v1 runtime is Docker-only:

1. `postgres` stores local reference data.
2. `yfinance` fetches market and company data inside the Docker network.
3. `valuation-service` owns deterministic DCF math on `http://localhost:8081`.
4. `stockvaluation_agent_native` runs on the host as the installer, service controller, and MCP adapter.

No host-native Java, Postgres, or yfinance setup is supported for v1.

## User Stories

1. As an agent user, I can install the skill pack so my agent knows the valuation workflow.
2. As an agent user, I can install MCP configuration so my agent can call `stockvaluation.*` tools.
3. As an agent user, I can start the local valuation stack without understanding internal services.
4. As an agent user, I can ask my agent to value a ticker and receive an educational report based on deterministic DCF JSON.
5. As an agent user, I can ask for scenario recalculation and see requested, mapped, unsupported, and effective assumptions separately.
6. As an agent user, I can see clear failure explanations when data, currency conversion, service readiness, or ticker validation blocks a valuation.
7. As a maintainer, I can validate the supported product with agent-native tests, valuation-service tests, optional yfinance tests, and MCP smoke checks.

## MCP Contract

The public agent-facing tool contract has seven tools:

1. `stockvaluation.health`
2. `stockvaluation.value_ticker`
3. `stockvaluation.recalculate`
4. `stockvaluation.get_assumptions`
5. `stockvaluation.get_growth_anchor`
6. `stockvaluation.get_reference_data_status`
7. `stockvaluation.explain_failure`

All tools return structured JSON. The MCP adapter may validate inputs, map supported scenario overrides, summarize service payloads, and classify failures. It must not compute DCF values itself.

## Skill Requirements

The skill pack must instruct agents to:

1. Treat MCP JSON as the source of truth.
2. Never hand-compute valuation math.
3. Use `stockvaluation.recalculate` for scenario math.
4. Explain assumptions, accounting adjustments, data quality, and growth anchors when present.
5. Preserve explicit failure categories instead of inventing valuation numbers.
6. Frame reports as educational use only and not financial advice.
7. Avoid recommendation language such as buy, sell, hold, target price, or “should invest.”

## Environment Requirements

Required local setup values:

1. `POSTGRES_PASSWORD`
2. `YFINANCE_SECRET_KEY`
3. `DEFAULT_PASSWORD`
4. `VALUATION_SERVICE_JWT_SECRET`

Optional runtime values include `CORS_ORIGINS`, `CURRENCY_PROVIDER_BASE_URL`, `POSTGRES_USER`, `POSTGRES_DB`, and default local admin profile fields. Currency conversion uses the keyless Frankfurter provider by default.

## Acceptance Criteria

1. `./install.sh install` installs skills and MCP config without installing host-native service dependencies.
2. `./install.sh check-env` checks Docker, Docker Compose, daemon reachability, required ports, and required local env values.
3. `./install.sh start` starts only `postgres`, `yfinance`, and `valuation-service`.
4. `stockvaluation.health` reports structured service readiness.
5. MCP tool discovery lists all seven StockValuation tools.
6. `stockvaluation.value_ticker` returns DCF JSON for supported tickers when the local stack is healthy.
7. `stockvaluation.recalculate` returns transparent scenario mapping and effective assumptions.
8. Unsupported or blocked valuations return stable failure categories and no invented numbers.
9. Generated reports remain educational and avoid investment recommendations.
10. CI covers agent-native tests, installer smoke, MCP stdio smoke, valuation-service tests, and yfinance tests when present.

## Out Of Scope

1. A separate graphical application surface.
2. A report-writing API or chat service.
3. A `sv value` command or any CLI-generated valuation report.
4. Moving valuation math into MCP, the CLI, the skill pack, or the user's agent.
5. Host-native service installation for v1.
6. Investment recommendations.
