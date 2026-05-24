"""Service-management helpers for the local valuation stack."""

from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Callable
from urllib import error, request

REQUIRED_ENV_KEYS = [
    "POSTGRES_PASSWORD",
    "DEFAULT_PASSWORD",
    "YFINANCE_SECRET_KEY",
    "VALUATION_SERVICE_JWT_SECRET",
    "CURRENCY_API_KEY",
]

SERVICE_NAMES = ["postgres", "yfinance", "valuation-service"]


@dataclass
class EnvironmentStatus:
    values: dict[str, bool]

    @staticmethod
    def print(status: "EnvironmentStatus") -> None:
        for key in REQUIRED_ENV_KEYS:
            state = "set" if status.values.get(key) else "missing"
            print(f"{key}: {state}")


class ServiceController:
    def __init__(
        self,
        project_dir: Path | str | None = None,
        runner: Callable[[list[str], Path], int] | None = None,
    ):
        self.project_dir = Path(project_dir).resolve() if project_dir is not None else Path.cwd()
        self.runner = runner or self._run

    def start(self) -> int:
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
            "valuationService": service_health,
            "composeFile": str(self.project_dir / "docker-compose.local.yml"),
        }

    def check_environment(self) -> EnvironmentStatus:
        env_values = self._read_env_file()
        values = {
            key: bool(os.environ.get(key) or env_values.get(key, "").strip())
            for key in REQUIRED_ENV_KEYS
        }
        return EnvironmentStatus(values)

    def _http_health(self) -> dict[str, object]:
        try:
            with request.urlopen("http://localhost:8081/actuator/health", timeout=5) as response:
                return {"reachable": True, "httpStatus": response.status}
        except (error.URLError, TimeoutError) as exc:
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

    @staticmethod
    def _run(command: list[str], cwd: Path) -> int:
        return subprocess.call(command, cwd=cwd)
