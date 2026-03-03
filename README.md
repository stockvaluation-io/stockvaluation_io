# StockValuation.io

StockValuation.io is a local-first development platform for performing automated stock valuations using the Discounted Cash Flow (DCF) method. It integrates deterministic financial modelling with LLM-powered qualitative analysis to provide deep financial insights and automated reports.

This is the community version of the project, designed to be run entirely on your local machine with no external hosted dependencies for the core workflow.

## Project Overview

The system provides a local workstation for Damodaran-style analysis. It uses a Java engine for precise mathematical calculations and Python-based agents for research, assumption critique, and narrative generation.

Key Principles:
- Local-First: All persistence is local (Postgres, SQLite, and JSON files).
- Bring Your Own Key (BYOK): Users provide their own API keys for LLMs and research tools.
- No Accounts: No login or credit management system is required for the local workstation.
- Deterministic Math: Financial calculations are handled by code, not by LLMs, ensuring consistency and accuracy.

## System Architecture

The project consists of several microservices orchestrated via Docker.

| Service | Technology | Description |
| :--- | :--- | :--- |
| frontend | Angular | A search-first interface for analyzing stocks and visualizing DCF results. |
| valuation-service | Java (Spring Boot) | The math authority that performs DCF calculations based on reference data. |
| valuation-agent | Python | The orchestration layer that coordinates LLMs for research and assumption derivation. |
| yfinance | Python | A microservice dedicated to fetching real-time market and fundamental data. |
| bullbeargpt | Python | Handles conversational AI features and chat-based analysis. |
| postgres | PostgreSQL 17 | Primary database for reference data like industry averages and risk-free rates. |

## Prerequisites

Ensure you have the following installed:
- Docker and Docker Compose
- Node.js (v22) - if you intend to run the frontend outside of Docker.
- API keys for at least one LLM provider (OpenAI, Anthropic, Gemini, or Groq).

## Detailed Setup and API Keys

The platform requires several API keys to function fully. These are configured in your `.env` file.

### 1. Environment Configuration
Copy the template to create your local environment file:
```bash
cp .env.example .env
```

### 2. Mandatory LLM Keys
You must provide at least one key for the AI agents to function.
- **OpenAI**: Get a key at [platform.openai.com](https://platform.openai.com/)
- **Anthropic**: Get a key at [console.anthropic.com](https://console.anthropic.com/)
- **Gemini**: Get a key at [aistudio.google.com](https://aistudio.google.com/)
- **Groq**: Get a key at [console.groq.com](https://console.groq.com/)

### 3. Search and Research Keys (Tavily)
- **TAVILY_API_KEY**: Used by the `valuation-agent` to perform live web searches for news, earnings transcripts, and macro-economic data.
- **Usage**: Highly recommended for the automated research phase.
- **Where to get**: Sign up at [tavily.com](https://tavily.com/). They offer a free tier for developers.

### 4. Currency Conversion Keys (CurrencyBeacon)
- **CURRENCY_API_KEY**: Used by the `valuation-service` to fetch the latest exchange rates. This is essential for valuing companies that report in non-USD currencies (e.g., European or Asian stocks).
- **Usage**: Used to normalize financial data into a consistent currency for the DCF model.
- **Where to get**: Sign up at [currencybeacon.com](https://currencybeacon.com/).

## Running the Platform

To start the entire stack, use the provided local-first Docker Compose file.

```bash
docker compose -f docker-compose.local.yml up --build
```

Once the build is complete and containers are healthy:
- **Frontend**: [http://localhost:4200](http://localhost:4200)
- **Valuation Service API**: [http://localhost:8081](http://localhost:8081)
- **Valuation Agent API**: [http://localhost:5001](http://localhost:5001)

The first run may take several minutes as it downloads Docker images and installs Node.js dependencies for the frontend.

## Repository Structure

- `valuation-service/`: Core Java Spring Boot backend (DCF math).
- `valuation-agent/`: Python-based LLM orchestrator.
- `frontend/`: Angular-based search and results UI.
- `yfinance/`: Data fetcher utility.
- `bullbeargpt/`: Conversational AI components.
- `local_data/`: Automatically generated local storage for valuation history and audits.
- `docker/`: SQL seed files and configuration for the local database.

## Troubleshooting

- **Database Errors**: Ensure port 4322 is available on your host machine.
- **LLM Failures**: Verify your keys are active and that you have configured the `DEFAULT_LLM_PROVIDER` in your `.env` file to match one of your provided keys.
- **Missing Data**: Some ticker data may be incomplete on Yahoo Finance; the valuation agent will attempt to fill gaps using Tavily search if enabled.

## Acknowledgments and Attribution

This project is built upon the foundational work and datasets provided by **Aswath Damodaran**, Professor of Finance at the Stern School of Business at New York University.

- **Methodology**: The core valuation logic and DCF modelling principles used in this platform are based on the frameworks developed by Professor Damodaran.
- **Reference Datasets**: The foundational data for industry averages, risk-free rates, equity risk premiums, and sector-specific financial metrics are sourced from [Damodaran Online](https://pages.stern.nyu.edu/~adamodar/New_Home_Page/data.html), which is an invaluable resource for the global finance community.