"""Build prompt skill bundles from industry/segment context dictionaries."""
from __future__ import annotations

from typing import Any, Dict, List

from domain.knowledge.tool_definitions import (
    INDUSTRY_CONTEXTS,
    NARRATIVE_INDUSTRY_CONTEXTS,
    NARRATIVE_SECTOR_CONTEXTS,
    SECTOR_CONTEXT,
)


def _normalize_key(value: Any) -> str:
    if value is None:
        return ""
    return str(value).strip().lower()


def _safe_float(value: Any) -> float | None:
    try:
        if value is None:
            return None
        return float(value)
    except (TypeError, ValueError):
        return None


def build_skill_bundle(industry: str, segments_payload: Dict[str, Any]) -> Dict[str, Any]:
    """
    Build a provider-neutral skill bundle for analyzer/analyst prompts.
    Segment skills are included only when valid segments are available.
    """
    industry_key = _normalize_key(industry) or "default"
    narrative_industry = NARRATIVE_INDUSTRY_CONTEXTS.get(
        industry_key,
        NARRATIVE_INDUSTRY_CONTEXTS["default"],
    )

    bundle: Dict[str, Any] = {
        "industry_skill": {
            "industry": industry_key,
            "valuation_context": INDUSTRY_CONTEXTS.get(industry_key, INDUSTRY_CONTEXTS["default"]).strip(),
            "narrative_context": str(narrative_industry.get("context", "")).strip(),
            "sensitivity_focus": str(narrative_industry.get("sensitivity_focus", "")).strip(),
        },
        "segment_skills": [],
        "has_segment_skills": False,
    }

    segments = (segments_payload or {}).get("segments", [])
    if not isinstance(segments, list):
        return bundle

    deduped: Dict[str, Dict[str, Any]] = {}
    for item in segments:
        if not isinstance(item, dict):
            continue

        sector = str(item.get("sector", "")).strip()
        sector_key = _normalize_key(sector)
        if not sector_key or sector_key == "unknown":
            continue

        share = _safe_float(item.get("revenue_share"))
        score = _safe_float(item.get("mapping_score"))
        if share is not None and share <= 0:
            continue
        if score is not None and score <= 0:
            continue

        seg_industry_key = _normalize_key(item.get("industry")) or industry_key
        narrative_sector = NARRATIVE_SECTOR_CONTEXTS.get(
            sector_key,
            NARRATIVE_SECTOR_CONTEXTS["default"],
        )

        row = {
            "sector": sector,
            "industry": seg_industry_key,
            "revenue_share": share,
            "mapping_score": score,
            "components": item.get("components") if isinstance(item.get("components"), list) else [],
            "valuation_context": SECTOR_CONTEXT.get(sector_key, SECTOR_CONTEXT["default"]).strip(),
            "narrative_context": str(narrative_sector.get("context", "")).strip(),
            "sensitivity_focus": str(narrative_sector.get("sensitivity_focus", "")).strip(),
        }

        existing = deduped.get(sector_key)
        if not existing:
            deduped[sector_key] = row
            continue
        existing_share = existing.get("revenue_share")
        if (share or 0.0) > (existing_share or 0.0):
            deduped[sector_key] = row

    segment_skills: List[Dict[str, Any]] = sorted(
        deduped.values(),
        key=lambda row: (row.get("revenue_share") or 0.0, row.get("mapping_score") or 0.0),
        reverse=True,
    )[:5]

    bundle["segment_skills"] = segment_skills
    bundle["has_segment_skills"] = len(segment_skills) > 0
    return bundle

