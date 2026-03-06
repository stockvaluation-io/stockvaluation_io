from domain.knowledge.skill_context import build_skill_bundle


def test_build_skill_bundle_includes_segment_skills_only_for_valid_segments():
    bundle = build_skill_bundle(
        industry="technology",
        segments_payload={
            "segments": [
                {"sector": "software-application", "industry": "technology", "revenue_share": 0.7, "mapping_score": 0.91},
                {"sector": "UNKNOWN", "industry": "technology", "revenue_share": 0.2, "mapping_score": 0.8},
                {"sector": "semiconductors", "industry": "technology", "revenue_share": 0.0, "mapping_score": 0.9},
            ]
        },
    )

    assert bundle["has_segment_skills"] is True
    assert len(bundle["segment_skills"]) == 1
    assert bundle["segment_skills"][0]["sector"] == "software-application"
    assert bundle["industry_skill"]["industry"] == "technology"


def test_build_skill_bundle_without_valid_segments_sets_flag_false():
    bundle = build_skill_bundle(
        industry="technology",
        segments_payload={"segments": [{"sector": "UNKNOWN"}]},
    )

    assert bundle["has_segment_skills"] is False
    assert bundle["segment_skills"] == []


def test_build_skill_bundle_includes_growth_skill_keys():
    """Growth skill keys should always be present in the bundle."""
    bundle = build_skill_bundle(
        industry="technology",
        segments_payload={"segments": []},
    )
    assert "growth_skill" in bundle
    assert "has_growth_skill" in bundle
    assert isinstance(bundle["growth_skill"], dict)


def test_build_skill_bundle_growth_skill_with_yahoo_industry():
    """When yahoo_industry is provided, growth_skill should attempt lookup."""
    bundle = build_skill_bundle(
        industry="technology",
        segments_payload={"segments": []},
        yahoo_industry="Software—Application",
        region="United States",
    )
    assert "growth_skill" in bundle
    assert "has_growth_skill" in bundle
    # Whether data is found depends on ETL output availability,
    # but the keys must always be present and not raise.


def test_build_skill_bundle_backward_compat_without_new_params():
    """Calling without yahoo_industry/region should still work (backward compat)."""
    bundle = build_skill_bundle(
        industry="healthcare",
        segments_payload={"segments": []},
    )
    assert "industry_skill" in bundle
    assert "growth_skill" in bundle
    assert "segment_skills" in bundle


def test_build_skill_bundle_prefers_growth_skill_override_and_normalizes_bands():
    bundle = build_skill_bundle(
        industry="technology",
        segments_payload={"segments": []},
        growth_skill_override={
            "entity": "softwareinternet",
            "entityDisplay": "Software (Internet)",
            "region": "United States",
            "year": 2026,
            "confidenceScore": 0.91,
            "fundamentalGrowth": 12.5,  # percent-like input
            "p10": 5.0,
            "p25": 8.0,
            "p50": 12.0,
            "p75": 16.0,
            "p90": 20.0,
        },
    )

    assert bundle["has_growth_skill"] is True
    growth = bundle["growth_skill"]
    assert growth["entity"] == "softwareinternet"
    assert growth["confidence"] == "HIGH"
    assert growth["fundamental_growth"] == 0.125
    assert growth["revenue_cagr_band"]["p25"] == 0.08
    assert growth["revenue_cagr_band"]["p75"] == 0.16
