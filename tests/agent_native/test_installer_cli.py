import json
from pathlib import Path

import pytest

from stockvaluation_agent_native import cli
from stockvaluation_agent_native.installer import AgentInstaller
from stockvaluation_agent_native.service_control import CommandResult, EnvironmentStatus, ServiceController


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
    (tmp_path / "docker-compose.local.yml").write_text("services: {}\n", encoding="utf-8")

    def runner(command, cwd):
        commands.append((command, cwd))
        return 0

    def probe(command, cwd):
        return CommandResult(returncode=0, stdout="ok", stderr="")

    controller = ServiceController(
        project_dir=tmp_path,
        runner=runner,
        probe_runner=probe,
        docker_path_resolver=lambda _: "/usr/bin/docker",
    )
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


def test_service_start_fails_clearly_without_docker_and_does_not_run_compose(tmp_path, capsys):
    commands = []
    controller = ServiceController(
        project_dir=tmp_path,
        runner=lambda command, cwd: commands.append((command, cwd)) or 0,
        docker_path_resolver=lambda _: None,
    )

    exit_code = controller.start()

    captured = capsys.readouterr()
    assert exit_code == 1
    assert commands == []
    assert "Docker Desktop or a compatible Docker Engine with Compose is required" in captured.err
    assert "native" not in captured.err.lower()


def test_service_start_fails_clearly_without_compose_file(tmp_path, capsys):
    controller = ServiceController(
        project_dir=tmp_path,
        runner=lambda command, cwd: 0,
        probe_runner=lambda command, cwd: CommandResult(returncode=0, stdout="ok", stderr=""),
        docker_path_resolver=lambda _: "/usr/bin/docker",
    )

    exit_code = controller.start()

    captured = capsys.readouterr()
    assert exit_code == 1
    assert "Missing docker-compose.local.yml" in captured.err


def test_check_env_reports_presence_without_printing_secret_values(tmp_path, capsys, monkeypatch):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "POSTGRES_PASSWORD=postgres-secret\nYFINANCE_SECRET_KEY=yfinance-live-secret\n",
        encoding="utf-8",
    )
    controller = ServiceController(
        project_dir=tmp_path,
        probe_runner=lambda command, cwd: CommandResult(returncode=0, stdout="ok", stderr=""),
        docker_path_resolver=lambda _: "/usr/bin/docker",
        port_checker=lambda host, port: True,
    )

    status = controller.check_environment()
    EnvironmentStatus.print(status)

    output = capsys.readouterr().out
    assert "POSTGRES_PASSWORD: set" in output
    assert "YFINANCE_SECRET_KEY: set" in output
    assert "CURRENCY_API_KEY" not in output
    assert "postgres-secret" not in output
    assert "yfinance-live-secret" not in output


def test_check_env_detects_docker_compose_daemon_ports_and_placeholder_env(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "POSTGRES_PASSWORD=postgres-secret",
                "DEFAULT_PASSWORD=CHANGE_ME",
                "YFINANCE_SECRET_KEY=yfinance-secret",
                "VALUATION_SERVICE_JWT_SECRET=jwt-secret",
            ]
        ),
        encoding="utf-8",
    )
    probe_calls = []

    def probe(command, cwd):
        probe_calls.append(command)
        return CommandResult(returncode=0, stdout="ok", stderr="")

    controller = ServiceController(
        project_dir=tmp_path,
        probe_runner=probe,
        docker_path_resolver=lambda _: "/usr/bin/docker",
        port_checker=lambda host, port: port != 8081,
    )

    status = controller.check_environment()

    assert status.docker["binary"] is True
    assert status.docker["compose"] is True
    assert status.docker["daemon"] is True
    assert status.values["POSTGRES_PASSWORD"] is True
    assert status.values["DEFAULT_PASSWORD"] is False
    assert "CURRENCY_API_KEY" not in status.values
    assert status.ports["4322"]["available"] is True
    assert status.ports["8081"]["available"] is False
    assert ["docker", "compose", "version"] in probe_calls
    assert ["docker", "info", "--format", "{{.ServerVersion}}"] in probe_calls


def test_status_uses_compose_ps_for_agent_native_services_only(tmp_path):
    probe_calls = []

    def probe(command, cwd):
        probe_calls.append(command)
        return CommandResult(
            returncode=0,
            stdout='\n'.join(
                [
                    '{"Service":"postgres","State":"running"}',
                    '{"Service":"yfinance","State":"running"}',
                    '{"Service":"valuation-service","State":"running"}',
                ]
            ),
            stderr="",
        )

    controller = ServiceController(
        project_dir=tmp_path,
        probe_runner=probe,
        docker_path_resolver=lambda _: "/usr/bin/docker",
    )

    status = controller.status()

    assert probe_calls == [
        [
            "docker",
            "compose",
            "-f",
            "docker-compose.local.yml",
            "ps",
            "--format",
            "json",
            "postgres",
            "yfinance",
            "valuation-service",
        ]
    ]
    assert set(status["compose"]["services"]) == {"postgres", "yfinance", "valuation-service"}
    assert "frontend" not in json.dumps(status)
    assert "bullbeargpt" not in json.dumps(status)


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
