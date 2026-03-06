from prompts.agents.analyzer_v1 import get_prompt


def test_analyzer_prompt_renders_with_growth_skill_context():
    prompt = get_prompt(
        {
            "ticker": "AAPL",
            "name": "Apple Inc",
            "industry": "technology",
            "dcf": {"derived": {"revenue_cagr_pct": 5.0}},
            "financials": {"profile": {"industry": "technology"}},
            "segments": {"segments": []},
            "news": {"tone": "neutral"},
            "skills": {
                "growth_skill": {
                    "entity": "softwareinternet",
                    "revenue_cagr_band": {"p25": 0.08, "p50": 0.12, "p75": 0.16},
                }
            },
        }
    )

    assert "GROWTH_SKILL_JSON" in prompt
    assert "softwareinternet" in prompt
