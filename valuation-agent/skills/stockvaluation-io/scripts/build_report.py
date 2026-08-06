#!/usr/bin/env python3
"""Assemble the StockValuation.io report from run data plus model prose.

Code owns the structure: section order, data-populated tables, the single
no-advice line, and the compact Audit block. The model only writes the named
narrative prose fields. Sections without underlying data are omitted
entirely — the builder never writes "Unavailable" filler. The prose linter
runs before rendering and the builder refuses to render on error findings.

Input: one JSON file (or stdin) with:
  company, ticker, title?, currency?
  valuation: {point: {value_per_share, label?}} or
             {range: {low, high, unresolved_drivers: [...]}}
  prose: {investment_thesis, framing_questions, valuation_thesis,
          business_story, growth, profitability, reinvestment, risk,
          terminal_value, sensitivity_takeaway, what_would_change_the_view,
          bottom_line}
  market_implied_diagnostics?: {rows: [{assumption, required_value, note?}]}
  key_assumptions?: [{driver, value, unit?, source}]  # source: anchor:<label>|user_input|service|segments:anchor:<label>|segments:user_input
  guided_judgment?: [{question, driver, answer, source?}]
  data_limits?: [string]
  sources?: [{title, url?, date?}]
  audit?: {workflow_state?, evidence_review?, guided_refinement?, source_class?,
           skill_version?, service_version?, mcp_version?, run_id?}
"""

from __future__ import annotations

import argparse
import datetime as dt
import importlib.util
import json
import os
import re
import sys
import webbrowser
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent

NO_ADVICE_LINE = (
    "Educational analysis only. This is not financial advice."
)

OPENING_PROSE_SECTIONS = (
    ("investment_thesis", "Investment Thesis"),
)

PROSE_SECTIONS = (
    ("business_story", "Business Story"),
    ("growth", "Growth"),
    ("profitability", "Profitability"),
    ("reinvestment", "Reinvestment"),
    ("risk", "Risk"),
)


