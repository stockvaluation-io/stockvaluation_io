"""Constrained StockValuation agent-native CLI."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Sequence

from .installer import AgentInstaller
from .service_control import EnvironmentStatus, ServiceController


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="sv",
        description="Install StockValuation skills/MCP config and manage the local valuation service.",
    )
    parser.add_argument("--home", type=Path, default=None, help=argparse.SUPPRESS)
    parser.add_argument("--project-dir", type=Path, default=None, help="Project directory with docker-compose.local.yml.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    install = subparsers.add_parser("install", help="Install or update agent-native assets.")
    install_sub = install.add_subparsers(dest="install_target", required=True)
    for target in ("skills", "mcp", "all"):
        target_parser = install_sub.add_parser(target, help=f"Install or update {target}.")
        target_parser.add_argument("--client", choices=["codex", "claude", "all"], default="all")

    service = subparsers.add_parser("service", help="Start, stop, or inspect the local valuation service.")
    service_sub = service.add_subparsers(dest="service_command", required=True)
    service_sub.add_parser("start", help="Start postgres, yfinance, and valuation-service.")
    service_sub.add_parser("stop", help="Stop postgres, yfinance, and valuation-service.")
    service_sub.add_parser("status", help="Show service status.")

    subparsers.add_parser("check-env", help="Check required local environment variables without printing values.")

    uninstall = subparsers.add_parser("uninstall", help="Remove installed skills and MCP config.")
    uninstall.add_argument("--client", choices=["codex", "claude", "all"], default="all")

    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    clients = [getattr(args, "client", "all")]
    installer = AgentInstaller(home=args.home, project_dir=args.project_dir)
    controller = ServiceController(project_dir=args.project_dir)

    if args.command == "install":
        if args.install_target in {"skills", "all"}:
            installed = installer.install_skills(clients)
            for client, path in installed.items():
                print(f"Installed {client} skills: {path}")
        if args.install_target in {"mcp", "all"}:
            installed = installer.install_mcp_config(clients)
            for client, path in installed.items():
                print(f"Installed {client} MCP config: {path}")
        return 0

    if args.command == "service":
        if args.service_command == "start":
            return controller.start()
        if args.service_command == "stop":
            return controller.stop()
        if args.service_command == "status":
            print(json.dumps(controller.status(), indent=2, sort_keys=True))
            return 0

    if args.command == "check-env":
        EnvironmentStatus.print(controller.check_environment())
        return 0

    if args.command == "uninstall":
        removed = installer.uninstall(clients)
        for path in removed:
            print(f"Removed: {path}")
        return 0

    parser.error("unsupported command")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
