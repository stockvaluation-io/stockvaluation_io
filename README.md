# StockValuation.io

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/stockvaluation-io/stockvaluation_io)

StockValuation.io is an agent-native, local-first DCF valuation workspace. It installs valuation skills for Claude Code/Codex-style agents, runs deterministic valuation math locally, and exposes that math through a local MCP server.

> Warning: This project is for educational use and is not financial advice. It helps explain valuation assumptions and model output; it does not provide personalized recommendations.

## What It Does

- Installs StockValuation skills into supported agent clients.
- Installs local MCP configuration so the agent can call `stockvaluation.*` tools.
- Runs the local deterministic valuation service used for DCF math.
- Returns structured MCP JSON for baseline valuations, assumptions, growth anchors, recalculation scenarios, reference-data status, and failures.
- Leaves report writing to the user's existing agent, guided by the installed skills.

## Agent-Native Flow

```text
Install skills -> install MCP config -> start local service -> agent calls MCP -> agent writes educational report
```

The CLI is operational glue only. It does not value companies, write valuation reports, or provide investment recommendations.

## Quick Start

From a local checkout:

Docker Desktop or a compatible Docker Engine with Compose is required for the v1 local runtime. No native Java/Postgres/yfinance runtime is installed or supported for v1.

```bash
./install.sh install
./install.sh check-env
./install.sh start
```

Then open your agent client and ask it to value a supported ticker. The installed skill instructs the agent to call `stockvaluation.value_ticker` and write an educational report from the returned JSON.

You can also use the Python CLI directly:

```bash
python3.11 -m stockvaluation_agent_native.cli install all --client all
python3.11 -m stockvaluation_agent_native.cli service start
python3.11 -m stockvaluation_agent_native.cli service status
```

## MCP Tools

- `stockvaluation.health`
- `stockvaluation.value_ticker`
- `stockvaluation.recalculate`
- `stockvaluation.get_assumptions`
- `stockvaluation.get_growth_anchor`
- `stockvaluation.get_reference_data_status`
- `stockvaluation.explain_failure`

MCP tools return structured JSON. Scenario math must come from `stockvaluation.recalculate`; agents should not hand-compute valuation outputs.

## Local Configuration

The canonical local runtime is `docker-compose.local.yml`. The agent-native service path starts only the hidden service plumbing required for valuation math:

- `postgres`
- `yfinance`
- `valuation-service`

Required local environment values are documented in `.env.example`. Use `scripts/bootstrap_local_secrets.sh` as the safe starting point for generated local secrets. Do not commit `.env`.

Currency conversion is handled inside `valuation-service` with the keyless Frankfurter provider. The default provider base URL is `https://api.frankfurter.dev/v2`; no currency API key is required for v1.

## Current Limits

- The system depends on Yahoo Finance data. If Yahoo Finance does not provide required company data, valuation can fail.
- Historical coverage is limited.
- Financial-sector companies are not supported in the first agent-native release.
- Growth anchors are reference data for critique, not proof that a company will match an industry distribution.

## Verification

Primary checks:

```bash
python3.11 -m pytest tests/agent_native -q
cd valuation-service && mvn -B -ntp test
./scripts/local_smoke.sh --agent-native --ticker MSFT
```

Run `./scripts/local_release_check.sh` when deterministic valuation math, baselines, or release readiness are in scope.

## Security

- Never paste real API keys into chat.
- Never commit `.env`, prompt dumps, or local runtime data.
- MCP responses and installer output should not expose secret values.
- Local defaults are for development on one machine; do not deploy them directly to internet-facing environments.

## Cite

```text
@misc{stockvaluation_io,
  author = {Pradeep Singh},
  title = {StockValuation.io: Local-first stock valuation workspace},
  year = {2026},
  publisher = {GitHub},
  url = {https://github.com/stockvaluation-io/stockvaluation_io}
}
```

## Acknowledgments

Core methodology and reference data are based on Aswath Damodaran's resources:

- https://pages.stern.nyu.edu/~adamodar/New_Home_Page/data.html
