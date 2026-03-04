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
