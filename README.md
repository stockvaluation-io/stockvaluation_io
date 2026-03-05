# StockValuation.io

StockValuation.io is a local-first DCF valuation workspace that runs fully on your machine, with structured research and narrative output layered on top of core valuation calculations.

![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-05-2026_02_04_PM.png)

**Warning: This project is for educational use and is not financial advice.**

## Fast Onboarding

### One-line startup

Minimum required env vars:

```bash
POSTGRES_PASSWORD='change-me' \
DEFAULT_PASSWORD='change-me' \
YFINANCE_SECRET_KEY='change-me-yfinance-secret' \
VALUATION_AGENT_SECRET_KEY='change-me-valuation-agent-secret' \
BULLBEARGPT_SECRET_KEY='change-me-bullbeargpt-secret' \
VALUATION_SERVICE_JWT_SECRET='change-me-valuation-service-jwt-secret-32chars' \
CURRENCY_API_KEY='your-currency-api-key' \
ANTHROPIC_API_KEY='your-anthropic-api-key' \
docker compose -f docker-compose.local.yml up -d --build
```

Or bootstrap local secrets automatically:

```bash
./scripts/bootstrap_local_secrets.sh
docker compose -f docker-compose.local.yml up -d --build
```

Need `CURRENCY_API_KEY`?

- Create an account at `https://currencybeacon.com`
- Copy your API key from the dashboard
- Paste it into the command above (or `.env`)

Open:

- `http://localhost:4200`

Health checks:

```bash
curl http://localhost:5001/health
curl http://localhost:5002/health
```

### `.env` setup (recommended for regular use)

```bash
cp .env.example .env
```

Then fill the required keys below and run:

```bash
./scripts/bootstrap_local_secrets.sh
docker compose -f docker-compose.local.yml up -d --build
```

## Required Keys (What they do)

| Key | Why it is needed | Where it is used |
| :--- | :--- | :--- |
| `POSTGRES_PASSWORD` | Required DB password for local startup. | `postgres`, `valuation-service` |
| `DEFAULT_PASSWORD` | Required default local user credential in valuation-service. | `valuation-service` |
| `YFINANCE_SECRET_KEY` | Required Flask secret for yfinance session security. | `yfinance` |
| `VALUATION_AGENT_SECRET_KEY` | Required Flask secret for valuation-agent session security. | `valuation-agent` |
| `BULLBEARGPT_SECRET_KEY` | Required Flask secret for bullbeargpt session security. | `bullbeargpt` |
| `VALUATION_SERVICE_JWT_SECRET` | Required JWT signing/validation secret (32+ chars). | `valuation-service` |
| `CURRENCY_API_KEY` | Used to fetch FX rates during valuation when market quote currency and financial-reporting currency differ. Get it from `https://currencybeacon.com` dashboard. | `valuation-service` |
| One LLM key (`ANTHROPIC_API_KEY` or `OPENAI_API_KEY` or `GROQ_API_KEY` or `GEMINI_API_KEY` or `OPENROUTER_API_KEY`) | Used for AI analysis/research/narrative generation. | `valuation-agent`, `bullbeargpt` |

## Optional Keys (Useful in practice)

| Key | Why it is useful | Where it is used |
| :--- | :--- | :--- |
| `POSTGRES_PASSWORD` | Required DB password for local Postgres and Java datasource. | `postgres`, `valuation-service` |
| `YFINANCE_SECRET_KEY` | Service secret key for yfinance. | `yfinance` |
| `VALUATION_AGENT_SECRET_KEY` | Service secret key for valuation-agent. | `valuation-agent` |
| `BULLBEARGPT_SECRET_KEY` | Service secret key for bullbeargpt. | `bullbeargpt` |
| `VALUATION_SERVICE_JWT_SECRET` | JWT signing/validation secret used by valuation-service auth filter (use 32+ chars). | `valuation-service` |
| `DEFAULT_PASSWORD` | Required seeded local user password. | `valuation-service` |
| `INTERNAL_API_KEY` | Optional shared API key to restrict internal endpoints (recommended). | `valuation-agent`, `bullbeargpt`, `yfinance` |
| `TAVILY_API_KEY` | Better external news/evidence retrieval. | `valuation-agent`, `bullbeargpt` |
| `DEFAULT_LLM_PROVIDER` | Global provider default. | `valuation-agent`, `bullbeargpt` |
| `AGENT_LLM_PROVIDER` | Force provider for agent path. | `valuation-agent`, `bullbeargpt` |
| `JUDGE_LLM_PROVIDER` | Force provider for judge/review path. | `valuation-agent`, `bullbeargpt` |
| `AGENT_LLM_MODEL` | Pin model for agent path. | `valuation-agent`, `bullbeargpt` |
| `JUDGE_LLM_MODEL` | Pin model for judge path. | `valuation-agent`, `bullbeargpt` |
| `VALUATION_SERVICE_TIMEOUT_SECONDS` | Prevent timeout failures for heavy valuations. | `valuation-agent` |
| `CORS_ORIGINS` | Controls which local browser origins can call APIs. | `bullbeargpt` |
| `BULLBEARGPT_PORT` | Changes notebook/chat API host port mapping. | Compose runtime |
| `LOG_LEVEL` | Controls runtime log verbosity. | `bullbeargpt` |
| `DUMP_PROMPTS`, `PROMPT_DUMP_DIR` | Writes prompt payloads for local debugging. | `valuation-agent`, `bullbeargpt` |

## What Runs Locally

| Service | Purpose | Local URL |
| :--- | :--- | :--- |
| `frontend` | Main UI | `http://localhost:4200` |
| `valuation-service` | Core valuation API | `http://localhost:8081` |
| `valuation-agent` | Orchestration/research API | `http://localhost:5001` |
| `bullbeargpt` | Notebook/chat API | `http://localhost:5002` |
| `postgres` | Local persistence | `localhost:4322` |

## Core API Flow

Main entrypoint:

- `POST /api-s/valuate` with `{ "ticker": "AAPL" }`

Pipeline:

1. Segment mapping
2. Research/evidence retrieval
3. Baseline valuation run
4. Assumption override generation
5. Recalculation with overrides
6. Narrative assembly
7. Merged response returned to UI

## Useful Commands

```bash
# show running containers
docker compose -f docker-compose.local.yml ps

# logs
docker compose -f docker-compose.local.yml logs -f valuation-agent
docker compose -f docker-compose.local.yml logs -f valuation-service
docker compose -f docker-compose.local.yml logs -f bullbeargpt

# stop
docker compose -f docker-compose.local.yml down

# full reset (containers + volumes)
docker compose -f docker-compose.local.yml down -v
```

## Troubleshooting

- `403`/CORS issues:
  - Verify `CORS_ORIGINS`, then restart `bullbeargpt`.
- Missing env variable error:
  - Fill required keys in `.env`, rerun compose.
- Valuation timeouts:
  - Increase `VALUATION_SERVICE_TIMEOUT_SECONDS`.
- Weak AI output:
  - Check LLM key validity, quota, and billing.

## Security

- Local-first defaults are meant for development on your machine.
- Do not deploy these defaults directly to internet-facing environments.
- Never commit `.env` with real credentials.

## Project Layout

- `frontend/` UI
- `valuation-service/` core valuation engine
- `valuation-agent/` orchestration layer
- `bullbeargpt/` notebook/chat
- `yfinance/` market data facade
- `docker/` local DB init/seed scripts
- `local_data/` runtime data generated locally

## Acknowledgments

Core methodology and reference data are based on Aswath Damodaran resources:

- https://pages.stern.nyu.edu/~adamodar/New_Home_Page/data.html
