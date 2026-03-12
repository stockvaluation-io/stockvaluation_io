# StockValuation.io

StockValuation.io is a local-first DCF valuation workspace that runs fully on your machine, with structured research and narrative output layered on top of core valuation calculations using Aswath Damodaran methodology.

> **Warning: This project is not a financial advice.**


## Fast Onboarding

### One-line startup

To install and run StockValuation.io on your machine using our automated script:

```bash
curl -fsSL https://raw.githubusercontent.com/stockvaluation-io/stockvaluation_io/main/install.sh | bash
```

> **Note:** The script will check prerequisites, download the project if needed, bootstrap local secrets, and interactively prompt for your API keys. It supports **Anthropic, OpenAI, Gemini, Groq, and OpenRouter** for LLM access, plus **`TAVILY_API_KEY`** and **`CURRENCY_API_KEY`** before starting up the containers.

Need these APIs?

- **Tavily (Web Search):** Create a free account at [tavily.com](https://tavily.com)
- **CurrencyBeacon (FX Rates):** Create a free account at [currencybeacon.com](https://currencybeacon.com)

![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-05-2026_02_04_PM.png)


## What Runs Locally

| Service | Purpose | Local URL |
| :--- | :--- | :--- |
| `frontend` | Main UI | `http://localhost:4200` |
| `valuation-service` | Core valuation API | `http://localhost:8081` |
| `valuation-agent` | Orchestration/research API | `http://localhost:5001` |
| `bullbeargpt` | Notebook/chat API | `http://localhost:5002` |
| `postgres` | Local persistence | `localhost:4322` |

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
