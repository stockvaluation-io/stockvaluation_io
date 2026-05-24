from pathlib import Path

from stockvaluation_agent_native.installer import bundled_skill_dir
from stockvaluation_agent_native.security import sanitize_for_agent


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


def test_sanitize_for_agent_redacts_env_and_nested_secret_values(monkeypatch):
    monkeypatch.setenv("CURRENCY_API_KEY", "currency-live-secret")
    monkeypatch.setenv("POSTGRES_PASSWORD", "postgres-live-secret")

    payload = {
        "error": "CURRENCY_API_KEY=currency-live-secret failed",
        "nested": {
            "password": "postgres-live-secret",
            "safe": "risk-free-rate",
        },
    }

    clean = sanitize_for_agent(payload)

    assert "currency-live-secret" not in str(clean)
    assert "postgres-live-secret" not in str(clean)
    assert clean["nested"]["password"] == "[REDACTED]"
    assert clean["nested"]["safe"] == "risk-free-rate"
