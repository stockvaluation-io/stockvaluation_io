#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8081/api/v1/automated-dcf-analysis}"
BASELINE_FILE="${BASELINE_FILE:-.etl/regression/regression/valuation_baseline_golden_2026.json}"
REPORT_JSON="${REPORT_JSON:-.etl/regression/regression/valuation_drift_report_latest.json}"
REPORT_MD="${REPORT_MD:-.etl/regression/regression/valuation_drift_report_latest.md}"
MAX_INTRINSIC_DRIFT_PCT="${MAX_INTRINSIC_DRIFT_PCT:-8}"
MAX_WACC_DRIFT_BPS="${MAX_WACC_DRIFT_BPS:-75}"
MAX_TERMINAL_GROWTH_DRIFT_BPS="${MAX_TERMINAL_GROWTH_DRIFT_BPS:-50}"
RUN_SMOKE=1

usage() {
  cat <<'USAGE'
Usage: ./scripts/local_release_check.sh [options]

Release gate checks:
  1) Local smoke check (unless --skip-smoke)
  2) Valuation drift regression compare against golden baseline

Options:
  --base-url URL
  --baseline-file PATH
  --report-json PATH
  --report-md PATH
  --max-intrinsic-drift-pct N
  --max-wacc-drift-bps N
  --max-terminal-growth-drift-bps N
  --skip-smoke
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --base-url)
      BASE_URL="${2:-}"
      shift 2
      ;;
    --baseline-file)
      BASELINE_FILE="${2:-}"
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
    --max-intrinsic-drift-pct)
      MAX_INTRINSIC_DRIFT_PCT="${2:-}"
      shift 2
      ;;
    --max-wacc-drift-bps)
      MAX_WACC_DRIFT_BPS="${2:-}"
      shift 2
      ;;
    --max-terminal-growth-drift-bps)
      MAX_TERMINAL_GROWTH_DRIFT_BPS="${2:-}"
      shift 2
      ;;
    --skip-smoke)
      RUN_SMOKE=0
      shift
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

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "Missing baseline file: $BASELINE_FILE" >&2
  echo "Create it first with --write-baseline on valuation_drift_regression.py" >&2
  exit 1
fi

if [[ "$RUN_SMOKE" -eq 1 ]]; then
  echo "[release-check] running local smoke..."
  ./scripts/local_smoke.sh
fi

PYTHON_BIN=""
if command -v python3.11 >/dev/null 2>&1; then
  PYTHON_BIN="python3.11"
elif command -v python3 >/dev/null 2>&1; then
  PYTHON_BIN="python3"
else
  echo "python3.11/python3 not found" >&2
  exit 1
fi

TICKERS="$($PYTHON_BIN - "$BASELINE_FILE" <<'PY'
import json,sys
path=sys.argv[1]
obj=json.load(open(path))
tickers=obj.get('tickers') or sorted((obj.get('snapshots') or {}).keys())
if not tickers:
    raise SystemExit("baseline file has no tickers")
print(','.join(tickers))
PY
)"

echo "[release-check] running valuation drift regression..."
$PYTHON_BIN .etl/tools/regression/valuation_drift_regression.py \
  --base-url "$BASE_URL" \
  --tickers "$TICKERS" \
  --baseline-file "$BASELINE_FILE" \
  --report-json "$REPORT_JSON" \
  --report-md "$REPORT_MD" \
  --max-intrinsic-drift-pct "$MAX_INTRINSIC_DRIFT_PCT" \
  --max-wacc-drift-bps "$MAX_WACC_DRIFT_BPS" \
  --max-terminal-growth-drift-bps "$MAX_TERMINAL_GROWTH_DRIFT_BPS"

echo "[release-check] PASS"
