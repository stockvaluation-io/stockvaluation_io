from pathlib import Path

from stockvaluation_agent_native.installer import bundled_skill_dir
from stockvaluation_agent_native.security import sanitize_for_agent

REPO_ROOT = Path(__file__).resolve().parents[2]


def test_skill_pack_contains_required_agent_native_references():
    skill_dir = bundled_skill_dir()

    required = {
        "mcp-tools.md",
        "damodaran-method.md",
        "report-template.md",
        "no-advice-policy.md",
        "assumption-checks.md",
        "accounting-adjustments.md",
        "troubleshooting.md",
    }

    assert (skill_dir / "SKILL.md").exists()
    assert required.issubset({path.name for path in (skill_dir / "references").iterdir()})


def test_main_skill_requires_mcp_json_and_agent_written_educational_report():
    skill_text = (bundled_skill_dir() / "SKILL.md").read_text(encoding="utf-8")

    assert "stockvaluation.value_ticker" in skill_text
    assert "Do not hand-compute valuation math" in skill_text
    assert "educational" in skill_text.lower()
    assert "financial advice" in skill_text.lower()
    assert "BullBearGPT" not in skill_text
    assert "Angular" not in skill_text
    assert "sv value" not in skill_text


def test_report_template_has_no_advice_framing_without_recommendation_phrases():
    template = (bundled_skill_dir() / "references" / "report-template.md").read_text(encoding="utf-8")
    lower = template.lower()

    assert "educational use only" in lower
    assert "not financial advice" in lower
    for phrase in [
        "you should invest",
        "target price is",
        "we recommend buying",
        "we recommend selling",
        "buy rating",
        "sell rating",
        "hold rating",
    ]:
        assert phrase not in lower


def test_mcp_tool_reference_documents_required_tool_names():
    reference = (bundled_skill_dir() / "references" / "mcp-tools.md").read_text(encoding="utf-8")

    for name in [
        "stockvaluation.health",
        "stockvaluation.value_ticker",
        "stockvaluation.recalculate",
        "stockvaluation.get_assumptions",
        "stockvaluation.get_growth_anchor",
        "stockvaluation.get_reference_data_status",
        "stockvaluation.explain_failure",
    ]:
        assert name in reference


def test_default_readme_documents_docker_only_agent_native_runtime():
    readme = (REPO_ROOT / "README.md").read_text(encoding="utf-8")
    lower = readme.lower()

    assert "docker desktop or a compatible docker engine with compose" in lower
    assert "no native java/postgres/yfinance runtime is installed or supported for v1" in lower
    assert "bullbeargpt" not in lower
    assert "angular" not in lower
    assert "sv value" not in lower


def test_compose_hides_legacy_surfaces_behind_non_default_profiles():
    compose = (REPO_ROOT / "docker-compose.local.yml").read_text(encoding="utf-8")

    assert 'profiles: ["legacy-orchestration"]' in compose
    assert 'profiles: ["legacy-bullbeargpt"]' in compose
    assert 'profiles: ["legacy-ui"]' in compose
    assert "BULLBEARGPT_SECRET_KEY:?BULLBEARGPT_SECRET_KEY is required" not in compose
    assert "VALUATION_AGENT_SECRET_KEY:?VALUATION_AGENT_SECRET_KEY is required" not in compose


def test_compose_uses_keyless_frankfurter_currency_provider():
    compose = (REPO_ROOT / "docker-compose.local.yml").read_text(encoding="utf-8")

    assert "CURRENCY_PROVIDER_BASE_URL: ${CURRENCY_PROVIDER_BASE_URL:-https://api.frankfurter.dev/v2}" in compose
    assert "CURRENCY_API_KEY" not in compose
    assert "api.currencybeacon.com" not in compose


def test_env_example_lists_only_agent_native_required_secrets():
    env_example = (REPO_ROOT / ".env.example").read_text(encoding="utf-8")

    assert "POSTGRES_PASSWORD=" in env_example
    assert "YFINANCE_SECRET_KEY=" in env_example
    assert "VALUATION_SERVICE_JWT_SECRET=" in env_example
    assert "CURRENCY_PROVIDER_BASE_URL=https://api.frankfurter.dev/v2" in env_example
    assert "CURRENCY_API_KEY" not in env_example
    assert "api.currencybeacon.com" not in env_example
    assert "BULLBEARGPT_SECRET_KEY" not in env_example
    assert "VALUATION_AGENT_SECRET_KEY" not in env_example
    assert "OPENAI_API_KEY" not in env_example


def test_sanitize_for_agent_redacts_env_and_nested_secret_values(monkeypatch):
    monkeypatch.setenv("YFINANCE_SECRET_KEY", "yfinance-live-secret")
    monkeypatch.setenv("POSTGRES_PASSWORD", "postgres-live-secret")

    payload = {
        "error": "YFINANCE_SECRET_KEY=yfinance-live-secret failed",
        "nested": {
            "password": "postgres-live-secret",
            "safe": "risk-free-rate",
        },
    }

    clean = sanitize_for_agent(payload)

    assert "yfinance-live-secret" not in str(clean)
    assert "postgres-live-secret" not in str(clean)
    assert clean["nested"]["password"] == "[REDACTED]"
    assert clean["nested"]["safe"] == "risk-free-rate"
