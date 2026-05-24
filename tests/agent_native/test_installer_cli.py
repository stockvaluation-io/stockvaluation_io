import json
from pathlib import Path

import pytest

from stockvaluation_agent_native import cli
from stockvaluation_agent_native.installer import AgentInstaller
from stockvaluation_agent_native.service_control import EnvironmentStatus, ServiceController


def test_install_skills_is_idempotent_for_codex_and_claude(tmp_path):
    home = tmp_path / "home"
    installer = AgentInstaller(home=home)

    first = installer.install_skills(["codex", "claude"])
    second = installer.install_skills(["codex", "claude"])

    assert first == second
    codex_skill = home / ".codex" / "skills" / "stockvaluation-io" / "SKILL.md"
    claude_skill = home / ".claude" / "skills" / "stockvaluation-io" / "SKILL.md"
    assert codex_skill.exists()
    assert claude_skill.exists()
    assert "stockvaluation.value_ticker" in codex_skill.read_text(encoding="utf-8")
    assert (codex_skill.parent / "references" / "mcp-tools.md").exists()


def test_install_mcp_config_is_idempotent_for_claude_and_codex(tmp_path):
    home = tmp_path / "home"
    project = tmp_path / "project"
    project.mkdir()
    installer = AgentInstaller(home=home, project_dir=project, python_executable="/usr/bin/python3")

    installer.install_mcp_config(["claude", "codex"])
    installer.install_mcp_config(["claude", "codex"])

    claude_config = json.loads((project / ".mcp.json").read_text(encoding="utf-8"))
    assert claude_config["mcpServers"]["stockvaluation"]["command"] == "/usr/bin/python3"
    assert claude_config["mcpServers"]["stockvaluation"]["args"] == [
        "-m",
        "stockvaluation_agent_native.mcp_server",
    ]

    codex_config = home / ".codex" / "config.toml"
    contents = codex_config.read_text(encoding="utf-8")
    assert contents.count("BEGIN StockValuation.io MCP") == 1
    assert '[mcp_servers.stockvaluation]' in contents
    assert 'STOCKVALUATION_SERVICE_URL = "http://localhost:8081/api/v1/automated-dcf-analysis"' in contents


def test_cli_exposes_only_install_service_check_and_uninstall_commands(capsys):
    parser = cli.build_parser()

    with pytest.raises(SystemExit):
        parser.parse_args(["value", "MSFT"])

    parser.parse_args(["install", "skills", "--client", "codex"])
    parser.parse_args(["install", "mcp", "--client", "claude"])
    parser.parse_args(["service", "start"])
    parser.parse_args(["service", "status"])
    parser.parse_args(["service", "stop"])
    parser.parse_args(["check-env"])
    parser.parse_args(["uninstall", "--client", "codex"])


def test_service_start_uses_hidden_service_plumbing_only(tmp_path):
    commands = []

    def runner(command, cwd):
        commands.append((command, cwd))
        return 0

    controller = ServiceController(project_dir=tmp_path, runner=runner)
    controller.start()

    assert commands == [
        (
            [
                "docker",
                "compose",
                "-f",
                "docker-compose.local.yml",
                "up",
                "-d",
                "--build",
                "postgres",
                "yfinance",
                "valuation-service",
            ],
            tmp_path,
        )
    ]


def test_check_env_reports_presence_without_printing_secret_values(tmp_path, capsys, monkeypatch):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "CURRENCY_API_KEY=currency-live-secret\nPOSTGRES_PASSWORD=postgres-secret\n",
        encoding="utf-8",
    )
    controller = ServiceController(project_dir=tmp_path)

    status = controller.check_environment()
    EnvironmentStatus.print(status)

    output = capsys.readouterr().out
    assert "CURRENCY_API_KEY: set" in output
    assert "POSTGRES_PASSWORD: set" in output
    assert "currency-live-secret" not in output
    assert "postgres-secret" not in output


def test_uninstall_removes_installed_skill_and_mcp_blocks(tmp_path):
    home = tmp_path / "home"
    project = tmp_path / "project"
    project.mkdir()
    installer = AgentInstaller(home=home, project_dir=project)
    installer.install_skills(["codex"])
    installer.install_mcp_config(["claude", "codex"])

    removed = installer.uninstall(["codex", "claude"])

    assert removed
    assert not (home / ".codex" / "skills" / "stockvaluation-io").exists()
    assert "stockvaluation" not in (project / ".mcp.json").read_text(encoding="utf-8")
    assert "StockValuation.io MCP" not in (home / ".codex" / "config.toml").read_text(encoding="utf-8")
