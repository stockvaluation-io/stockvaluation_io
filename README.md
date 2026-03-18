# StockValuation.io

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/stockvaluation-io/stockvaluation_io)

StockValuation.io is a local-first stock valuation workspace that runs fully on your machine, with structured research and narrative output layered on top of core valuation calculations. Build your own stock narratives. 

> **Warning: This project is for educational use and is not financial advice. Its your valuation and you agree by using this project.**


## Fast Onboarding

### One-line startup

To install and run StockValuation.io on your machine using our automated script:

```bash
curl -fsSL https://raw.githubusercontent.com/stockvaluation-io/stockvaluation_io/main/install.sh | bash
```

> **Note:** The script will check prerequisites, download the project if needed, bootstrap local secrets, and interactively prompt for your API keys. It supports **Anthropic, OpenAI, Gemini, Groq, and OpenRouter** for LLM access, plus **`TAVILY_API_KEY`** and **`CURRENCY_API_KEY`** before starting up the containers.

### Why These Keys Are Required

- **LLM key (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROQ_API_KEY`, or `OPENROUTER_API_KEY`):** This is needed for the AI features. Without it, the app can still run, but it will not generate the narrative, explain the assumptions, or help you build an investment thesis in chat.
- **`TAVILY_API_KEY`:** This is needed for live web search. Without it, the app cannot pull in recent company news, earnings coverage, or other outside research used in the analysis.
- **`CURRENCY_API_KEY`:** This is needed for exchange rates. Without it, the app cannot properly handle companies where the stock price and financial statements use different currencies.

Need these APIs?

- **Tavily (Web Search):** Create a free account at [tavily.com](https://tavily.com)
- **CurrencyBeacon (FX Rates):** Create a free account at [currencybeacon.com](https://currencybeacon.com)

## Product Video

<a href="https://video.golpoai.com/share/34af4546-fb30-49cb-a956-0f59d985382a" target="_blank" rel="noopener noreferrer">Watch the product video</a>


![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-05-2026_02_04_PM.png)

## What Runs Locally

| Service | Purpose | Local URL |
| :--- | :--- | :--- |
| `frontend` | Main UI | `http://localhost:4200` |
| `valuation-service` | Core valuation API | `http://localhost:8081` |
| `valuation-agent` | Orchestration/research API | `http://localhost:5001` |
| `bullbeargpt` | Notebook/chat API | `http://localhost:5002` |
| `postgres` | Local persistence | `localhost:4322` |

## Common Failure Reasons

- The system depends on Yahoo Finance data. If Yahoo Finance does not provide the required company data, the valuation can fail.
- Historical coverage is limited because Yahoo Finance typically provides only about 5 years of history.
- Financial sector companies are not supported.

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
