#!/usr/bin/env python3
"""Run agent-native QA through the local MCP stdio path."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any

DEFAULT_TICKERS = [
    "MSFT",
    "AAPL",
    "GOOGL",
    "AMZN",
    "NVDA",
    "META",
    "TSLA",
    "UBER",
    "KO",
    "PG",
    "JNJ",
    "LLY",
    "GILD",
    "ASML",
    "SAP.DE",
    "INFY",
    "BABA",
    "TSM",
    "RIVN",
    "SNAP",
]

DEFAULT_SCENARIOS = ["MSFT", "NVDA", "KO", "ASML", "RIVN"]

PROHIBITED_PATTERNS = [
    re.compile(r"\bbuy\b", re.IGNORECASE),
    re.compile(r"\bsell\b", re.IGNORECASE),
    re.compile(r"\bhold\b", re.IGNORECASE),
    re.compile(r"target price", re.IGNORECASE),
    re.compile(r"should invest", re.IGNORECASE),
    re.compile(r"\brecommendation\b", re.IGNORECASE),
]

SCENARIO_OVERRIDES = {
    "revenue_growth": 0.06,
    "operating_margin": 25.0,
    "sales_to_capital": 2.0,
    "wacc": 0.09,
    "terminal_growth": 0.025,
    "tax_rate": 0.21,
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run StockValuation agent-native MCP QA.")
    parser.add_argument("--tickers", default=",".join(DEFAULT_TICKERS))
    parser.add_argument("--scenarios", default=",".join(DEFAULT_SCENARIOS))
    parser.add_argument("--output-dir", type=Path, default=None)
    parser.add_argument("--python", default=sys.executable)
    parser.add_argument("--timeout-seconds", type=int, default=240)
    return parser.parse_args()


def call_tool(python_bin: str, name: str, arguments: dict[str, Any], timeout: int) -> dict[str, Any]:
    request = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": "tools/call",
        "params": {"name": name, "arguments": arguments},
    }
    completed = subprocess.run(
        [python_bin, "-m", "stockvaluation_agent_native.mcp_server"],
        input=json.dumps(request) + "\n",
        capture_output=True,
        text=True,
        timeout=timeout,
        check=False,
    )
    if completed.returncode != 0:
        raise RuntimeError(f"MCP server exited with {completed.returncode}: {completed.stderr.strip()}")
    try:
        response = json.loads(completed.stdout.strip().splitlines()[-1])
    except (json.JSONDecodeError, IndexError) as exc:
        raise RuntimeError(f"MCP server returned invalid JSON-RPC: {completed.stdout[:500]}") from exc
    result = response.get("result")
    if not isinstance(result, dict):
        raise RuntimeError(f"MCP response missing result: {response}")
    structured = result.get("structuredContent")
    if not isinstance(structured, dict):
        raise RuntimeError(f"MCP response missing structuredContent: {response}")
    return structured


def educational_report(ticker: str, valuation: dict[str, Any], recalc: dict[str, Any] | None = None) -> str:
    if valuation.get("ok") is not True:
        failure = valuation
        return "\n".join(
            [
                "This report is for educational use only and is not financial advice.",
                f"Ticker: {ticker}",
                "Valuation did not complete.",
                f"Failure category: {failure.get('failureCategory')}",
                f"Plain-language explanation: {(failure.get('error') or {}).get('message')}",
                f"Next diagnostic action: {(failure.get('recovery') or {}).get('agentAction')}",
            ]
        )

    dcf = valuation.get("dcf") or {}
    assumptions = valuation.get("assumptions") or {}
    growth = assumptions.get("growth") or {}
    margin = assumptions.get("margin") or {}
    capital = assumptions.get("salesToCapital") or {}
    discount = assumptions.get("costOfCapital") or {}
    anchor = valuation.get("growthAnchor") or {}

    lines = [
        "This report is for educational use only and is not financial advice.",
        f"Ticker: {ticker}",
        f"Company: {dcf.get('companyName')}",
        f"Currency: {dcf.get('currency') or valuation.get('valuation', {}).get('currency')}",
        f"Market price: {dcf.get('marketPrice')}",
        f"Model intrinsic value per share: {dcf.get('estimatedValuePerShare')}",
        f"Revenue growth assumption: {growth.get('revenueGrowthRateYears2To5')}",
        f"Target operating margin assumption: {margin.get('targetOperatingMargin')}",
        f"Sales-to-capital assumption: {capital.get('years1To5')}",
        f"Initial cost of capital: {discount.get('initialCostOfCapital')}",
        f"Terminal growth: {(assumptions.get('terminalGrowth') or {}).get('rate')}",
        f"Growth anchor: {anchor.get('mappedEntityDisplay') or anchor.get('mappedEntity')}",
        "The model output depends on the assumptions above and should be read as a scenario.",
    ]

    if recalc:
        if recalc.get("ok") is True:
            recalc_dcf = recalc.get("dcf") or {}
            lines.extend(
                [
                    "Recalculated scenario:",
                    f"Changed assumptions: {json.dumps((recalc.get('assumptions') or {}).get('requested'), sort_keys=True)}",
                    f"Scenario model intrinsic value per share: {recalc_dcf.get('estimatedValuePerShare')}",
                ]
            )
        else:
            lines.extend(
                [
                    "Recalculated scenario did not complete.",
                    f"Scenario failure category: {recalc.get('failureCategory')}",
                    f"Scenario explanation: {(recalc.get('error') or {}).get('message')}",
                ]
            )

    return "\n".join(lines)


def scan_report(text: str) -> list[str]:
    hits = []
    for pattern in PROHIBITED_PATTERNS:
        if pattern.search(text):
            hits.append(pattern.pattern)
    return hits


def write_outputs(output_dir: Path, reports: dict[str, str], summary: dict[str, Any]) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    reports_dir = output_dir / "reports"
    reports_dir.mkdir(exist_ok=True)
    for ticker, text in reports.items():
        safe_name = ticker.replace(".", "_").replace("-", "_")
        (reports_dir / f"{safe_name}.md").write_text(text + "\n", encoding="utf-8")
    (output_dir / "qa-summary.json").write_text(json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> int:
    args = parse_args()
    tickers = [ticker.strip().upper() for ticker in args.tickers.split(",") if ticker.strip()]
    scenarios = [ticker.strip().upper() for ticker in args.scenarios.split(",") if ticker.strip()]
    output_dir = args.output_dir or Path(tempfile.mkdtemp(prefix="stockvaluation-agent-native-qa-"))

    health = call_tool(args.python, "stockvaluation.health", {}, args.timeout_seconds)
    if health.get("ok") is not True:
        print(json.dumps({"health": health}, indent=2, sort_keys=True))
        return 1

    valuations: dict[str, dict[str, Any]] = {}
    recalculations: dict[str, dict[str, Any]] = {}
    reports: dict[str, str] = {}
    failures: dict[str, dict[str, Any]] = {}
    scan_hits: dict[str, list[str]] = {}

    for ticker in tickers:
        valuation = call_tool(args.python, "stockvaluation.value_ticker", {"ticker": ticker}, args.timeout_seconds)
        if valuation.get("ok") is not True:
            explanation = call_tool(args.python, "stockvaluation.explain_failure", {"error": valuation}, args.timeout_seconds)
            merged = {**valuation, "explanation": explanation}
            valuations[ticker] = merged
            failures[ticker] = {
                "category": valuation.get("failureCategory"),
                "message": (valuation.get("error") or {}).get("message"),
                "explanation": explanation.get("message"),
            }
            reports[ticker] = educational_report(ticker, merged)
        else:
            valuations[ticker] = valuation
            reports[ticker] = educational_report(ticker, valuation)

    for ticker in scenarios:
        recalc = call_tool(
            args.python,
            "stockvaluation.recalculate",
            {"ticker": ticker, "overrides": SCENARIO_OVERRIDES},
            args.timeout_seconds,
        )
        recalculations[ticker] = recalc
        reports[ticker] = educational_report(ticker, valuations.get(ticker, {}), recalc)

    for ticker, report in reports.items():
        hits = scan_report(report)
        if hits:
            scan_hits[ticker] = hits

    summary = {
        "health": {
            "ok": health.get("ok"),
            "service": health.get("service"),
        },
        "tickers": {
            ticker: {
                "ok": payload.get("ok"),
                "failureCategory": payload.get("failureCategory"),
                "company": ((payload.get("dcf") or {}).get("companyName")),
                "hasDcf": (payload.get("dcf") or {}).get("estimatedValuePerShare") is not None,
            }
            for ticker, payload in valuations.items()
        },
        "recalculations": {
            ticker: {
                "ok": payload.get("ok"),
                "failureCategory": payload.get("failureCategory"),
                "requested": (payload.get("assumptions") or {}).get("requested"),
                "mapped": (payload.get("assumptions") or {}).get("mapped"),
                "unsupported": (payload.get("assumptions") or {}).get("unsupported"),
                "hasDcf": (payload.get("dcf") or {}).get("estimatedValuePerShare") is not None,
            }
            for ticker, payload in recalculations.items()
        },
        "failures": failures,
        "reportScan": {
            "prohibitedLanguageFound": bool(scan_hits),
            "hits": scan_hits,
        },
        "outputDir": str(output_dir),
    }

    write_outputs(output_dir, reports, summary)
    print(json.dumps(summary, indent=2, sort_keys=True))
    return 1 if scan_hits else 0


if __name__ == "__main__":
    raise SystemExit(main())
