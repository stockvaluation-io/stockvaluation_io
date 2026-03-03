# StockValuation.io

StockValuation.io is a local-first workspace for automated DCF valuation.  
It combines deterministic valuation math with optional LLM-powered research and narrative generation.

![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-03-2026_11_29_PM.png)

**Warning: This valuation is based on public data and Damodaran-style methodology for educational use. It is not financial advice.**

This repository is the community/local edition and is designed to run entirely on your machine.

## Quick Start (5-10 minutes)

### 1. Prerequisites

- Docker + Docker Compose
- Git
- Optional: Node.js v22 (only needed if you run frontend outside Docker)

### 2. Configure environment

```bash
cp .env.example .env
```

Set required keys in `.env` (details in **Environment Keys** below):

- `POSTGRES_PASSWORD`
- `SECRET_KEY`
- `CURRENCY_API_KEY`
- `DEFAULT_PASSWORD`
- At least one LLM key:
  - `OPENAI_API_KEY` or `ANTHROPIC_API_KEY` or `GROQ_API_KEY` or `GEMINI_API_KEY` or `OPENROUTER_API_KEY`

### 3. Start the stack

```bash
docker compose -f docker-compose.local.yml up -d --build
```

### 4. Verify health

```bash
docker compose -f docker-compose.local.yml ps
curl http://localhost:5001/health
curl http://localhost:5002/health
```

Open UI:

- `http://localhost:4200`

### 5. Stop / reset

```bash
# stop
docker compose -f docker-compose.local.yml down

# full reset (containers + volumes)
docker compose -f docker-compose.local.yml down -v
```

## Environment Keys (What, Why, Where)

### Required keys

| Key | Why it exists | Used by |
| :--- | :--- | :--- |
| `POSTGRES_PASSWORD` | Required to boot local Postgres and allow service DB connections. | `postgres`, `valuation-service` |
| `SECRET_KEY` | App signing/session secret for local service security primitives. | `yfinance`, `valuation-agent`, `bullbeargpt` |
| `CURRENCY_API_KEY` | Enables FX conversion when valuing non-USD companies. | `valuation-service` |
| `DEFAULT_PASSWORD` | Creates local default app user credentials used in local runtime. | `valuation-service` |
| One LLM key (`OPENAI_API_KEY` or `ANTHROPIC_API_KEY` or `GROQ_API_KEY` or `GEMINI_API_KEY` or `OPENROUTER_API_KEY`) | Powers analyzer/judge/narrative steps. Without at least one provider key, AI sections degrade or fail. | `valuation-agent`, `bullbeargpt` |

### Recommended keys

| Key | Why it exists | Used by |
| :--- | :--- | :--- |
| `TAVILY_API_KEY` | Improves news retrieval and source quality for research steps. | `valuation-agent`, `bullbeargpt` |
| `DEFAULT_LLM_PROVIDER` | Sets global default provider selection. | `valuation-agent`, `bullbeargpt` |
| `AGENT_LLM_PROVIDER` | Overrides provider for agent tasks (analysis/narrative). | `valuation-agent`, `bullbeargpt` |
| `JUDGE_LLM_PROVIDER` | Overrides provider for judge/review tasks. | `valuation-agent`, `bullbeargpt` |
| `AGENT_LLM_MODEL` | Pin model used for agent path. | `valuation-agent`, `bullbeargpt` |
| `JUDGE_LLM_MODEL` | Pin model used for judge path. | `valuation-agent`, `bullbeargpt` |

### Operational keys (optional)

| Key | Why it exists | Used by |
| :--- | :--- | :--- |
| `VALUATION_SERVICE_TIMEOUT_SECONDS` | Controls timeout from agent -> Java valuation engine. | `valuation-agent` |
| `CORS_ORIGINS` | Allowed browser origins for local API calls. | `bullbeargpt` |
| `BULLBEARGPT_PORT` | Host port mapping override for notebook/chat API. | `docker-compose.local.yml` |
| `LOG_LEVEL` | Runtime log verbosity. | `bullbeargpt` |
| `ENABLE_LLM_GUARD` | Toggle LLM safety/guard behavior where supported. | `valuation-agent` |
| `DUMP_PROMPTS` / `PROMPT_DUMP_DIR` | Saves prompt payloads for debugging local runs. | `valuation-agent`, `bullbeargpt` |
| `CURRENCY_API_BASE_URL` | Override FX provider endpoint if needed. | `valuation-service` |
| `DEFAULT_USERNAME`, `DEFAULT_FIRSTNAME`, `DEFAULT_LASTNAME`, `DEFAULT_CONTACT` | Local seed metadata for default user profile. | `valuation-service` |

## Local Architecture

| Service | Technology | Responsibility |
| :--- | :--- | :--- |
| `frontend` | Angular | Search-first UI and valuation presentation |
| `valuation-service` | Java 21 + Spring Boot | Source of truth for deterministic DCF math |
| `valuation-agent` | Python + Flask | Orchestration: ticker flow, research, overrides, narrative |
| `yfinance` | Python | Market/financial data ingestion facade |
| `bullbeargpt` | Python | Notebook/chat workflow over valuation context |
| `postgres` | PostgreSQL 17 | Local reference data persistence |

## API Flow (Ticker -> Output)

Main entrypoint:

- `POST /api-s/valuate` with `{ "ticker": "AAPL" }`

Pipeline:

1. Segment mapping
2. News/evidence retrieval
3. Baseline Java DCF run
4. Analyzer proposes assumption overrides
5. Java DCF recalculation with overrides
6. Analyst narrative assembly
7. Merged payload returned to frontend

Java valuation endpoint called by agent:

- `POST /api/v1/automated-dcf-analysis/{ticker}/valuation`

## Useful Commands

```bash
# stream logs
docker compose -f docker-compose.local.yml logs -f valuation-agent
docker compose -f docker-compose.local.yml logs -f valuation-service
docker compose -f docker-compose.local.yml logs -f bullbeargpt

# run tests for a service (examples)
cd valuation-agent && pytest
cd bullbeargpt && pytest -q tests
cd valuation-service && mvn -B -ntp test
```

## Troubleshooting

- `403` or CORS failures:
  - Check `CORS_ORIGINS` in `.env`, then restart `bullbeargpt`.
- Compose exits with missing env var:
  - Fill required keys in `.env`, then run `up` again.
- Slow/timeout valuations:
  - Increase `VALUATION_SERVICE_TIMEOUT_SECONDS`.
- Sparse or incomplete ticker output:
  - Some upstream symbols have partial fundamentals.

## Security Note

- This repository is local-first by design.
- `docker-compose.local.yml` is intended for local machine usage.
- Do not deploy these defaults directly to an internet-facing environment.
- Never commit `.env` with real secrets.

## Project Layout

- `valuation-service/` deterministic Java DCF engine
- `valuation-agent/` orchestration and LLM pipeline
- `frontend/` Angular UI
- `bullbeargpt/` notebook/chat service
- `yfinance/` data provider service
- `docker/` local Postgres init + seeds
- `local_data/` generated runtime data
- `.etl/` local ETL/regression workspace (not for VCS)

## Acknowledgments

Core valuation methodology and reference datasets are based on Aswath Damodaran resources:

- https://pages.stern.nyu.edu/~adamodar/New_Home_Page/data.html
