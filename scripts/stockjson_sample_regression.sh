#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${CONTAINER_NAME:-sv-local-yfinance}"
STOCK_FILE="${STOCK_FILE:-data/stock.json}"
REPORT_JSON="${REPORT_JSON:-.etl/regression/regression/stockjson_sample_regression_latest.json}"
REPORT_MD="${REPORT_MD:-.etl/regression/regression/stockjson_sample_regression_latest.md}"
SEED="${SEED:-42}"
CANDIDATE_LIMIT="${CANDIDATE_LIMIT:-300}"
MAX_SELECTED="${MAX_SELECTED:-30}"
MAX_SECTORS_PER_COUNTRY="${MAX_SECTORS_PER_COUNTRY:-3}"
EXCLUDE_SECTORS="${EXCLUDE_SECTORS:-financial services}"
MAX_DATA_INSUFFICIENCY="${MAX_DATA_INSUFFICIENCY:-999}"

usage() {
  cat <<'USAGE'
Usage: ./scripts/stockjson_sample_regression.sh [options]

Runs a strict valuation sample sweep from data/stock.json using yfinance metadata
to select a diverse non-financial basket by country+sector.

Options:
  --container NAME
  --stock-file PATH
  --report-json PATH
  --report-md PATH
  --seed N
  --candidate-limit N
  --max-selected N
  --max-sectors-per-country N
  --exclude-sectors CSV
  --max-data-insufficiency N
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --container)
      CONTAINER_NAME="${2:-}"
      shift 2
      ;;
    --stock-file)
      STOCK_FILE="${2:-}"
      shift 2
      ;;
    --report-json)
      REPORT_JSON="${2:-}"
      shift 2
      ;;
    --report-md)
      REPORT_MD="${2:-}"
      shift 2
      ;;
    --seed)
      SEED="${2:-}"
      shift 2
      ;;
    --candidate-limit)
      CANDIDATE_LIMIT="${2:-}"
      shift 2
      ;;
    --max-selected)
      MAX_SELECTED="${2:-}"
      shift 2
      ;;
    --max-sectors-per-country)
      MAX_SECTORS_PER_COUNTRY="${2:-}"
      shift 2
      ;;
    --exclude-sectors)
      EXCLUDE_SECTORS="${2:-}"
      shift 2
      ;;
    --max-data-insufficiency)
      MAX_DATA_INSUFFICIENCY="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "$STOCK_FILE" ]]; then
  echo "Missing stock file: $STOCK_FILE" >&2
  exit 1
fi

if ! docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  echo "Container is not running: $CONTAINER_NAME" >&2
  exit 1
fi

mkdir -p "$(dirname "$REPORT_JSON")" "$(dirname "$REPORT_MD")"

docker cp "$STOCK_FILE" "${CONTAINER_NAME}:/tmp/stockjson_sample_input.json"

docker exec -i \
  -e SV_SAMPLE_SEED="$SEED" \
  -e SV_SAMPLE_CANDIDATE_LIMIT="$CANDIDATE_LIMIT" \
  -e SV_SAMPLE_MAX_SELECTED="$MAX_SELECTED" \
  -e SV_SAMPLE_MAX_SECTORS_PER_COUNTRY="$MAX_SECTORS_PER_COUNTRY" \
  -e SV_SAMPLE_EXCLUDE_SECTORS="$EXCLUDE_SECTORS" \
  "$CONTAINER_NAME" python - <<'PY'
import json
import os
import random
import time
import urllib.error
import urllib.parse
import urllib.request

INPUT_PATH = "/tmp/stockjson_sample_input.json"
OUTPUT_PATH = "/tmp/stockjson_sample_report.json"
YFINANCE_BASE = "http://yfinance:5000/api-s"
VALUATION_BASE = "http://valuation-service:8081/api/v1/automated-dcf-analysis"
TIMEOUT_SEC = 30

