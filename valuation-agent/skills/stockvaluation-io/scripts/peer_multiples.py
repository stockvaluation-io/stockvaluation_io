#!/usr/bin/env python3
"""Fetch peer comparable multiples (PE, EV/EBITDA, growth) for a ticker.

Outputs a stable JSON table that build_report.py can render as the
"Peer Multiples" section. Uses yfinance info endpoints with graceful
degradation: missing metrics are emitted as null (not dropped), so the
table shape is stable even when a peer quote is unavailable.

Usage:
    python3 peer_multiples.py MSFT [AAPL GOOGL AMZN ORCL] > peers.json
"""

from __future__ import annotations

import json
import sys
import time

try:
    import yfinance as yf
except Exception as exc:  # pragma: no cover - import guard
    sys.stderr.write(f"yfinance unavailable: {exc}\n")
    sys.exit(2)

DEFAULT_PEER_SETS: dict[str, list[str]] = {
    "MSFT": ["AAPL", "GOOGL", "AMZN", "ORCL", "ADBE"],
    "GOOGL": ["MSFT", "AAPL", "AMZN", "META", "NFLX"],
    "AAPL": ["MSFT", "GOOGL", "AMZN", "META", "ORCL"],
    "AMZN": ["MSFT", "GOOGL", "AAPL", "META", "ORCL"],
    "NVDA": ["AMD", "AVGO", "QCOM", "INTC", "TSM"],
    "META": ["GOOGL", "MSFT", "AMZN", "AAPL", "NFLX"],
}


def _num(value) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def fetch(ticker: str) -> dict:
    out: dict = {
        "ticker": ticker,
        "price": None,
        "trailing_pe": None,
        "forward_pe": None,
        "ev_ebitda": None,
        "revenue_growth": None,
        "gross_margin": None,
        "operating_margin": None,
        "market_cap": None,
    }
    try:
        info = yf.Ticker(ticker).info or {}
    except Exception as exc:
        out["error"] = f"{type(exc).__name__}: {exc}"
        return out
    out["price"] = _num(info.get("currentPrice") or info.get("regularMarketPrice"))
    out["trailing_pe"] = _num(info.get("trailingPE"))
    out["forward_pe"] = _num(info.get("forwardPE"))
    out["ev_ebitda"] = _num(info.get("enterpriseToEbitda"))
    out["revenue_growth"] = _num(info.get("revenueGrowth"))
    out["gross_margin"] = _num(info.get("grossMargins"))
    out["operating_margin"] = _num(info.get("operatingMargins"))
    out["market_cap"] = _num(info.get("marketCap"))
    return out


def main() -> int:
    args = [a for a in sys.argv[1:] if a]
    if not args:
        sys.stderr.write("usage: peer_multiples.py TICKER [PEER...]\n")
        return 2
    ticker = args[0].upper()
    peers = [a.upper() for a in args[1:]] or DEFAULT_PEER_SETS.get(ticker, [])
    rows = [fetch(ticker), *[fetch(p) for p in peers]]
    # yfinance throttling guard: avoid hammering the API.
    print(json.dumps({"ticker": ticker, "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
                      "peers": rows}, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