def _load_module(name: str):
    spec = importlib.util.spec_from_file_location(name, SCRIPT_DIR / f"{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _text(value) -> str:
    return value.strip() if isinstance(value, str) else ""


def _dict(value) -> dict:
    return value if isinstance(value, dict) else {}


def _list(value) -> list:
    return value if isinstance(value, list) else []


def _number(value) -> float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    return None


def _currency(data: dict) -> str:
    return _text(data.get("currency")) or _text(_valuation_output(data).get("currency")) or "USD"


def _currency_symbol(currency: str) -> str:
    return "$" if _text(currency).upper() == "USD" else ""


def _with_currency_symbol(number_text: str, currency: str) -> str:
    symbol = _currency_symbol(currency)
    if symbol:
        if number_text.startswith("-"):
            return f"-{symbol}{number_text[1:]}"
        return f"{symbol}{number_text}"
    return f"{number_text} {currency}".strip()


def _trimmed(number: float, decimals: int) -> str:
    return f"{number:,.{decimals}f}".rstrip("0").rstrip(".")


def _fmt(value, suffix: str = "", decimals: int = 2) -> str:
    number = _number(value)
    if number is None:
        return ""
    formatted = f"{number:,.{decimals}f}"
    return f"{formatted}{suffix}" if suffix else formatted


def _fmt_compact_number(value, suffix: str = "") -> str:
    number = _number(value)
    if number is None:
        return ""
    sign = "-" if number < 0 else ""
    absolute = abs(number)
    for threshold, label, decimals in (
        (1_000_000_000_000, "T", 2),
        (1_000_000_000, "B", 1),
        (1_000_000, "M", 1),
    ):
        if absolute >= threshold:
            return f"{sign}{_trimmed(absolute / threshold, decimals)}{label}{suffix}"
    return f"{number:,.2f}{suffix}"


def _fmt_money(value, currency: str = "USD", compact: bool = True) -> str:
    number = _number(value)
    if number is None:
        return ""
    if compact:
        formatted = _fmt_compact_number(number)
    else:
        formatted = f"{number:,.2f}"
    return _with_currency_symbol(formatted, currency)


def _fmt_per_share(value, currency: str = "USD") -> str:
    number = _number(value)
    if number is None:
        return ""
    formatted = f"{number:,.2f}"
    return _with_currency_symbol(formatted, currency)


def _fmt_percent(value, decimals: int = 1) -> str:
    number = _number(value)
    if number is None:
        return ""
    return f"{number:,.{decimals}f}%"


def _fmt_multiple(value) -> str:
    number = _number(value)
    if number is None:
        return ""
    return f"{number:,.2f}x"



def _fmt_growth_ratio(value) -> str:
    """Yahoo revenueGrowth is a ratio (0.177 = 17.7%); render as percent."""
    number = _number(value)
    if number is None:
        return "—"
    return f"{number * 100:,.1f}%"

def _fmt_pe(value) -> str:
    """P/E multiples from Yahoo are already ratio units (e.g. 27.6)."""
    number = _number(value)
    if number is None:
        return "—"
    return f"{number:,.1f}x"


def _fmt_count(value) -> str:
    return _fmt_compact_number(value)


def _fmt_unit(value, unit: str = "", currency: str = "USD") -> str:
    normalized = _text(unit).lower()
    if normalized in {"percent", "%"}:
        return _fmt_percent(value)
    if normalized in {"multiple", "x", "ratio"}:
        return _fmt_multiple(value)
    if normalized in {"money", "currency", "usd", "dollar", "dollars"}:
        return _fmt_money(value, currency)
    return _fmt(value)


def _first_present(*values):
    for value in values:
        if value not in (None, ""):
            return value
    return None


def _revealed_thesis(data: dict) -> dict:
    thesis = data.get("revealedThesis")
    if not isinstance(thesis, dict):
        thesis = data.get("revealed_thesis")
    if isinstance(thesis, dict) and thesis.get("schema_version") == "revealed_thesis.v1":
        return thesis
    return {}


def _render_value(value) -> str:
    if value in (None, ""):
        return ""
    if isinstance(value, (dict, list)):
        return json.dumps(value, sort_keys=True)
    return _text(str(value))


def _collect_structured_numbers(value, numbers: list[float], *, in_model_prose: bool = False) -> None:
    if isinstance(value, bool):
        return
    if isinstance(value, (int, float)):
        if in_model_prose:
            return
        if value == value:
            numbers.append(float(value))
        return
    if isinstance(value, str):
        if in_model_prose:
            return
        for token in _numeric_tokens(value):
            parsed = _token_number(token)
            if parsed is not None:
                numbers.append(parsed)
        return
    if isinstance(value, dict):
        for key, child in value.items():
            child_is_prose = in_model_prose or key in {"prose", "framing_questions"}
            _collect_structured_numbers(child, numbers, in_model_prose=child_is_prose)
        return
    if isinstance(value, list):
        for child in value:
            _collect_structured_numbers(child, numbers, in_model_prose=in_model_prose)


def _model_prose_items(data: dict) -> list[tuple[str, str]]:
    prose = _dict(data.get("prose"))
    items: list[tuple[str, str]] = []

    def append_text(prefix: str, value) -> None:
        if isinstance(value, str):
            text = _text(value)
            if text:
                items.append((prefix, text))
            return
        if isinstance(value, dict):
            for key, child in value.items():
                append_text(f"{prefix}.{key}", child)
            return
        if isinstance(value, list):
            for index, child in enumerate(value):
                append_text(f"{prefix}[{index}]", child)

    for key, value in prose.items():
        append_text(f"prose.{key}", value)
    questions = data.get("framing_questions")
    append_text("framing_questions", questions)
    return items


def _remove_allowed_time_references(text: str) -> str:
    patterns = (
        r"\bQ[1-4]\b",
        r"\b[Ff][Yy]\s?\d{2,4}\b",
        r"\b[Yy]ears?\s+\d+\s*[-/]\s*\d+\b",
        r"\b[Yy]ear\s+\d+\b",
        r"\b\d+\s*[- ]year\b",
    )
    cleaned = text
    for pattern in patterns:
        cleaned = re.sub(pattern, "", cleaned)
    return cleaned


def _numeric_tokens(text: str) -> list[str]:
    cleaned = _remove_allowed_time_references(text)
    token_re = re.compile(
        r"(?<![A-Za-z0-9])\$?-?\d+(?:,\d{3})*(?:\.\d+)?\s*(?:%|x|T|B|M|K|trillion|billion|million)?",
        re.IGNORECASE,
    )
    return [match.group(0).strip() for match in token_re.finditer(cleaned)]


def _token_number(token: str) -> float | None:
    clean = token.replace("$", "").replace(",", "").strip().lower()
    multiplier = 1.0
    for suffix, scale in (
        ("trillion", 1_000_000_000_000.0),
        ("billion", 1_000_000_000.0),
        ("million", 1_000_000.0),
        ("t", 1_000_000_000_000.0),
        ("b", 1_000_000_000.0),
        ("m", 1_000_000.0),
        ("k", 1_000.0),
        ("%", 1.0),
        ("x", 1.0),
    ):
        if clean.endswith(suffix):
            multiplier = scale
            clean = clean[: -len(suffix)].strip()
            break
    try:
        return float(clean) * multiplier
    except ValueError:
        return None


def validate_model_prose_numbers(data: dict) -> list[dict[str, str]]:
    numbers: list[float] = []
    data_without_prose = {
        key: value
        for key, value in data.items()
        if key not in {"prose", "framing_questions"}
    }
    _collect_structured_numbers(data_without_prose, numbers)
    if not numbers:
        return []
    errors: list[dict[str, str]] = []
    for field, text in _model_prose_items(data):
        for token in _numeric_tokens(text):
            parsed = _token_number(token)
            if parsed is None:
                continue
            if not any(abs(parsed - allowed) <= max(0.15, abs(allowed) * 0.005) for allowed in numbers):
                errors.append({"field": field, "number": token})
    return errors


SEGMENT_ECONOMIC_CLAIM_PATTERNS: tuple[tuple[str, str], ...] = (
    ("segment_mix", r"\b(segment mix|business mix|mix shift|mix-shift|higher-margin|lower-margin|margin profile)\b"),
    ("amazon_business_line", r"\b(aws|seller services|marketplace|subscription growth|international retail|cloud economics)\b"),
    ("microsoft_business_line", r"\b(azure|linkedin|office|windows|xbox|intelligent cloud)\b"),
    ("alphabet_business_line", r"\b(google cloud|youtube|search advertising|search ads|other bets)\b"),
    ("meta_business_line", r"\b(reality labs|family of apps|facebook|instagram|whatsapp)\b"),
    ("disney_business_line", r"\b(parks and experiences|direct-to-consumer|linear networks|studio entertainment|espn)\b"),
)


def _unsupported_driver_entries(data: dict) -> list[dict]:
    valuation = _valuation_output(data)
    transparency = _assumption_transparency(data)
    audit = _dict(data.get("audit"))
    entries: list[dict] = []
    for source in (data, valuation, transparency, audit):
        value = _list(_dict(source).get("unsupportedBaselineDrivers") or _dict(source).get("unsupported_baseline_drivers"))
        entries.extend([_dict(item) for item in value])
    return entries


def _segment_basis_insufficient(data: dict) -> bool:
    valuation = _valuation_output(data)
    transparency = _assumption_transparency(data)
    audit = _dict(data.get("audit"))
    for value in (
        data.get("baselineUseStatus"),
        data.get("baseline_use_status"),
        valuation.get("baselineUseStatus"),
        valuation.get("baseline_use_status"),
        transparency.get("baselineUseStatus"),
        transparency.get("baseline_use_status"),
        audit.get("baselineUseStatus"),
        audit.get("baseline_use_status"),
    ):
        if _text(value).lower() == "segment_evidence_insufficient":
            return True
    for entry in _unsupported_driver_entries(data):
        field = _text(entry.get("field")).lower()
        status = _text(entry.get("status") or entry.get("reason")).lower()
        if field == "segments" and "segment_evidence_insufficient" in status:
            return True
    segment_aware = _first_present(
        data.get("segmentAware"),
        data.get("segment_aware"),
        valuation.get("segmentAware"),
        valuation.get("segment_aware"),
        transparency.get("segmentAware"),
        transparency.get("segment_aware"),
        audit.get("segmentAware"),
        audit.get("segment_aware"),
    )
    segment_count = _first_present(
        data.get("segmentCount"),
        data.get("segment_count"),
        valuation.get("segmentCount"),
        valuation.get("segment_count"),
        transparency.get("segmentCount"),
        transparency.get("segment_count"),
        audit.get("segmentCount"),
        audit.get("segment_count"),
    )
    if segment_aware is False and _number(segment_count) == 0:
        return True
    return False


def validate_model_prose_basis(data: dict) -> list[dict[str, str]]:
    if not _segment_basis_insufficient(data):
        return []
    errors: list[dict[str, str]] = []
    for field, text in _model_prose_items(data):
        for label, pattern in SEGMENT_ECONOMIC_CLAIM_PATTERNS:
            if re.search(pattern, text, flags=re.IGNORECASE):
                errors.append(
                    {
                        "field": field,
                        "claim": label,
                        "basis": "segment_evidence_insufficient",
                    }
                )
                break
    return errors


def _table(headers: list[str], rows: list[list[str]]) -> list[str]:
    lines = ["| " + " | ".join(headers) + " |", "| " + " | ".join("---" for _ in headers) + " |"]
    for row in rows:
        lines.append("| " + " | ".join(str(cell) for cell in row) + " |")
    return lines


def _valuation_output(data: dict) -> dict:
    for key in ("valuation_output", "valuationOutput", "raw_valuation", "rawValuation", "valuation_json", "valuationJson"):
        value = data.get(key)
        if isinstance(value, dict):
            return value
    valuation = data.get("valuation")
    if isinstance(valuation, dict) and any(key in valuation for key in ("companyDTO", "financialDTO", "terminalValueDTO")):
        return valuation
    return {}


def _series(container: dict, key: str) -> list:
    value = container.get(key)
    return value if isinstance(value, list) else []


def _human_label(value: str) -> str:
    clean = " ".join(_text(value).replace("-", " ").replace("_", " ").split())
    return clean[:1].upper() + clean[1:] if clean else ""


def _assumption_transparency(data: dict) -> dict:
    return _dict(_valuation_output(data).get("assumptionTransparency"))


def _priced_in_expectations(data: dict) -> dict:
    return _dict(_assumption_transparency(data).get("pricedInExpectations"))


def _case_status_label(data: dict) -> str:
    valuation = _dict(data.get("valuation"))
    valuation_output = _valuation_output(data)
    transparency = _assumption_transparency(data)
    audit = _dict(data.get("audit"))
    workflow = _dict(audit.get("workflow_state"))
    for value in (
        data.get("case_status"),
        data.get("caseStatus"),
        data.get("valuation_case_status"),
        data.get("valuationCaseStatus"),
        valuation.get("case_status"),
        valuation.get("caseStatus"),
        valuation_output.get("caseStatus"),
        valuation_output.get("valuationCaseStatus"),
        transparency.get("valuationCaseStatus"),
        audit.get("case_status"),
        audit.get("caseStatus"),
        audit.get("final_case_type"),
        workflow.get("case_status"),
        workflow.get("caseStatus"),
    ):
        label = _human_label(str(value)) if value not in (None, "") else ""
        if label:
            return label
    if isinstance(valuation.get("range"), dict):
        return "Unresolved range"
    gates = _dict(workflow.get("gates"))
    guided = _dict(gates.get("guided_refinement"))
    guided_status = " ".join(
        _text(guided.get(key)).lower()
        for key in ("status", "outcome")
        if _text(guided.get(key))
    )
    if any(token in guided_status for token in ("applied", "complete", "cleared")):
        return "User refined scenario"
    evidence = _dict(gates.get("evidence_review"))
    evidence_status = " ".join(
        _text(evidence.get(key)).lower()
        for key in ("status", "outcome")
        if _text(evidence.get(key))
    )
    if any(token in evidence_status for token in ("approved", "accepted", "cleared", "bypassed")):
        return "Evidence constrained base"
    return "Diagnostic baseline"


def _market_price_value(data: dict) -> float | None:
    valuation = _valuation_output(data)
    company = _dict(valuation.get("companyDTO"))
    priced_in = _priced_in_expectations(data)
    for value in (
        data.get("market_price"),
        data.get("marketPrice"),
        company.get("price"),
        priced_in.get("marketPrice"),
    ):
        number = _number(value)
        if number is not None:
            return number
    return None


def _valuation_view(data: dict) -> list[str]:
    valuation = data.get("valuation") or {}
    currency = _currency(data)
    point = valuation.get("point") if isinstance(valuation, dict) else None
    value_range = valuation.get("range") if isinstance(valuation, dict) else None
    lines: list[str] = [f"Case status: **{_case_status_label(data)}**."]
    if isinstance(point, dict) and isinstance(point.get("value_per_share"), (int, float)):
        label = _text(point.get("label")) or "Estimated value per share"
        lines.append(f"{label}: **{_fmt_per_share(point['value_per_share'], currency)}**.")
    elif isinstance(value_range, dict):
        low = value_range.get("low")
        high = value_range.get("high")
        if isinstance(low, (int, float)) and isinstance(high, (int, float)):
            lines.append(
                "Value range: "
                f"**{_fmt_per_share(min(low, high), currency)}-{_fmt_per_share(max(low, high), currency)}** per share."
            )
            drivers = [str(d) for d in value_range.get("unresolved_drivers") or [] if d]
            if drivers:
                lines.append(
                    "The spread comes from unresolved driver(s): "
                    + ", ".join(driver.replace("_", " ") for driver in drivers)
                    + ". A single point estimate appears only when every material driver is pinned."
                )
    market_price = _market_price_value(data)
    if market_price is not None:
        lines.append(f"Market price: **{_fmt_per_share(market_price, currency)}**.")
    return lines


def _prose_text_section(prose: dict, key: str, heading: str) -> list[str]:
    body = _text(prose.get(key))
    return [f"## {heading}", "", body] if body else []


def _revealed_investment_thesis_section(data: dict) -> list[str]:
    thesis = _revealed_thesis(data)
    body = _text(thesis.get("investment_thesis"))
    return ["## Investment Thesis", "", body] if body else []


def _framing_questions_section(data: dict, prose: dict) -> list[str]:
    revealed = _revealed_thesis(data)
    if revealed:
        rows = []
        for item in _list(revealed.get("framing_questions")):
            if not isinstance(item, dict):
                continue
            question = _text(item.get("question"))
            if not question:
                continue
            driver = _text(item.get("driver"))
            detail = question
            if driver:
                detail = f"{detail} ({driver.replace('_', ' ')})"
            rows.append(f"{len(rows) + 1}. {detail}")
        return ["## Framing Questions", ""] + rows if rows else []
    questions = prose.get("framing_questions")
    if questions in (None, ""):
        questions = data.get("framing_questions")
    if isinstance(questions, str):
        body = _text(questions)
        return ["## Framing Questions", "", body] if body else []
    rows: list[str] = []
    if isinstance(questions, list):
        for index, item in enumerate(questions, start=1):
            if isinstance(item, dict):
                question = _text(item.get("question"))
                if not question:
                    continue
                driver = _text(item.get("driver"))
                context = _text(item.get("context") or item.get("why_it_matters") or item.get("why"))
                detail = question
                if driver:
                    detail = f"{detail} ({driver.replace('_', ' ')})"
                if context:
                    detail = f"{detail} — {context}"
                rows.append(f"{index}. {detail}")
            else:
                question = _text(str(item))
                if question:
                    rows.append(f"{index}. {question}")
    if not rows:
        return []
    return ["## Framing Questions", ""] + rows


def _revealed_thesis_trail_section(data: dict) -> list[str]:
    revealed = _revealed_thesis(data)
    if not revealed:
        return []
    rows = []
    for decision in _list(revealed.get("decisions")):
        if not isinstance(decision, dict):
            continue
        question = _text(decision.get("framing_question"))
        interpretation = _text(decision.get("selected_interpretation"))
        if not question or not interpretation:
            continue
        mapping = _dict(decision.get("mapped_effect"))
        mapping_bits = [
            _text(decision.get("model_action")),
            _text(mapping.get("status")),
            _text(mapping.get("field")),
            _render_value(mapping.get("value")),
            _text(mapping.get("source")),
        ]
        evidence_bits = []
        refs = _string_list(decision.get("supporting_evidence_refs"))
        if refs:
            evidence_bits.append("support: " + ", ".join(refs))
        opposing = _string_list(decision.get("opposing_evidence_refs"))
        if opposing:
            evidence_bits.append("opposes: " + ", ".join(opposing))
        gaps = _string_list(decision.get("evidence_gaps"))
        if gaps:
            evidence_bits.append("gaps: " + "; ".join(gaps))
        caveats = _string_list(decision.get("coherence_caveats"))
        if caveats:
            evidence_bits.append("caveats: " + "; ".join(caveats))
        rows.append(
            [
                question,
                interpretation,
                "; ".join(bit for bit in mapping_bits if bit),
                "; ".join(evidence_bits),
            ]
        )
    if not rows:
        return []
    return ["## Revealed Thesis Trail", ""] + _table(
        ["Question", "Selected interpretation", "Driver mapping", "Evidence and caveats"],
        rows,
    )


def _market_implied_section(data: dict) -> list[str]:
    diagnostics = data.get("market_implied_diagnostics")
    rows = diagnostics.get("rows") if isinstance(diagnostics, dict) else None
    table_rows = []
    for row in rows or []:
        if not isinstance(row, dict):
            continue
        assumption = _text(row.get("assumption"))
        required = row.get("required_value")
        if not assumption or required in (None, ""):
            continue
        table_rows.append([assumption, required, _text(row.get("note"))])
    if not table_rows:
        service_section = _service_market_implied_section(data)
        return service_section if service_section else _priced_in_pressure_section(data)
    return ["## What The Price Would Need", ""] + _table(["Assumption", "Required value", "Note"], table_rows)


def _service_market_implied_section(data: dict) -> list[str]:
    transparency = _assumption_transparency(data)
    diagnostics = _dict(transparency.get("marketImpliedExpectations"))
    currency = _currency(data)
    rows = []
    for metric in _list(diagnostics.get("metrics")):
        if not isinstance(metric, dict):
            continue
        label = _text(metric.get("label") or metric.get("key"))
        if not label:
            continue
        unit = _text(metric.get("unit"))
        rows.append(
            [
                label,
                _fmt_unit(metric.get("modelValue"), unit, currency),
                _fmt_unit(metric.get("impliedValue"), unit, currency),
                _fmt_unit(metric.get("gap"), unit, currency),
                _text(metric.get("note")),
            ]
        )
    if not rows:
        return []
    return ["## What The Price Would Need", ""] + _table(
        ["Driver", "Model", "Market-implied", "Gap", "Note"],
        rows,
    )


def _priced_in_pressure_section(data: dict) -> list[str]:
    priced_in = _priced_in_expectations(data)
    if not priced_in:
        return []
    currency = _currency(data)
    base_case = _dict(priced_in.get("baseCase"))
    base_value = _fmt_per_share(base_case.get("intrinsicValue"), currency)
    market_price = _fmt_per_share(priced_in.get("marketPrice"), currency)
    if not base_value and not market_price:
        return []
    lines = ["## What The Price Would Need", ""]
    if base_value and market_price:
        detail = f"The base case value is {base_value} versus market price {market_price}."
        gap = _fmt_per_share(base_case.get("gapToMarket"), currency)
        gap_pct = _fmt_percent(base_case.get("gapToMarketPct"))
        if gap and gap_pct:
            detail += f" The returned price gap is {gap} ({gap_pct})."
        lines.extend([detail, ""])
    rows = []
    for row in _list(priced_in.get("frontier")):
        if not isinstance(row, dict):
            continue
        margin = _fmt_percent(row.get("operatingMargin"))
        growth = _fmt_percent(row.get("impliedRevenueGrowth"))
        value = _fmt_per_share(row.get("intrinsicValue"), currency)
        if not margin or not growth:
            continue
        status = "Solved" if row.get("solved") is True else "Nearest sampled point"
        rows.append([margin, growth, value, status, _text(row.get("note"))])
    if rows:
        lines.extend(_table(["Operating margin", "Implied growth", "Value/share", "Status", "Note"], rows))
    return lines


def _peer_multiples_section(data: dict) -> list[str]:
    """Render the peer comparable multiples table when peer rows are present.

    Input shape (produced by scripts/peer_multiples.py):
      peers: [{ticker, price, trailing_pe, forward_pe, ev_ebitda,
               revenue_growth, gross_margin, operating_margin, market_cap, error?}]
    Missing metrics render as "—" instead of dropping the row, so the table
    shape is stable even with a degraded quote.
    """
    rows = data.get("peers")
    if not isinstance(rows, list) or not rows:
        return []
    header = ["Ticker", "Price", "Trailing P/E", "Forward P/E", "EV/EBITDA", "Rev growth"]
    table_rows = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        ticker = _text(row.get("ticker")) or "?"
        if row.get("error"):
            table_rows.append([ticker, "n/a", "n/a", "n/a", "n/a", "n/a"])
            continue
        price = _fmt_per_share(row.get("price"), _currency(data))
        table_rows.append([
            ticker,
            price or "—",
            _fmt_pe(row.get("trailing_pe")),
            _fmt_pe(row.get("forward_pe")),
            _fmt_multiple(row.get("ev_ebitda")) or "—",
            _fmt_growth_ratio(row.get("revenue_growth")),
        ])
    if not table_rows:
        return []
    return ["## Peer Multiples", ""] + _table(header, table_rows)


def _service_key_drivers_section(data: dict) -> list[str]:
    if data.get("key_assumptions"):
        return []
    valuation = _valuation_output(data)
    transparency = _dict(valuation.get("assumptionTransparency"))
    operating = _dict(transparency.get("operatingAssumptions"))
    discount = _dict(transparency.get("discountRate"))
    terminal = _dict(valuation.get("terminalValueDTO"))
    currency = _currency(data)
    rows = []

    def add(label: str, value, unit: str = "", source: str = "", rationale: str = "") -> None:
        formatted = _fmt_unit(value, unit, currency)
        if formatted:
            rows.append([label, formatted, _text(source), _text(rationale)])

    add(
        "Revenue growth years 2-5",
        operating.get("revenueGrowthRateYears2To5"),
        "percent",
        operating.get("revenueGrowthSource"),
        operating.get("revenueGrowthRationale"),
    )
    add(
        "Operating margin next year",
        operating.get("operatingMarginNextYear"),
        "percent",
        operating.get("operatingMarginSource"),
        operating.get("operatingMarginRationale"),
    )
    add(
        "Target operating margin",
        operating.get("targetOperatingMargin"),
        "percent",
        operating.get("operatingMarginSource"),
        operating.get("operatingMarginRationale"),
    )
    add("Margin convergence year", operating.get("convergenceYearMargin"))
    add(
        "Sales-to-capital years 1-5",
        operating.get("salesToCapitalYears1To5"),
        "multiple",
        operating.get("salesToCapitalSource"),
        operating.get("salesToCapitalRationale"),
    )
    add(
        "Sales-to-capital years 6-10",
        operating.get("salesToCapitalYears6To10"),
        "multiple",
        operating.get("salesToCapitalSource"),
        operating.get("salesToCapitalRationale"),
    )
    add(
        "Initial cost of capital",
        discount.get("initialCostOfCapital"),
        "percent",
        discount.get("initialCostOfCapitalSource"),
    )
    add(
        "Terminal cost of capital",
        _first_present(terminal.get("costOfCapital"), discount.get("terminalCostOfCapital")),
        "percent",
        discount.get("equityRiskPremiumSource"),
        discount.get("costOfCapitalFormula"),
    )
    add("Terminal growth", terminal.get("growthRate"), "percent")
    add("Terminal return on capital", terminal.get("returnOnCapital"), "percent")
    if not rows:
        return []
    has_detail = any(row[2] or row[3] for row in rows)
    if has_detail:
        return ["## Model Driver Snapshot", ""] + _table(["Driver", "Value", "Source", "Rationale"], rows)
    return ["## Model Driver Snapshot", ""] + _table(["Driver", "Value"], [row[:2] for row in rows])


def _priced_in_expectations_section(data: dict) -> list[str]:
    priced_in = _priced_in_expectations(data)
    base_case = _dict(priced_in.get("baseCase"))
    frontier = _list(priced_in.get("frontier"))
    currency = _currency(data)
    lines: list[str] = []
    base_value = _fmt_per_share(base_case.get("intrinsicValue"), currency)
    market_price = _fmt_per_share(priced_in.get("marketPrice"), currency)
    if base_value and market_price:
        gap = _fmt_per_share(base_case.get("gapToMarket"), currency)
        gap_pct = _fmt_percent(base_case.get("gapToMarketPct"))
        detail = f"Base case value is {base_value} versus market price {market_price}."
        if gap and gap_pct:
            detail += f" Gap to market is {gap} ({gap_pct})."
        lines.extend(["## Priced-In Expectations", "", detail, ""])
    rows = []
    for row in frontier:
        if not isinstance(row, dict):
            continue
        margin = _fmt_percent(row.get("operatingMargin"))
        implied_growth = _fmt_percent(row.get("impliedRevenueGrowth"))
        value = _fmt_per_share(row.get("intrinsicValue"), currency)
        if not margin or not implied_growth:
            continue
        status = "Solved" if row.get("solved") is True else "Nearest sampled point"
        rows.append([margin, implied_growth, value, status, _text(row.get("note"))])
    if rows:
        if not lines:
            lines.extend(["## Priced-In Expectations", ""])
        lines.extend(_table(["Operating margin", "Implied growth", "Value/share", "Status", "Note"], rows))
    return lines


def _sensitivity_grid_section(data: dict) -> list[str]:
    priced_in = _priced_in_expectations(data)
    grid = [row for row in _list(priced_in.get("grid")) if isinstance(row, dict)]
    if not grid:
        return []

    margin_values = sorted({
        _number(row.get("operatingMargin"))
        for row in grid
        if _number(row.get("operatingMargin")) is not None
    })
    growth_values = sorted({
        _number(row.get("revenueGrowth"))
        for row in grid
        if _number(row.get("revenueGrowth")) is not None
    })
    if not margin_values or not growth_values:
        return []

    currency = _currency(data)
    value_by_axis: dict[tuple[float, float], str] = {}
    for row in grid:
        margin = _number(row.get("operatingMargin"))
        growth = _number(row.get("revenueGrowth"))
        value = _fmt_per_share(row.get("intrinsicValue"), currency)
        if margin is None or growth is None or not value:
            continue
        value_by_axis[(round(margin, 4), round(growth, 4))] = value

    table_rows: list[list[str]] = []
    for margin in margin_values:
        cells = [_fmt_percent(margin)]
        for growth in growth_values:
            cells.append(value_by_axis.get((round(margin, 4), round(growth, 4)), ""))
        table_rows.append(cells)
    if not table_rows:
        return []

    headers = ["Operating margin \\ Revenue growth"] + [_fmt_percent(value) for value in growth_values]
    lines = ["## Sensitivity Analysis", ""]
    method = _text(priced_in.get("method"))
    if method:
        lines.extend([method, ""])
    lines.extend(_table(headers, table_rows))
    prose = _dict(data.get("prose"))
    takeaway = _text(prose.get("sensitivity_takeaway"))
    if takeaway:
        lines.extend(["", takeaway])
    return lines


def _key_assumptions_section(data: dict) -> list[str]:
    rows = []
    has_source_detail = False
    currency = _currency(data)
    for item in data.get("key_assumptions") or []:
        if not isinstance(item, dict):
            continue
        driver = _text(item.get("driver"))
        value = item.get("value")
        source = _text(item.get("source"))
        if not driver or value in (None, "") or not source:
            continue
        unit = _text(item.get("unit"))
        source_detail = _anchor_explanation_text(item)
        has_source_detail = has_source_detail or bool(source_detail)
        formatted = _fmt_unit(value, unit, currency) or f"{value} {unit}".strip()
        rows.append([driver.replace("_", " "), formatted, source, source_detail])
    if not rows:
        return []
    if has_source_detail:
        return ["## Key Assumptions", ""] + _table(["Driver", "Value", "Source", "Source detail"], rows)
    return ["## Key Assumptions", ""] + _table(["Driver", "Value", "Source"], [row[:3] for row in rows])


def _projection_walk_section(data: dict) -> list[str]:
    valuation = _valuation_output(data)
    financial = _dict(valuation.get("financialDTO"))
    revenues = _series(financial, "revenues")
    if not revenues:
        return []
    currency = _currency(data)
    revenue_growth = _series(financial, "revenueGrowthRate")
    margins = _series(financial, "ebitOperatingMargin")
    ebit = _series(financial, "ebitOperatingIncome")
    fcff = _series(financial, "fcff")
    pv_fcff = _series(financial, "pvFcff")
    projection_years = int(_number(valuation.get("projectionYears")) or max(0, len(revenues) - 2))
    max_year = min(projection_years, len(revenues) - 2, 10)
    rows: list[list[str]] = []
    for year in range(0, max_year + 1):
        rows.append(
            [
                "Base" if year == 0 else f"Year {year}",
                _fmt_money(_first_present(revenues[year] if year < len(revenues) else None), currency),
                _fmt_percent(revenue_growth[year] if year < len(revenue_growth) else None),
                _fmt_percent(margins[year] if year < len(margins) else None),
                _fmt_money(ebit[year] if year < len(ebit) else None, currency),
                _fmt_money(fcff[year] if year < len(fcff) else None, currency),
                _fmt_money(pv_fcff[year] if year < len(pv_fcff) else None, currency),
            ]
        )
    if len(rows) <= 1:
        return []
    return ["## Projection Walk", ""] + _table(
        ["Year", "Revenue", "Growth", "EBIT margin", "EBIT", "FCFF", "PV FCFF"],
        rows,
    )


def _valuation_bridge_section(data: dict) -> list[str]:
    valuation = _valuation_output(data)
    company = _dict(valuation.get("companyDTO"))
    rows = []
    currency = _currency(data)
    for label, value, kind in (
        ("PV explicit cash flows", company.get("pvCFOverNext10Years"), "money"),
        ("PV terminal value", company.get("pvTerminalValue"), "money"),
        ("Operating asset value", company.get("valueOfOperatingAssets"), "money"),
        ("Cash", company.get("cash"), "money"),
        ("Non-operating assets", company.get("nonOperatingAssets"), "money"),
        ("Debt", company.get("debt"), "money"),
        ("Minority interests", company.get("minorityInterests"), "money"),
        ("Equity value", company.get("valueOfEquity"), "money"),
        ("Option value", company.get("valueOfOptions"), "money"),
        ("Common equity value", company.get("valueOfEquityInCommonStock"), "money"),
        ("Shares", company.get("numberOfShares"), "count"),
        ("Estimated value per share", company.get("estimatedValuePerShare"), "per_share"),
        ("Market price", company.get("price"), "per_share"),
        ("Price as percent of value", company.get("priceAsPercentageOfValue"), "percent"),
    ):
        if kind == "money":
            formatted = _fmt_money(value, currency)
        elif kind == "per_share":
            formatted = _fmt_per_share(value, currency)
        elif kind == "percent":
            formatted = _fmt_percent(value)
        elif kind == "count":
            formatted = _fmt_count(value)
        else:
            formatted = _fmt(value)
        if formatted:
            rows.append([label, formatted])
    if not rows:
        return []
    return ["## Valuation Bridge", ""] + _table(["Item", "Value"], rows)


def _terminal_value_section(data: dict) -> list[str]:
    valuation = _valuation_output(data)
    terminal = _dict(valuation.get("terminalValueDTO"))
    company = _dict(valuation.get("companyDTO"))
    currency = _currency(data)
    rows = []
    for label, value, kind in (
        ("Terminal growth", terminal.get("growthRate"), "percent"),
        ("Terminal cost of capital", terminal.get("costOfCapital"), "percent"),
        ("Terminal return on capital", terminal.get("returnOnCapital"), "percent"),
        ("Terminal reinvestment rate", terminal.get("reinvestmentRate"), "percent"),
        ("Terminal cash flow", company.get("terminalCashFlow"), "money"),
        ("Terminal value", company.get("terminalValue"), "money"),
        ("PV terminal value", company.get("pvTerminalValue"), "money"),
    ):
        formatted = _fmt_percent(value) if kind == "percent" else _fmt_money(value, currency)
        if formatted:
            rows.append([label, formatted])
    pv_terminal = _number(company.get("pvTerminalValue"))
    operating_assets = _number(company.get("valueOfOperatingAssets"))
    if pv_terminal is not None and operating_assets and operating_assets != 0:
        rows.append(["PV terminal share of operating assets", _fmt_percent(pv_terminal / operating_assets * 100.0)])
    if not rows:
        return []
    return ["## Terminal Value", ""] + _table(["Driver", "Value"], rows)


def _anchor_explanation_text(item: dict) -> str:
    explanation = item.get("anchor_explanation") or item.get("anchorExplanation")
    if not isinstance(explanation, dict):
        return _text(item.get("source_detail") or item.get("sourceDetail"))
    parts: list[str] = []
    summary = _text(explanation.get("summary"))
    if summary:
        parts.append(summary)
    weighted = explanation.get("weighted_anchors") or explanation.get("weightedAnchors")
    if isinstance(weighted, dict):
        anchors = []
        for label in ("low", "base", "high"):
            value = weighted.get(label)
            if value not in (None, ""):
                anchors.append(f"{label} {value}")
        if anchors:
            parts.append("Weighted anchors: " + ", ".join(anchors))
    for row in explanation.get("segment_rows") or explanation.get("segmentRows") or []:
        if not isinstance(row, dict):
            continue
        segment = _text(row.get("segment"))
        industry = _text(row.get("industry_group") or row.get("industryGroup"))
        if not segment or not industry:
            continue
        weight_text = _anchor_weight_text(row)
        values = [
            str(row.get(label))
            for label in ("low", "base", "high")
            if row.get(label) not in (None, "")
        ]
        value_text = f", low/base/high {'/'.join(values)}" if values else ""
        parts.append(f"{segment} -> {industry}{weight_text}{value_text}")
    warnings = [_text(item) for item in explanation.get("warnings") or [] if _text(item)]
    if warnings:
        parts.append("Warnings: " + "; ".join(warnings))
    return "; ".join(parts)


def _anchor_weight_text(row: dict) -> str:
    filing = row.get("filing_weight_pct")
    effective = row.get("effective_anchor_weight_pct")
    if filing in (None, ""):
        filing = row.get("revenue_weight_pct")
    try:
        filing_number = float(filing)
    except (TypeError, ValueError):
        filing_number = None
    try:
        effective_number = float(effective)
    except (TypeError, ValueError):
        effective_number = None
    if filing_number is None and effective_number is None:
        return ""
    if effective_number is None or filing_number == effective_number:
        return f", filing weight {filing_number:g}%" if filing_number is not None else ""
    if filing_number is None:
        return f", effective anchor weight {effective_number:g}%"
    return f", filing weight {filing_number:g}%, effective anchor weight {effective_number:g}%"


def _guided_judgment_section(data: dict) -> list[str]:
    rows = []
    for item in data.get("guided_judgment") or []:
        if not isinstance(item, dict):
            continue
        question = _text(item.get("question"))
        answer = _text(str(item.get("answer") or ""))
        if not question or not answer:
            continue
        rows.append([question, _text(item.get("driver")).replace("_", " "), answer, _text(item.get("source"))])
    if not rows:
        return []
    return ["## Guided Judgment", ""] + _table(["Question", "Driver", "Answer", "Source"], rows)


def _data_limits_section(data: dict) -> list[str]:
    limits = [_text(item) for item in data.get("data_limits") or [] if _text(item)]
    if not limits:
        return []
    return ["## Data Limits", ""] + [f"- {item}" for item in limits]


def _string_list(value) -> list[str]:
    if isinstance(value, list):
        return [_text(str(item)) for item in value if _text(str(item))]
    text = _text(str(value)) if value not in (None, "") else ""
    return [text] if text else []


def _basis_warning_items(data: dict) -> list[str]:
    warnings: list[str] = []

    def extend(value) -> None:
        warnings.extend(_string_list(value))

    valuation = _valuation_output(data)
    transparency = _assumption_transparency(data)
    audit = _dict(data.get("audit"))
    source_gate = _dict(data.get("sourceQualityGate") or data.get("source_quality_gate"))
    if not source_gate:
        source_gate = _dict(valuation.get("sourceQualityGate") or transparency.get("sourceQualityGate"))

    for key in (
        "weak_basis_warnings",
        "weakBasisWarnings",
        "valuation_basis_warnings",
        "valuationBasisWarnings",
        "basis_warnings",
        "warnings",
    ):
        extend(data.get(key))
        extend(valuation.get(key))
        extend(audit.get(key))
    extend(transparency.get("baselineWarnings"))
    extend(valuation.get("baselineWarnings"))
    extend(source_gate.get("warnings"))
    extend(source_gate.get("dataQualityWarnings") or source_gate.get("data_quality_warnings"))
    gate_status = _text(source_gate.get("status") or source_gate.get("sourceQualityGateStatus"))
    gate_reason = _text(source_gate.get("reason") or source_gate.get("message"))
    if gate_status and gate_status.lower() not in {"pass", "passed", "ok", "clear", "cleared"} and gate_reason:
        warnings.append(gate_reason)

    seen: set[str] = set()
    deduped: list[str] = []
    for warning in warnings:
        clean = " ".join(warning.split())
        key = clean.lower()
        if clean and key not in seen:
            deduped.append(clean)
            seen.add(key)
    return deduped


def _basis_warnings_section(data: dict) -> list[str]:
    warnings = _basis_warning_items(data)
    if not warnings:
        return []
    return ["## Basis Warnings", ""] + [f"- {item}" for item in warnings]


def _what_would_change_section(data: dict, prose: dict) -> list[str]:
    revealed = _revealed_thesis(data)
    if revealed:
        rows = []
        for item in _list(revealed.get("falsifiers")):
            if not isinstance(item, dict):
                continue
            falsifier = _text(item.get("falsifier"))
            if not falsifier:
                continue
            driver = _text(item.get("driver")).replace("_", " ")
            rows.append(f"- {driver}: {falsifier}" if driver else f"- {falsifier}")
        return ["## What Would Change The View", ""] + rows if rows else []
    value = prose.get("what_would_change_the_view")
    if value in (None, ""):
        value = prose.get("metrics_to_monitor")
    if value in (None, ""):
        value = data.get("metrics_to_monitor") or data.get("what_would_change_the_view")
    if isinstance(value, str):
        body = _text(value)
        return ["## What Would Change The View", "", body] if body else []
    rows: list[str] = []
    if isinstance(value, list):
        for item in value:
            if isinstance(item, dict):
                metric = _text(item.get("metric") or item.get("event") or item.get("item") or item.get("question"))
                detail = _text(item.get("why") or item.get("detail") or item.get("rationale"))
                if metric and detail:
                    rows.append(f"- {metric}: {detail}")
                elif metric:
                    rows.append(f"- {metric}")
            else:
                text = _text(str(item))
                if text:
                    rows.append(f"- {text}")
    if not rows:
        return []
    return ["## What Would Change The View", ""] + rows


def _sources_section(data: dict) -> list[str]:
    entries = []
    for item in data.get("sources") or []:
        if not isinstance(item, dict):
            continue
        title = _text(item.get("title"))
        if not title:
            continue
        parts = [title]
        if _text(item.get("url")):
            parts.append(f"<{_text(item.get('url'))}>")
        if _text(item.get("date")):
            parts.append(f"({_text(item.get('date'))})")
        entries.append("- " + " ".join(parts))
    if not entries:
        return []
    return ["## Sources", ""] + entries


def _audit_section(data: dict) -> list[str]:
    audit = data.get("audit")
    if not isinstance(audit, dict):
        return []
    rows: list[list[str]] = []
    workflow_state = audit.get("workflow_state")
    if isinstance(workflow_state, dict):
        gates = workflow_state.get("gates")
        if isinstance(gates, dict):
            for gate, entry in sorted(gates.items()):
                if not isinstance(entry, dict):
                    continue
                status = _text(entry.get("status"))
                detail = _text(entry.get("outcome"))
                if _text(entry.get("reason")):
                    detail = f"{detail} ({_text(entry.get('reason'))})".strip()
                rows.append([f"Gate: {gate.replace('_', ' ')}", " ".join(part for part in [status, detail] if part)])
        if _text(workflow_state.get("run_id")):
            rows.append(["Run", _text(workflow_state.get("run_id"))])
    existing_labels = {row[0] for row in rows}
    for key, label in (
        ("evidence_review", "Evidence review"),
        ("guided_refinement", "Guided refinement"),
        ("source_class", "Source class"),
        ("skill_version", "Skill version"),
        ("service_version", "Service version"),
        ("mcp_version", "MCP version"),
        ("run_id", "Run"),
    ):
        value = _text(str(audit.get(key) or ""))
        if value and label not in existing_labels:
            rows.append([label, value])
            existing_labels.add(label)
    if not rows:
        return []
    return ["## Audit", ""] + _table(["Item", "Status"], rows)


def build_report_markdown(data: dict) -> str:
    company = _text(data.get("company")) or _text(data.get("ticker")) or "Valuation"
    title = _text(data.get("title")) or f"{company} Valuation Report"
    sections: list[list[str]] = []
    prose = data.get("prose") if isinstance(data.get("prose"), dict) else {}

    valuation_view = _valuation_view(data)
    if valuation_view:
        sections.append(["## Valuation View", ""] + valuation_view)

    revealed_section = _revealed_investment_thesis_section(data)
    if revealed_section:
        sections.append(revealed_section)
    else:
        for key, heading in OPENING_PROSE_SECTIONS:
            section = _prose_text_section(prose, key, heading)
            if section:
                sections.append(section)

    section = _framing_questions_section(data, prose)
    if section:
        sections.append(section)

    section = _revealed_thesis_trail_section(data)
    if section:
        sections.append(section)

    section = _prose_text_section(prose, "valuation_thesis", "Valuation Thesis")
    if section:
        sections.append(section)

    for key, heading in PROSE_SECTIONS:
        section = _prose_text_section(prose, key, heading)
        if section:
            sections.append(section)

    terminal_table = _terminal_value_section(data)
    if not terminal_table:
        section = _prose_text_section(prose, "terminal_value", "Terminal Value")
        if section:
            sections.append(section)

    for builder in (
        _service_key_drivers_section,
        _peer_multiples_section,
        _market_implied_section,
        _priced_in_expectations_section,
        _sensitivity_grid_section,
        _projection_walk_section,
        _valuation_bridge_section,
        _key_assumptions_section,
        _guided_judgment_section,
    ):
        section = builder(data)
        if section:
            sections.append(section)
    if terminal_table:
        sections.append(terminal_table)

    section = _what_would_change_section(data, prose)
    if section:
        sections.append(section)

    section = _basis_warnings_section(data)
    if section:
        sections.append(section)

    section = _data_limits_section(data)
    if section:
        sections.append(section)

    bottom_line = _text(prose.get("bottom_line"))
    if bottom_line:
        sections.append(["## Bottom Line", "", bottom_line])

    for builder in (_sources_section, _audit_section):
        section = builder(data)
        if section:
            sections.append(section)

    lines: list[str] = [f"# {title}", "", NO_ADVICE_LINE, ""]
    for section in sections:
        lines.extend(section)
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def _browser_open_default() -> bool:
    value = os.environ.get("STOCKVALUATION_OPEN_REPORT", "1").strip().lower()
    return value not in {"0", "false", "no", "off"}


def _open_report_in_browser(html_path: Path, renderer) -> bool:
    try:
        return bool(webbrowser.open_new_tab(renderer._file_uri(html_path)))
    except Exception:
        return False


def main() -> int:
    parser = argparse.ArgumentParser(description="Build the StockValuation report from structured run data.")
    parser.add_argument("--input", type=Path, default=None, help="Report data JSON; defaults to stdin.")
    parser.add_argument("--out-dir", type=Path, required=True, help="Directory for report artifacts.")
    parser.add_argument("--skip-html", action="store_true", help="Emit markdown only.")
    parser.add_argument(
        "--open-browser",
        dest="open_browser",
        action="store_true",
        default=None,
        help="Open the generated HTML report in the default browser.",
    )
    parser.add_argument(
        "--no-open",
        dest="open_browser",
        action="store_false",
        help="Do not open the generated HTML report.",
    )
    args = parser.parse_args()

    raw = args.input.read_text(encoding="utf-8") if args.input else sys.stdin.read()
    data = json.loads(raw)
    prose_number_errors = validate_model_prose_numbers(data)
    if prose_number_errors:
        print(json.dumps({"ok": False, "reason": "prose_number_errors", "findings": prose_number_errors}, indent=2))
        return 1
    prose_basis_errors = validate_model_prose_basis(data)
    if prose_basis_errors:
        print(json.dumps({"ok": False, "reason": "prose_basis_errors", "findings": prose_basis_errors}, indent=2))
        return 1
    markdown = build_report_markdown(data)

    prose_lint = _load_module("prose_lint")
    findings = prose_lint.lint_markdown(markdown)
    errors = prose_lint.error_findings(findings)
    if errors:
        print(json.dumps({"ok": False, "reason": "prose_lint_errors", "findings": errors}, indent=2))
        return 1

    out_dir = args.out_dir.expanduser().resolve()
    out_dir.mkdir(parents=True, exist_ok=True)
    markdown_path = out_dir / "report.md"
    report_data_path = out_dir / "report_data.json"
    markdown_path.write_text(markdown, encoding="utf-8")
    report_data_path.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    outputs = {"ok": True, "report_data": str(report_data_path), "markdown": str(markdown_path), "findings": findings}

    if not args.skip_html:
        renderer = _load_module("render_report_html")
        title = _text(data.get("title")) or f"{_text(data.get('company')) or _text(data.get('ticker')) or 'Valuation'} Valuation Report"
        html_path = out_dir / "index.html"
        generated_at = dt.datetime.now().astimezone().strftime("%Y-%m-%d %H:%M %Z")
        html_path.write_text(
            renderer.build_html(
                markdown,
                title,
                _text(data.get("company")) or None,
                _text(data.get("ticker")) or None,
                generated_at,
                report_data=data,
            ),
            encoding="utf-8",
        )
        outputs["html"] = str(html_path)
        outputs["browser_link"] = renderer._file_uri(html_path)
        should_open = args.open_browser if args.open_browser is not None else _browser_open_default()
        outputs["browser_opened"] = _open_report_in_browser(html_path, renderer) if should_open else False

    print(json.dumps(outputs, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