seed = int(os.environ.get("SV_SAMPLE_SEED", "42"))
candidate_limit = int(os.environ.get("SV_SAMPLE_CANDIDATE_LIMIT", "300"))
max_selected = int(os.environ.get("SV_SAMPLE_MAX_SELECTED", "30"))
max_sectors_per_country = int(os.environ.get("SV_SAMPLE_MAX_SECTORS_PER_COUNTRY", "3"))
excluded_sectors = {
    x.strip().lower()
    for x in os.environ.get("SV_SAMPLE_EXCLUDE_SECTORS", "financial services").split(",")
    if x.strip()
}

random.seed(seed)

def request_json(url, method="GET", payload=None):
    data = None
    headers = {}
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url=url, method=method, data=data, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=TIMEOUT_SEC) as resp:
            body = resp.read().decode("utf-8", errors="replace")
            parsed = json.loads(body) if body else {}
            return resp.status, parsed, body
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        parsed = {}
        if body:
            try:
                parsed = json.loads(body)
            except json.JSONDecodeError:
                parsed = {}
        return e.code, parsed, body
    except Exception as e:
        return None, {"message": str(e)}, str(e)

def classify_failure(status, message):
    lower = (message or "").lower()
    if status == 422 and "revenueltm cannot be zero" in lower:
        return "data_insufficiency_revenue_ltm_zero"
    if status == 422 and "insufficient dimension" in lower:
        return "data_insufficiency_insufficient_dimension"
    if status == 422 and "industry averages not found for mapped industry" in lower:
        return "data_insufficiency_reference_industry_averages_missing"
    if status == 500 and "financial services sector" in lower:
        return "financial_services_unsupported"
    if status == 500 and "query did not return a unique result" in lower:
        return "db_non_unique_result"
    if status is None:
        return "request_error"
    if status >= 500:
        return "unexpected_5xx"
    if status >= 400:
        return "unexpected_4xx"
    return "unknown_failure"

with open(INPUT_PATH, "r", encoding="utf-8") as f:
    stock_rows = json.load(f)

symbols = [row.get("symbol") for row in stock_rows if row.get("symbol")]
random.shuffle(symbols)
symbols = symbols[:candidate_limit]

meta = []
for ticker in symbols:
    info_url = f"{YFINANCE_BASE}/info?{urllib.parse.urlencode({'ticker': ticker})}"
    status, payload, _ = request_json(info_url, method="GET")
    if status != 200:
        continue
    country = (payload.get("country") or payload.get("countryOfIncorporation") or "").strip()
    sector = (payload.get("sector") or "").strip()
    if not country or not sector:
        continue
    if sector.lower() in excluded_sectors:
        continue
    meta.append({
        "ticker": ticker,
        "country": country,
        "sector": sector
    })

pairs = {}
for row in meta:
    key = (row["country"].lower(), row["sector"].lower())
    pairs.setdefault(key, []).append(row["ticker"])

selected = []
for (country, sector), tickers in pairs.items():
    selected.append({
        "ticker": sorted(set(tickers))[0],
        "country": country,
        "sector": sector
    })

selected.sort(key=lambda x: (x["country"], x["sector"], x["ticker"]))

if len(selected) > max_selected:
    by_country = {}
    for row in selected:
        by_country.setdefault(row["country"], []).append(row)
    capped = []
    for country in sorted(by_country.keys()):
        capped.extend(by_country[country][:max_sectors_per_country])
    selected = capped[:max_selected]

results = []
for row in selected:
    ticker = row["ticker"]
    start = time.time()
    status, payload, body = request_json(
        f"{VALUATION_BASE}/{ticker}/valuation",
        method="POST",
        payload={}
    )
    elapsed = round(time.time() - start, 2)
    message = payload.get("message") if isinstance(payload, dict) else None
    ok = status == 200
    rec = {
        "ticker": ticker,
        "country": row["country"],
        "sector": row["sector"],
        "status_code": status,
        "elapsed_sec": elapsed,
        "ok": ok,
    }
    if not ok:
        rec["message"] = message or body[:200]
        rec["category"] = classify_failure(status, rec["message"])
    results.append(rec)

category_counts = {}
for rec in results:
    if rec.get("ok"):
        continue
    key = rec.get("category", "unknown_failure")
    category_counts[key] = category_counts.get(key, 0) + 1

