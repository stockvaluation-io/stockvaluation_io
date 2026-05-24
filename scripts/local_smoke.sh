#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.local.yml}"
TICKER="${TICKER:-AAPL}"
RUN_FULL=0
RUN_AGENT_NATIVE=0

usage() {
  cat <<'EOF'
Usage: ./scripts/local_smoke.sh [--agent-native] [--full] [--ticker SYMBOL]

Checks:
  - valuation-agent health (host :5001)
  - yfinance health (inside docker network)
  - valuation-service /{ticker}/valuation endpoint (host :8081)

Optional:
  --agent-native  Check only the agent-native product path: yfinance, valuation-service, and MCP value_ticker
  --full    Run valuation-agent /api-s/valuate (requires LLM keys and takes longer)
  --ticker  Ticker to use for functional checks (default: AAPL)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-native)
      RUN_AGENT_NATIVE=1
      shift
      ;;
    --full)
      RUN_FULL=1
      shift
      ;;
    --ticker)
      TICKER="${2:-}"
      if [[ -z "$TICKER" ]]; then
        echo "Missing value for --ticker" >&2
        exit 2
      fi
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

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required command: $1" >&2
    exit 1
  }
}

need_cmd curl
need_cmd docker
need_cmd python3

json_status_check() {
  local name="$1"
  local payload_file="$2"
  python3 - "$name" "$payload_file" <<'PY'
import json, sys
name, path = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)
if isinstance(data, dict) and data.get("status") == "healthy":
    print(f"[OK] {name}: status=healthy")
    sys.exit(0)
print(f"[WARN] {name}: unexpected payload shape", file=sys.stderr)
print(json.dumps(data, indent=2)[:1000], file=sys.stderr)
sys.exit(1)
PY
}

echo "== Local Smoke Test =="
echo "compose file: $COMPOSE_FILE"
echo "ticker: $TICKER"

run_yfinance_health() {
  echo "yfinance health (internal via docker exec)"
  tmp_yf="$(mktemp)"
  docker exec sv-local-yfinance curl -fsS --max-time 10 "http://localhost:5000/health" > "$tmp_yf"
  json_status_check "yfinance" "$tmp_yf"
  rm -f "$tmp_yf"
}

run_valuation_service_check() {
  echo "valuation-service /{ticker}/valuation API (host)"
  tmp_java="$(mktemp)"
  java_code="$(
    curl -sS \
      -o "$tmp_java" \
      -w "%{http_code}" \
      --max-time 120 \
      -H "Content-Type: application/json" \
      -X POST "http://localhost:8081/api/v1/automated-dcf-analysis/${TICKER}/valuation" \
      -d '{}'
  )"

  if [[ "$java_code" != "200" ]]; then
    echo "[FAIL] valuation-service baseline DCF returned HTTP $java_code" >&2
    cat "$tmp_java" >&2
    rm -f "$tmp_java"
    exit 1
  fi

  python3 - "$tmp_java" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    payload = json.load(f)
data = payload.get("data", payload)
if not isinstance(data, dict):
    raise SystemExit("[FAIL] valuation-service response missing object payload")
company = data.get("companyDTO") or {}
name = data.get("companyName") or company.get("companyName") or "unknown"
print(f"[OK] valuation-service baseline DCF response for: {name}")
PY
  rm -f "$tmp_java"
}

run_mcp_value_ticker_check() {
  echo "MCP stockvaluation.value_ticker (stdio)"
  tmp_mcp="$(mktemp)"
  printf '%s\n' \
    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"stockvaluation.value_ticker\",\"arguments\":{\"ticker\":\"${TICKER}\"}}}" \
    | python3 -m stockvaluation_agent_native.mcp_server > "$tmp_mcp"
  python3 - "$tmp_mcp" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    response = json.load(f)
result = response.get("result") or {}
structured = result.get("structuredContent") or {}
if structured.get("ok") is not True:
    raise SystemExit("[FAIL] MCP value_ticker did not return ok=true")
dcf = structured.get("dcf") or {}
if dcf.get("estimatedValuePerShare") is None:
    raise SystemExit("[FAIL] MCP value_ticker missing DCF estimatedValuePerShare")
print(
    f"[OK] MCP value_ticker ticker={structured.get('ticker')} "
    f"company={dcf.get('companyName')} intrinsic={dcf.get('estimatedValuePerShare')}"
)
PY
  rm -f "$tmp_mcp"
}

