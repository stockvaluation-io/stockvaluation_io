#!/usr/bin/env bash
set -euo pipefail

# StockValuation.io agent-native installer shim.
# This script intentionally delegates to the constrained Python CLI. It does
# not run valuations, generate reports, or start UI/chat services.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

find_python() {
  if [[ -n "${PYTHON_BIN:-}" ]]; then
    echo "$PYTHON_BIN"
    return
  fi
  if command -v python3.11 >/dev/null 2>&1; then
    command -v python3.11
    return
  fi
  if command -v python3 >/dev/null 2>&1; then
    command -v python3
    return
  fi
  echo "python3.11 or python3 is required." >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: ./install.sh [command]

Docker Desktop or a compatible Docker Engine with Compose is required for service commands.

Commands:
  install        Install/update StockValuation skills and MCP config (default)
  install-skills Install/update skills only
  install-mcp    Install/update MCP config only
  start          Start the local valuation service plumbing
  status         Show local service status
  stop           Stop the local valuation service plumbing
  check-env      Check required env vars without printing values
  uninstall      Remove installed skills and MCP config
  help           Show this help

Optional:
  CLIENT=codex|claude|all      Agent client target (default: all)
  PROJECT_DIR=/path/to/repo    Project directory for service commands/config
EOF
}

run_sv() {
  local python_bin
  python_bin="$(find_python)"
  PYTHONPATH="$ROOT_DIR${PYTHONPATH:+:$PYTHONPATH}" "$python_bin" -m stockvaluation_agent_native.cli "$@"
}

run_sv_project() {
  if [[ -n "${PROJECT_DIR:-}" ]]; then
    run_sv --project-dir "$PROJECT_DIR" "$@"
  else
    run_sv "$@"
  fi
}

main() {
  local command="${1:-install}"
  case "$command" in
    install)
      run_sv_project install all --client "${CLIENT:-all}"
      ;;
    install-skills)
      run_sv_project install skills --client "${CLIENT:-all}"
      ;;
    install-mcp)
      run_sv_project install mcp --client "${CLIENT:-all}"
      ;;
    start)
      run_sv_project service start
      ;;
    status)
      run_sv_project service status
      ;;
    stop)
      run_sv_project service stop
      ;;
    check-env)
      run_sv_project check-env
      ;;
    uninstall)
      run_sv_project uninstall --client "${CLIENT:-all}"
      ;;
    help|-h|--help)
      usage
      ;;
    *)
      echo "Unknown command: $command" >&2
      usage >&2
      exit 2
      ;;
  esac
}

main "$@"
