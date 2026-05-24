"""Service-management helpers for the local valuation stack."""

from __future__ import annotations

import http.client
import json
import os
import shutil
import socket
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from urllib import error, request

REQUIRED_ENV_KEYS = [
    "POSTGRES_PASSWORD",
    "DEFAULT_PASSWORD",
    "YFINANCE_SECRET_KEY",
    "VALUATION_SERVICE_JWT_SECRET",
]

SERVICE_NAMES = ["postgres", "yfinance", "valuation-service"]
REQUIRED_PORTS = {
    4322: "postgres",
    8081: "valuation-service",
}
PLACEHOLDER_VALUES = {"", "CHANGE_ME", "CHANGE_ME_32_PLUS_CHARS"}


@dataclass
class CommandResult:
    returncode: int
    stdout: str = ""
    stderr: str = ""


@dataclass
class EnvironmentStatus:
    values: dict[str, bool]
    docker: dict[str, object]
    ports: dict[str, dict[str, object]]
    compose_file_exists: bool

    @staticmethod
    def print(status: "EnvironmentStatus") -> None:
        print("Docker:")
        print(f"  binary: {'found' if status.docker.get('binary') else 'missing'}")
        print(f"  compose: {'available' if status.docker.get('compose') else 'missing'}")
        print(f"  daemon: {'reachable' if status.docker.get('daemon') else 'unreachable'}")
        detail = status.docker.get("detail")
        if detail:
            print(f"  detail: {detail}")
        print(f"compose file: {'found' if status.compose_file_exists else 'missing'}")
        print("Required ports:")
        for port, info in status.ports.items():
            state = "available" if info.get("available") else "occupied"
            print(f"  127.0.0.1:{port} ({info.get('service')}): {state}")
        print("Required environment:")
        for key in REQUIRED_ENV_KEYS:
            state = "set" if status.values.get(key) else "missing"
            print(f"  {key}: {state}")