if [[ "$RUN_AGENT_NATIVE" -eq 1 ]]; then
  echo "[1/3] yfinance health"
  run_yfinance_health
  echo "[2/3] valuation-service baseline DCF"
  run_valuation_service_check
  echo "[3/3] MCP value_ticker"
  run_mcp_value_ticker_check
  echo "Agent-native smoke test passed."
  exit 0
fi

echo "[1/4] valuation-agent health (host)"
tmp_agent="$(mktemp)"
curl -fsS --max-time 10 "http://localhost:5001/health" > "$tmp_agent"
json_status_check "valuation-agent" "$tmp_agent"
rm -f "$tmp_agent"

echo "[2/4] yfinance health"
run_yfinance_health

echo "[3/4] valuation-service baseline DCF"
run_valuation_service_check

if [[ "$RUN_FULL" -eq 1 ]]; then
  echo "[4/4] valuation-agent full orchestration (/api-s/valuate) [slow]"
  tmp_full="$(mktemp)"
  full_code="$(
    curl -sS \
      -o "$tmp_full" \
      -w "%{http_code}" \
      --max-time 300 \
      -H "Content-Type: application/json" \
      -H "X-Local-User: local-smoke" \
      -X POST "http://localhost:5001/api-s/valuate" \
      -d "{\"ticker\":\"${TICKER}\"}"
  )"

  if [[ "$full_code" != "200" ]]; then
    echo "[FAIL] valuation-agent /api-s/valuate returned HTTP $full_code" >&2
    cat "$tmp_full" >&2
    rm -f "$tmp_full"
    exit 1
  fi

  python3 - "$tmp_full" <<'PY'
import json, sys
with open(sys.argv[1], "r", encoding="utf-8") as f:
    payload = json.load(f)
vid = payload.get("valuation_id")
aid = payload.get("audit_run_id")
ticker = payload.get("ticker")
if not vid:
    raise SystemExit("[FAIL] /api-s/valuate response missing valuation_id")
segments = payload.get("segments") or []
dcf = payload.get("dcf") or {}
financial = dcf.get("financialDTO") or {}
if isinstance(segments, list) and len(segments) > 0:
    revenues_by_sector = financial.get("revenuesBySector") or {}
    growth_by_sector = financial.get("revenueGrowthRateBySector") or {}
    income_by_sector = financial.get("ebitOperatingIncomeSector") or {}
    if not isinstance(revenues_by_sector, dict) or not revenues_by_sector:
        raise SystemExit("[FAIL] /api-s/valuate returned segments but revenuesBySector is empty")
    if not isinstance(growth_by_sector, dict) or not growth_by_sector:
        raise SystemExit("[FAIL] /api-s/valuate returned segments but revenueGrowthRateBySector is empty")
    if not isinstance(income_by_sector, dict) or not income_by_sector:
        raise SystemExit("[FAIL] /api-s/valuate returned segments but ebitOperatingIncomeSector is empty")
    print(
        f"[OK] valuation-agent /api-s/valuate ticker={ticker} valuation_id={vid} "
        f"audit_run_id={aid} sectors={len(revenues_by_sector)}"
    )
else:
    print(f"[OK] valuation-agent /api-s/valuate ticker={ticker} valuation_id={vid} audit_run_id={aid}")
PY
  rm -f "$tmp_full"
else
  echo "[4/4] full orchestration skipped (use --full to enable)"
fi

echo "Smoke test passed."
