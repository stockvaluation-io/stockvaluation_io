"""Install StockValuation skills and MCP config into agent clients."""

from __future__ import annotations

import json
import shutil
import sys
from pathlib import Path
from typing import Iterable

from .service_client import DEFAULT_SERVICE_URL

CLIENTS = {"codex", "claude"}
CODEX_MCP_BEGIN = "# BEGIN StockValuation.io MCP"
CODEX_MCP_END = "# END StockValuation.io MCP"


def bundled_skill_dir() -> Path:
    return Path(__file__).resolve().parent / "skills" / "stockvaluation-io"


class AgentInstaller:
    def __init__(
        self,
        home: Path | str | None = None,
        project_dir: Path | str | None = None,
        python_executable: str | None = None,
    ):
        self.home = Path(home).expanduser() if home is not None else Path.home()
        self.project_dir = Path(project_dir).resolve() if project_dir is not None else Path.cwd()
        self.python_executable = python_executable or sys.executable

    def install_skills(self, clients: Iterable[str]) -> dict[str, str]:
        installed: dict[str, str] = {}
        for client in expand_clients(clients):
            target = self._skill_target(client)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(bundled_skill_dir(), target, dirs_exist_ok=True)
            installed[client] = str(target)
        return installed

    def install_mcp_config(self, clients: Iterable[str]) -> dict[str, str]:
        installed: dict[str, str] = {}
        for client in expand_clients(clients):
            if client == "claude":
                path = self._install_claude_mcp()
            elif client == "codex":
                path = self._install_codex_mcp()
            else:
                continue
            installed[client] = str(path)
        return installed

    def uninstall(self, clients: Iterable[str]) -> list[str]:
        removed: list[str] = []
        for client in expand_clients(clients):
            skill_target = self._skill_target(client)
            if skill_target.exists():
                shutil.rmtree(skill_target)
                removed.append(str(skill_target))
            if client == "claude":
                path = self._remove_claude_mcp()
                if path:
                    removed.append(str(path))
            elif client == "codex":
                path = self._remove_codex_mcp()
                if path:
                    removed.append(str(path))
        return removed

    def _skill_target(self, client: str) -> Path:
        if client == "codex":
            return self.home / ".codex" / "skills" / "stockvaluation-io"
        if client == "claude":
            return self.home / ".claude" / "skills" / "stockvaluation-io"
        raise ValueError(f"Unsupported client: {client}")

    def _mcp_server_config(self) -> dict[str, object]:
        return {
            "command": self.python_executable,
            "args": ["-m", "stockvaluation_agent_native.mcp_server"],
            "env": {"STOCKVALUATION_SERVICE_URL": DEFAULT_SERVICE_URL},
        }

    def _install_claude_mcp(self) -> Path:
        path = self.project_dir / ".mcp.json"
        existing: dict[str, object] = {}
        if path.exists():
            existing = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(existing, dict):
                existing = {}
        servers = existing.setdefault("mcpServers", {})
        if not isinstance(servers, dict):
            servers = {}
            existing["mcpServers"] = servers
        servers["stockvaluation"] = self._mcp_server_config()
        path.write_text(json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return path

    def _install_codex_mcp(self) -> Path:
        path = self.home / ".codex" / "config.toml"
        path.parent.mkdir(parents=True, exist_ok=True)
        contents = path.read_text(encoding="utf-8") if path.exists() else ""
        block = self._codex_mcp_block()
        contents = replace_marked_block(contents, block)
        path.write_text(contents, encoding="utf-8")
        return path

    def _remove_claude_mcp(self) -> Path | None:
        path = self.project_dir / ".mcp.json"
        if not path.exists():
            return None
        existing = json.loads(path.read_text(encoding="utf-8"))
        servers = existing.get("mcpServers")
        if isinstance(servers, dict):
            servers.pop("stockvaluation", None)
        path.write_text(json.dumps(existing, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return path

    def _remove_codex_mcp(self) -> Path | None:
        path = self.home / ".codex" / "config.toml"
        if not path.exists():
            return None
        path.write_text(remove_marked_block(path.read_text(encoding="utf-8")), encoding="utf-8")
        return path

    def _codex_mcp_block(self) -> str:
        escaped_python = _toml_string(self.python_executable)
        escaped_url = _toml_string(DEFAULT_SERVICE_URL)
        return "\n".join(
            [
                CODEX_MCP_BEGIN,
                "[mcp_servers.stockvaluation]",
                f"command = {escaped_python}",
                'args = ["-m", "stockvaluation_agent_native.mcp_server"]',
                f'env = {{ STOCKVALUATION_SERVICE_URL = {escaped_url} }}',
                CODEX_MCP_END,
                "",
            ]
        )


def expand_clients(clients: Iterable[str]) -> list[str]:
    expanded: list[str] = []
    for client in clients:
        if client == "all":
            expanded.extend(sorted(CLIENTS))
        elif client in CLIENTS:
            expanded.append(client)
        else:
            raise ValueError(f"Unsupported client: {client}")
    return sorted(set(expanded))


def replace_marked_block(contents: str, block: str) -> str:
    stripped = remove_marked_block(contents).rstrip()
    if stripped:
        return stripped + "\n\n" + block
    return block


def remove_marked_block(contents: str) -> str:
    begin = contents.find(CODEX_MCP_BEGIN)
    end = contents.find(CODEX_MCP_END)
    if begin == -1 or end == -1:
        return contents
    end += len(CODEX_MCP_END)
    while end < len(contents) and contents[end] in "\r\n":
        end += 1
    return (contents[:begin] + contents[end:]).strip() + ("\n" if contents[:begin] or contents[end:] else "")


def _toml_string(value: str) -> str:
    return json.dumps(value)
