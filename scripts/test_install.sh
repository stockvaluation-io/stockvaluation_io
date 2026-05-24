#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTALL_SCRIPT="${ROOT_DIR}/install.sh"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

pass() {
  echo "PASS: $1"
}

assert_file_contains() {
  local file="$1"
  local pattern="$2"
  if ! grep -qE "$pattern" "$file"; then
    fail "Expected ${file} to contain pattern: ${pattern}"
  fi
}

assert_file_not_contains() {
  local file="$1"
  local pattern="$2"
  if grep -qE "$pattern" "$file"; then
    fail "Did not expect ${file} to contain pattern: ${pattern}"
  fi
}

test_agent_native_install_is_idempotent() {
  local work_dir
  local home_dir
  local project_dir

  work_dir="$(mktemp -d)"
  home_dir="${work_dir}/home"
  project_dir="${work_dir}/project"
  mkdir -p "$home_dir" "$project_dir"

  HOME="$home_dir" PROJECT_DIR="$project_dir" CLIENT=codex "$INSTALL_SCRIPT" install >/dev/null
  HOME="$home_dir" PROJECT_DIR="$project_dir" CLIENT=codex "$INSTALL_SCRIPT" install >/dev/null

  assert_file_contains "${home_dir}/.codex/skills/stockvaluation-io/SKILL.md" 'stockvaluation\.value_ticker'
  assert_file_contains "${home_dir}/.codex/config.toml" '\[mcp_servers\.stockvaluation\]'
  if [[ "$(grep -c 'BEGIN StockValuation.io MCP' "${home_dir}/.codex/config.toml")" != "1" ]]; then
    fail "Expected exactly one Codex MCP block"
  fi

  pass "agent-native install is idempotent"
}

test_installer_surface_does_not_advertise_app_paths() {
  local help_text
  help_text="$("$INSTALL_SCRIPT" help)"

  printf "%s" "$help_text" | grep -q 'install-mcp' || fail "help should mention install-mcp"
  assert_file_not_contains "$INSTALL_SCRIPT" '4200|5002|BullBearGPT|sv value'
  printf "%s" "$help_text" | grep -Eq 'start|status|stop|check-env|uninstall' || fail "help should mention service commands"

  pass "installer surface is agent-native only"
}

main() {
  test_agent_native_install_is_idempotent
  test_installer_surface_does_not_advertise_app_paths
}

main "$@"