class ServiceController:
    def __init__(
        self,
        project_dir: Path | str | None = None,
        runner: Callable[[list[str], Path], int] | None = None,
        probe_runner: Callable[[list[str], Path], CommandResult] | None = None,
        docker_path_resolver: Callable[[str], str | None] | None = None,
        port_checker: Callable[[str, int], bool] | None = None,
    ):
        self.project_dir = Path(project_dir).resolve() if project_dir is not None else Path.cwd()
        self.runner = runner or self._run
        self.probe_runner = probe_runner or self._probe
        self.docker_path_resolver = docker_path_resolver or shutil.which
        self.port_checker = port_checker or self._port_available

    def start(self) -> int:
        docker = self._docker_status()
        if not self._docker_ready(docker):
            print(
                "Docker Desktop or a compatible Docker Engine with Compose is required for "
                "StockValuation.io v1. Start Docker, verify `docker compose version`, then retry.",
                file=sys.stderr,
            )
            detail = docker.get("detail")
            if detail:
                print(f"Docker check: {detail}", file=sys.stderr)
            return 1
        compose_file = self.project_dir / "docker-compose.local.yml"
        if not compose_file.exists():
            print(
                f"Missing docker-compose.local.yml at {compose_file}. Run service commands from the repo root "
                "or pass --project-dir.",
                file=sys.stderr,
            )
            return 1
        return self.runner(
            ["docker", "compose", "-f", "docker-compose.local.yml", "up", "-d", "--build", *SERVICE_NAMES],
            self.project_dir,
        )

    def stop(self) -> int:
        return self.runner(
            ["docker", "compose", "-f", "docker-compose.local.yml", "stop", *reversed(SERVICE_NAMES)],
            self.project_dir,
        )

    def status(self) -> dict[str, object]:
        service_health = self._http_health()
        return {
            "services": SERVICE_NAMES,
            "compose": self._compose_ps(),
            "valuationService": service_health,
            "composeFile": str(self.project_dir / "docker-compose.local.yml"),
        }

    def check_environment(self) -> EnvironmentStatus:
        env_values = self._read_env_file()
        values = {
            key: self._has_required_value(os.environ.get(key) or env_values.get(key, ""))
            for key in REQUIRED_ENV_KEYS
        }
        return EnvironmentStatus(
            values=values,
            docker=self._docker_status(),
            ports=self._port_status(),
            compose_file_exists=(self.project_dir / "docker-compose.local.yml").exists(),
        )

    def _http_health(self) -> dict[str, object]:
        try:
            with request.urlopen("http://localhost:8081/actuator/health", timeout=5) as response:
                return {"reachable": True, "httpStatus": response.status}
        except (error.URLError, TimeoutError, http.client.RemoteDisconnected) as exc:
            return {"reachable": False, "error": str(exc)}

    def _read_env_file(self) -> dict[str, str]:
        path = self.project_dir / ".env"
        if not path.exists():
            return {}
        values: dict[str, str] = {}
        for line in path.read_text(encoding="utf-8").splitlines():
            if not line or line.lstrip().startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
        return values

    def _docker_status(self) -> dict[str, object]:
        docker_path = self.docker_path_resolver("docker")
        if not docker_path:
            return {
                "binary": False,
                "compose": False,
                "daemon": False,
                "detail": "docker command was not found on PATH",
            }

        compose = self.probe_runner(["docker", "compose", "version"], self.project_dir)
        if compose.returncode != 0:
            return {
                "binary": True,
                "compose": False,
                "daemon": False,
                "detail": self._safe_probe_detail(compose) or "docker compose is not available",
            }

        daemon = self.probe_runner(["docker", "info", "--format", "{{.ServerVersion}}"], self.project_dir)
        return {
            "binary": True,
            "compose": True,
            "daemon": daemon.returncode == 0,
            "detail": "" if daemon.returncode == 0 else self._safe_probe_detail(daemon) or "docker daemon is not reachable",
        }

    @staticmethod
    def _docker_ready(status: dict[str, object]) -> bool:
        return bool(status.get("binary") and status.get("compose") and status.get("daemon"))

    @staticmethod
    def _safe_probe_detail(result: CommandResult) -> str:
        detail = (result.stderr or result.stdout or "").strip()
        if not detail:
            return ""
        return detail.splitlines()[0][:240]

    def _port_status(self) -> dict[str, dict[str, object]]:
        return {
            str(port): {
                "service": service,
                "host": "127.0.0.1",
                "available": self.port_checker("127.0.0.1", port),
            }
            for port, service in REQUIRED_PORTS.items()
        }

    def _compose_ps(self) -> dict[str, object]:
        if not self.docker_path_resolver("docker"):
            return {"available": False, "services": {}, "error": "docker command was not found on PATH"}
        result = self.probe_runner(
            ["docker", "compose", "-f", "docker-compose.local.yml", "ps", "--format", "json", *SERVICE_NAMES],
            self.project_dir,
        )
        if result.returncode != 0:
            return {"available": False, "services": {}, "error": self._safe_probe_detail(result)}
        return {"available": True, "services": self._parse_compose_ps(result.stdout)}

    @staticmethod
    def _parse_compose_ps(stdout: str) -> dict[str, object]:
        services: dict[str, object] = {}
        stripped = stdout.strip()
        if not stripped:
            return services
        try:
            parsed = json.loads(stripped)
            if isinstance(parsed, list):
                rows = parsed
            elif isinstance(parsed, dict):
                rows = [parsed]
            else:
                rows = []
        except json.JSONDecodeError:
            rows = []
            for line in stripped.splitlines():
                try:
                    row = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if isinstance(row, dict):
                    rows.append(row)
        for row in rows:
            if not isinstance(row, dict):
                continue
            service = row.get("Service") or row.get("Name")
            if isinstance(service, str) and service in SERVICE_NAMES:
                services[service] = {
                    "name": row.get("Name") or row.get("Names"),
                    "service": service,
                    "state": row.get("State"),
                    "health": row.get("Health"),
                    "status": row.get("Status"),
                    "ports": row.get("Ports"),
                }
        return services

    @staticmethod
    def _has_required_value(value: str | None) -> bool:
        if value is None:
            return False
        return value.strip() not in PLACEHOLDER_VALUES

    @staticmethod
    def _port_available(host: str, port: int) -> bool:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                sock.bind((host, port))
            except OSError:
                return False
        return True

    @staticmethod
    def _run(command: list[str], cwd: Path) -> int:
        return subprocess.call(command, cwd=cwd)

    @staticmethod
    def _probe(command: list[str], cwd: Path) -> CommandResult:
        completed = subprocess.run(command, cwd=cwd, capture_output=True, text=True, check=False)
        return CommandResult(completed.returncode, completed.stdout, completed.stderr)
