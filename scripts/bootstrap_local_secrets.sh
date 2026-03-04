#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ROOT_DIR}/.env"
ENV_EXAMPLE="${ROOT_DIR}/.env.example"

usage() {
  cat <<'EOF'
Usage: ./scripts/bootstrap_local_secrets.sh [--force]

Creates/updates local .env secrets required by docker-compose.local.yml.

Behavior:
  - Copies .env.example -> .env if .env does not exist
  - Fills only missing/placeholder values by default
  - --force regenerates all managed secrets

Managed keys:
  POSTGRES_PASSWORD
  DEFAULT_PASSWORD
  YFINANCE_SECRET_KEY
  VALUATION_AGENT_SECRET_KEY
  BULLBEARGPT_SECRET_KEY
  VALUATION_SERVICE_JWT_SECRET
EOF
}

FORCE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --force)
      FORCE=1
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

if [[ ! -f "$ENV_FILE" ]]; then
  if [[ ! -f "$ENV_EXAMPLE" ]]; then
    echo "Missing ${ENV_EXAMPLE}" >&2
    exit 1
  fi
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "Created ${ENV_FILE} from .env.example"
fi

generate_secret() {
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -hex 32
  else
    python3 - <<'PY'
import secrets
print(secrets.token_hex(32))
PY
  fi
}

read_current_value() {
  local key="$1"
  local value
  value="$(sed -nE "s/^${key}=(.*)$/\1/p" "$ENV_FILE" | tail -n 1 || true)"
  echo "$value"
}

set_key_value() {
  local key="$1"
  local value="$2"

  if grep -qE "^${key}=" "$ENV_FILE"; then
    perl -0pi -e "s/^${key}=.*\$/${key}=${value}/m" "$ENV_FILE"
  else
    printf "\n%s=%s\n" "$key" "$value" >> "$ENV_FILE"
  fi
}

should_replace() {
  local current="$1"
  if [[ "$FORCE" -eq 1 ]]; then
    return 0
  fi
  if [[ -z "$current" ]]; then
    return 0
  fi
  if [[ "$current" == "CHANGE_ME" || "$current" == "CHANGE_ME_32_PLUS_CHARS" ]]; then
    return 0
  fi
  return 1
}

ensure_secret() {
  local key="$1"
  local current
  current="$(read_current_value "$key")"
  if should_replace "$current"; then
    local generated
    generated="$(generate_secret)"
    set_key_value "$key" "$generated"
    echo "Set ${key}"
  else
    echo "Kept ${key} (already set)"
  fi
}

ensure_secret "POSTGRES_PASSWORD"
ensure_secret "DEFAULT_PASSWORD"
ensure_secret "YFINANCE_SECRET_KEY"
ensure_secret "VALUATION_AGENT_SECRET_KEY"
ensure_secret "BULLBEARGPT_SECRET_KEY"
ensure_secret "VALUATION_SERVICE_JWT_SECRET"

echo "Local secret bootstrap complete: ${ENV_FILE}"