summary = {
    "seed": seed,
    "candidate_limit": candidate_limit,
    "max_selected": max_selected,
    "max_sectors_per_country": max_sectors_per_country,
    "excluded_sectors": sorted(excluded_sectors),
    "source_candidates": len(symbols),
    "meta_candidates_with_country_sector": len(meta),
    "selected_count": len(selected),
    "pass_count": sum(1 for r in results if r.get("ok")),
    "fail_count": sum(1 for r in results if not r.get("ok")),
    "category_counts": category_counts,
}

with open(OUTPUT_PATH, "w", encoding="utf-8") as f:
    json.dump({"summary": summary, "results": results}, f, indent=2)

print(json.dumps(summary, indent=2))
PY

docker cp "${CONTAINER_NAME}:/tmp/stockjson_sample_report.json" "$REPORT_JSON"

python3 - "$REPORT_JSON" "$REPORT_MD" "$MAX_DATA_INSUFFICIENCY" <<'PY'
import json
import sys
from pathlib import Path

report_json = Path(sys.argv[1])
report_md = Path(sys.argv[2])
max_data_insuff = int(sys.argv[3])

obj = json.loads(report_json.read_text(encoding="utf-8"))
summary = obj.get("summary", {})
results = obj.get("results", [])
categories = summary.get("category_counts", {})

data_insuff_count = (
    categories.get("data_insufficiency_revenue_ltm_zero", 0)
    + categories.get("data_insufficiency_insufficient_dimension", 0)
    + categories.get("data_insufficiency_reference_industry_averages_missing", 0)
)
gate_failures = []

if categories.get("db_non_unique_result", 0) > 0:
    gate_failures.append("db_non_unique_result > 0")
if categories.get("unexpected_5xx", 0) > 0:
    gate_failures.append("unexpected_5xx > 0")
if categories.get("unexpected_4xx", 0) > 0:
    gate_failures.append("unexpected_4xx > 0")
if categories.get("request_error", 0) > 0:
    gate_failures.append("request_error > 0")
if categories.get("financial_services_unsupported", 0) > 0:
    gate_failures.append("financial_services_unsupported > 0 (financial sector should be excluded)")
if data_insuff_count > max_data_insuff:
    gate_failures.append(f"data insufficiency count {data_insuff_count} exceeds max {max_data_insuff}")

lines = []
lines.append("# Stock.json Sample Regression")
lines.append("")
lines.append("## Summary")
lines.append("")
for key in [
    "source_candidates",
    "meta_candidates_with_country_sector",
    "selected_count",
    "pass_count",
    "fail_count",
]:
    lines.append(f"- {key}: {summary.get(key)}")
lines.append(f"- excluded_sectors: {', '.join(summary.get('excluded_sectors', []))}")
lines.append("")
lines.append("## Failure Categories")
lines.append("")
if categories:
    for key in sorted(categories.keys()):
        lines.append(f"- {key}: {categories[key]}")
else:
    lines.append("- none")
lines.append("")
lines.append("## Gate Result")
lines.append("")
if gate_failures:
    lines.append("- status: FAIL")
    for item in gate_failures:
        lines.append(f"- reason: {item}")
else:
    lines.append("- status: PASS")
lines.append("")
lines.append("## Failed Tickers")
lines.append("")
failed = [r for r in results if not r.get("ok")]
if failed:
    for rec in failed:
        lines.append(
            f"- {rec.get('ticker')} [{rec.get('country')} | {rec.get('sector')}] "
            f"status={rec.get('status_code')} category={rec.get('category')} message={rec.get('message')}"
        )
else:
    lines.append("- none")

report_md.write_text("\n".join(lines) + "\n", encoding="utf-8")

if gate_failures:
    print("[stockjson-sample-regression] FAIL")
    for reason in gate_failures:
        print(" -", reason)
    sys.exit(1)

print("[stockjson-sample-regression] PASS")
PY

echo "[stockjson-sample-regression] report: $REPORT_JSON"
echo "[stockjson-sample-regression] report: $REPORT_MD"
