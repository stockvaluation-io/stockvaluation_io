from orchestration.graph_builder import GraphBuilder


class _DummyOrchestrator:
    def run_agent(self, *_args, **_kwargs):
        return {}


def test_normalize_analyzer_uses_growth_skill_context_for_clamp():
    builder = GraphBuilder(_DummyOrchestrator())

    state = {
        "industry": "technology",
        "financials": {
            "profile": {
                "industry": "technology",
                "country": "United States",
            }
        },
        "segments": {
            "segments": [
                {"sector": "software-application", "industry": "technology", "revenue_share": 1.0}
            ]
        },
        "dcf": {"derived": {"revenue_cagr_pct": 0.12, "margin_end_pct": 25.0}},
        "growth_skill_context": {
            "entity": "softwareinternet",
            "region": "United States",
            "confidenceScore": 0.91,
            "p10": 0.05,
            "p25": 0.08,
            "p50": 0.12,
            "p75": 0.16,
            "p90": 0.20,
        },
    }

    llm_result = {
        "dcf_analysis": {
            "dcf_adjustment_instructions": [
                {
                    "parameter": "revenue_cagr",
                    "new_value": 0.35,  # Above p90; should be clamped to 0.20
                    "unit": "percent",
                    "rationale": "Test out-of-band override.",
                }
            ],
            "proposed_assumptions": {"revenue_cagr": 0.35},
        },
        "recommendations": {"confidence_level": "high"},
        "analyzer_metadata": {},
    }

    normalized = builder._normalize_analyzer_result(llm_result, state)

    proposed = normalized["dcf_analysis"]["proposed_assumptions"]
    assert proposed["revenue_cagr"] == 0.2
    assert (
        normalized["analyzer_metadata"]["growth_skill_override_reason"]
        == "Clamped revenue_cagr from 35.0% down to HIGH-confidence ceiling p90 (20.0%)."
    )


def test_normalize_analyzer_does_not_clamp_when_units_are_mixed_but_value_is_in_band():
    builder = GraphBuilder(_DummyOrchestrator())

    state = {
        "industry": "technology",
        "financials": {
            "profile": {
                "industry": "technology",
                "country": "United States",
            }
        },
        "segments": {
            "segments": [
                {"sector": "software-application", "industry": "technology", "revenue_share": 1.0}
            ]
        },
        "dcf": {"derived": {"revenue_cagr_pct": 12.0, "margin_end_pct": 25.0}},
        "growth_skill_context": {
            "entity": "softwareinternet",
            "region": "United States",
            "confidenceScore": 0.91,
            "p10": 0.05,
            "p25": 0.08,
            "p50": 0.12,
            "p75": 0.16,
            "p90": 0.20,
        },
    }

    llm_result = {
        "dcf_analysis": {
            "dcf_adjustment_instructions": [
                {
                    "parameter": "revenue_cagr",
                    "new_value": 13.5,  # Percent-form value; should be in-band vs p90=20%
                    "unit": "percent",
                    "rationale": "Mixed-unit guard check.",
                }
            ],
            "proposed_assumptions": {"revenue_cagr": 13.5},
        },
        "recommendations": {"confidence_level": "high"},
        "analyzer_metadata": {},
    }

    normalized = builder._normalize_analyzer_result(llm_result, state)
    proposed = normalized["dcf_analysis"]["proposed_assumptions"]
    assert proposed["revenue_cagr"] == 13.5
    assert "growth_skill_override_reason" not in normalized["analyzer_metadata"]
