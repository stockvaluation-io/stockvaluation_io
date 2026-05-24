#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.local.yml}"
TICKER="${TICKER:-MSFT}"

usage() {
  cat <<'EOF'
Usage: ./scripts/local_smoke.sh [--agent-native] [--ticker SYMBOL]

Checks the Docker-backed agent-native product path only:
  - yfinance health inside the Docker network
  - valuation-service /{ticker}/valuation endpoint on host :8081
  - MCP stockvaluation.health through stdio
  - MCP stockvaluation.value_ticker through stdio

Options:
  --agent-native  Accepted for compatibility; this is now the only smoke path
  --ticker        Ticker to use for functional checks (default: MSFT)
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --agent-native)
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

find_python() {
  if command -v python3.11 >/dev/null 2>&1; then
    command -v python3.11
    return
  fi
  if command -v python3 >/dev/null 2>&1; then
    command -v python3
    return
  fi
  echo "Missing required command: python3.11 or python3" >&2
  exit 1
}

need_cmd curl
need_cmd docker
PYTHON_BIN="$(find_python)"

json_status_check() {
  local name="$1"
  local payload_file="$2"
  "$PYTHON_BIN" - "$name" "$payload_file" <<'PY'
import json, sys
name, path = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    data = json.load(f)
if isinstance(data, dict) and data.get("status") in {"healthy", "UP"}:
    print(f"[OK] {name}: status={data.get('status')}")
    sys.exit(0)
print(f"[FAIL] {name}: unexpected payload shape", file=sys.stderr)
print(json.dumps(data, indent=2)[:1000], file=sys.stderr)
sys.exit(1)
PY
}

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
  java_code="000"
  curl_rc=1
  for attempt in $(seq 1 30); do
    set +e
    java_code="$(
      curl -sS \
        -o "$tmp_java" \
        -w "%{http_code}" \
        --max-time 120 \
        -H "Content-Type: application/json" \
        -X POST "http://localhost:8081/api/v1/automated-dcf-analysis/${TICKER}/valuation" \
        -d '{}'
    )"
    curl_rc=$?
    set -e
    if [[ "$curl_rc" -eq 0 && "$java_code" == "200" ]]; then
      break
    fi
    if [[ "$attempt" -lt 30 ]]; then
      sleep 2
    fi
  done

  if [[ "$curl_rc" -ne 0 || "$java_code" != "200" ]]; then
    echo "[FAIL] valuation-service baseline DCF returned curl=$curl_rc HTTP $java_code" >&2
    cat "$tmp_java" >&2
    rm -f "$tmp_java"
    exit 1
  fi

  "$PYTHON_BIN" - "$tmp_java" <<'PY'
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

run_mcp_call_check() {
  local tool="$1"
  local arguments="$2"
  local tmp_mcp
  tmp_mcp="$(mktemp)"
  printf '%s\n' \
    "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/call\",\"params\":{\"name\":\"${tool}\",\"arguments\":${arguments}}}" \
    | "$PYTHON_BIN" -m stockvaluation_agent_native.mcp_server > "$tmp_mcp"
  "$PYTHON_BIN" - "$tmp_mcp" "$tool" <<'PY'
import json, sys
path, tool = sys.argv[1], sys.argv[2]
with open(path, "r", encoding="utf-8") as f:
    response = json.load(f)
result = response.get("result") or {}
structured = result.get("structuredContent") or {}
if structured.get("ok") is not True:
    raise SystemExit(f"[FAIL] {tool} did not return ok=true")
if tool == "stockvaluation.value_ticker":
    dcf = structured.get("dcf") or {}
    if dcf.get("estimatedValuePerShare") is None:
        raise SystemExit("[FAIL] MCP value_ticker missing DCF estimatedValuePerShare")
    print(
        f"[OK] MCP value_ticker ticker={structured.get('ticker')} "
        f"company={dcf.get('companyName')} intrinsic={dcf.get('estimatedValuePerShare')}"
    )
else:
    service = structured.get("service") or {}
    print(f"[OK] {tool}: service status={service.get('status')}")
PY
  rm -f "$tmp_mcp"
}

echo "== Agent-Native Local Smoke Test =="
echo "compose file: $COMPOSE_FILE"
echo "ticker: $TICKER"
echo "[1/4] yfinance health"
run_yfinance_health
echo "[2/4] valuation-service baseline DCF"
run_valuation_service_check
echo "[3/4] MCP health"
run_mcp_call_check "stockvaluation.health" "{}"
echo "[4/4] MCP value_ticker"
run_mcp_call_check "stockvaluation.value_ticker" "{\"ticker\":\"${TICKER}\"}"
echo "Agent-native smoke test passed."
