# StockValuation.io

StockValuation.io is a local-first DCF valuation workspace that runs fully on your machine, with structured research and narrative output layered on top of core valuation calculations.

> **Warning: This project is for educational use and is not financial advice.**

![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-05-2026_02_04_PM.png)


## Fast Onboarding

### One-line startup

To install and run StockValuation.io on your machine using our automated script:

```bash
curl -fsSL https://raw.githubusercontent.com/stockvaluation-io/stockvaluation_io/main/install.sh | bash
```

> **Note:** The script will check prerequisites, clone the repository, bootstrap local secrets, and optionally prompt for your `ANTHROPIC_API_KEY` and `CURRENCY_API_KEY` before starting up the containers.

Need `CURRENCY_API_KEY`?

- Create an account at `https://currencybeacon.com`
- Copy your API key from the dashboard
- Paste it into the command above (or `.env`)

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
