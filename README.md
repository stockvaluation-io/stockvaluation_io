# StockValuation.io

[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/stockvaluation-io/stockvaluation_io)

Open-source DCF valuation app that turns a stock ticker into transparent Damodaran-style assumptions, valuation output, bull/bear thesis, and a chat workspace for building your own narrative.

> **Warning: This project is for educational use and is not financial advice. Its your valuation and you agree by using this project.**

StockValuation.io helps investors understand the story behind a valuation, not just the final number. Search a company, generate a DCF valuation, inspect the assumptions, challenge the bull and bear cases, then use chat to build and refine your own narrative as your view changes.

## What It Does

- Turns a stock ticker into a structured DCF valuation.
- Shows the assumptions behind the valuation instead of hiding them in a black box.
- Builds bull and bear thesis material around the company narrative.
- Gives you a chat workspace to develop your own investment narrative and thesis.
- Lets you test your own view by changing assumptions and recalculating value.
- Runs locally so your keys, prompts, and analysis stay on your machine.

## Why It Exists

Most valuation tools either give you a spreadsheet or a black-box target price. StockValuation.io is built for transparent reasoning: the assumptions, narrative, and valuation logic are visible so you can decide what you believe.

## Demo

<a href="https://video.golpoai.com/share/34af4546-fb30-49cb-a956-0f59d985382a" target="_blank" rel="noopener noreferrer">Watch the product video</a>

![StockValuation.io Automated DCF Analysis](./assets/StockValuation-io-—-Automated-DCF-Analysis-03-05-2026_02_04_PM.png)

## Quick Start

Run the local app with the installer:

```bash
curl -fsSL https://raw.githubusercontent.com/stockvaluation-io/stockvaluation_io/main/install.sh | bash
```

Then open:

```text
http://localhost:4200
```

The installer checks prerequisites, downloads the project if needed, bootstraps local secrets, and prompts for API keys.

## API Keys

For the full experience, add:

- One LLM key: `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, `GEMINI_API_KEY`, `GROQ_API_KEY`, or `OPENROUTER_API_KEY`.
- `TAVILY_API_KEY` for live company research and recent news.
- `CURRENCY_API_KEY` for exchange-rate handling when market price and statements use different currencies.

You can create free accounts at [tavily.com](https://tavily.com) and [currencybeacon.com](https://currencybeacon.com).

## Current Limits

- The system depends on Yahoo Finance data. If Yahoo Finance does not provide the required company data, the valuation can fail.
- Historical coverage is limited because Yahoo Finance typically provides only about 5 years of history.
- Financial sector companies are not supported.

## Security

- Local-first defaults are meant for development on your machine.
- Do not deploy these defaults directly to internet-facing environments.
- Never commit `.env` with real credentials.

## Star The Project

If StockValuation.io helps you learn valuation or build better investment theses, star the repo. It is the clearest signal that the project is worth keeping up to date.

## Cite
> Note: If this project is helpful to you, then please star. Otherwise, I would not know if I should keep this up-to-date. This project requires a consistent data update.
```
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
