"""Build prompt skill bundles from industry/segment context dictionaries."""
from __future__ import annotations

import json
import logging
import os
import re
from pathlib import Path
from typing import Any, Dict, List, Optional

from domain.knowledge.tool_definitions import (
    INDUSTRY_CONTEXTS,
    NARRATIVE_INDUSTRY_CONTEXTS,
    NARRATIVE_SECTOR_CONTEXTS,
    SECTOR_CONTEXT,
)


logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Growth anchor snapshot loader (ETL-4 output)
# ---------------------------------------------------------------------------

_GROWTH_SNAPSHOTS: Optional[Dict[str, Any]] = None
_GROWTH_FEATURES: Optional[List[Dict[str, Any]]] = None


def _load_growth_snapshots() -> Dict[str, Any]:
    """Lazy-load the combined growth skill snapshots from ETL-4."""
    global _GROWTH_SNAPSHOTS
    if _GROWTH_SNAPSHOTS is not None:
        return _GROWTH_SNAPSHOTS

    candidates = [
        os.getenv("GROWTH_SNAPSHOTS_PATH", ""),
        str(Path(__file__).resolve().parents[3] / ".etl" / "damodaran" / "2026" / "canonical" / "growth_skill_snapshots_combined.json"),
    ]
    for path_str in candidates:
        if not path_str:
            continue
        p = Path(path_str)
        if p.exists():
            try:
                _GROWTH_SNAPSHOTS = json.loads(p.read_text(encoding="utf-8"))
                logger.info("[growth_skill] Loaded %d snapshot cards from %s", len(_GROWTH_SNAPSHOTS), p)
                return _GROWTH_SNAPSHOTS
            except Exception as exc:
                logger.warning("[growth_skill] Failed to load snapshots from %s: %s", p, exc)

    _GROWTH_SNAPSHOTS = {}
    logger.info("[growth_skill] No growth snapshot file found; growth_skill will be empty.")
    return _GROWTH_SNAPSHOTS


def _load_growth_features() -> List[Dict[str, Any]]:
    """Lazy-load the features table for entity lookup."""
    global _GROWTH_FEATURES
    if _GROWTH_FEATURES is not None:
        return _GROWTH_FEATURES

    candidates = [
        os.getenv("GROWTH_FEATURES_PATH", ""),
        str(Path(__file__).resolve().parents[3] / ".etl" / "damodaran" / "2026" / "canonical" / "historical_growth_industry_features.json"),
    ]
    for path_str in candidates:
        if not path_str:
            continue
        p = Path(path_str)
        if p.exists():
            try:
                _GROWTH_FEATURES = json.loads(p.read_text(encoding="utf-8"))
                logger.info("[growth_skill] Loaded %d feature rows from %s", len(_GROWTH_FEATURES), p)
                return _GROWTH_FEATURES
            except Exception as exc:
                logger.warning("[growth_skill] Failed to load features from %s: %s", p, exc)

    _GROWTH_FEATURES = []
    return _GROWTH_FEATURES


def _slugify(text: str) -> str:
    s = re.sub(r"[^a-z0-9]+", "_", text.strip().lower())
    return s.strip("_")


def _lookup_growth_skill(entity: str, region: str = "", year: int = 2026) -> Dict[str, Any]:
    """Look up the best-fit growth skill card for an entity+region."""
    snapshots = _load_growth_snapshots()
    if not snapshots:
        return {}

    entity_slug = _slugify(entity) if entity else ""
    if not entity_slug:
        return {}

    # Try exact industry card first
    region_slug = _slugify(region) if region else ""
    if region_slug:
        key = f"industry_skill_{year}_{region_slug}_{entity_slug}"
        if key in snapshots:
            return snapshots[key]

    # Try any region
    for r in ["united_states", "europe", "emerging_markets", "global", "japan"]:
        key = f"industry_skill_{year}_{r}_{entity_slug}"
        if key in snapshots:
            return snapshots[key]

    # Try regional card
    if region_slug:
        key = f"regional_skill_{year}_{region_slug}"
        if key in snapshots:
            return snapshots[key]

    # Fall back to global
    key = f"global_skill_{year}"
    return snapshots.get(key, {})


def _lookup_entity_for_yahoo_industry(yahoo_industry: str) -> str:
    """
    Map a Yahoo Finance industry string to a Damodaran entity key.
    Uses the curated industry_mapping from tool_definitions.
    """
    from domain.knowledge.tool_definitions import industry_mapping

    if not yahoo_industry:
        return ""

    yahoo_lower = yahoo_industry.strip().lower()
    yahoo_slug = re.sub(r"[^a-z0-9]+", "-", yahoo_lower).strip("-")

    # First try direct name match against industry_mapping
    for entry in industry_mapping:
        name = entry.get("name", "")
        sector = entry.get("sector", "")
        if name.strip().lower() == yahoo_lower or sector == yahoo_slug:
            # Convert Damodaran name to entity slug
            return re.sub(r"[^a-z0-9]+", "", name.lower())

    # Fallback: try slugifying the yahoo industry directly
    return re.sub(r"[^a-z0-9]+", "", yahoo_lower)


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

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


def _normalize_ratio(value: Any) -> float | None:
    """Normalize percent-like values to ratio form (0.12 == 12%)."""
    parsed = _safe_float(value)
    if parsed is None:
        return None
    if abs(parsed) > 1.0:
        parsed = parsed / 100.0
    return parsed


def _first_non_null(data: Dict[str, Any], *keys: str) -> Any:
    for key in keys:
        if key in data and data.get(key) is not None:
            return data.get(key)
    return None


def _confidence_tier(score: float | None) -> str:
    if score is None:
        return "LOW"
    if score >= 0.8:
        return "HIGH"
    if score >= 0.5:
        return "MEDIUM"
    return "LOW"


def _standardize_growth_skill(raw: Dict[str, Any]) -> Dict[str, Any]:
    if not isinstance(raw, dict) or not raw:
        return {}

    historical = raw.get("historical_growth_proxy") if isinstance(raw.get("historical_growth_proxy"), dict) else {}
    expected = raw.get("expected_growth_proxy") if isinstance(raw.get("expected_growth_proxy"), dict) else {}
    revenue_band_raw = raw.get("revenue_cagr_band") if isinstance(raw.get("revenue_cagr_band"), dict) else {}

    p10 = _normalize_ratio(_first_non_null(raw, "p10", "P10", "historical_p10", "historicalP10", "revenueCagrP10"))
    p25 = _normalize_ratio(
        _first_non_null(
            revenue_band_raw,
            "p25",
            "P25",
        )
        if revenue_band_raw
        else _first_non_null(raw, "p25", "P25", "revenueCagrP25")
    )
    p50 = _normalize_ratio(
        _first_non_null(
            revenue_band_raw,
            "p50",
            "P50",
        )
        if revenue_band_raw
        else _first_non_null(raw, "p50", "P50", "revenueCagrP50")
    )
    p75 = _normalize_ratio(
        _first_non_null(
            revenue_band_raw,
            "p75",
            "P75",
        )
        if revenue_band_raw
        else _first_non_null(raw, "p75", "P75", "revenueCagrP75")
    )
    p90 = _normalize_ratio(_first_non_null(raw, "p90", "P90", "historical_p90", "historicalP90", "revenueCagrP90"))

    # Fall back to nested proxy bands when explicit p-values are unavailable.
    p25 = p25 if p25 is not None else _normalize_ratio(_first_non_null(historical, "p25", "P25"))
    p50 = p50 if p50 is not None else _normalize_ratio(_first_non_null(historical, "p50", "P50"))
    p75 = p75 if p75 is not None else _normalize_ratio(_first_non_null(historical, "p75", "P75"))

    expected_p25 = _normalize_ratio(_first_non_null(expected, "p25", "P25"))
    expected_p50 = _normalize_ratio(_first_non_null(expected, "p50", "P50"))
    expected_p75 = _normalize_ratio(_first_non_null(expected, "p75", "P75"))

    confidence_score = _safe_float(_first_non_null(raw, "confidence_score", "confidenceScore"))
    confidence = str(_first_non_null(raw, "confidence", "confidenceTier") or "").strip().upper()
    if confidence not in {"HIGH", "MEDIUM", "LOW"}:
        confidence = _confidence_tier(confidence_score)

    entity = str(_first_non_null(raw, "entity", "damodaranEntity", "entityDisplay") or "").strip()
    if not entity:
        return {}

    result = {
        "type": str(_first_non_null(raw, "type") or "industry"),
        "year": _first_non_null(raw, "year", "dataYear"),
        "region": str(_first_non_null(raw, "region") or "").strip(),
        "entity": entity,
        "entity_display": str(_first_non_null(raw, "entity_display", "entityDisplay", "entity") or entity).strip(),
        "fundamental_growth": _normalize_ratio(
            _first_non_null(raw, "fundamental_growth", "fundamentalGrowth")
        ),
        "historical_growth_proxy": {
            "p25": p25,
            "p50": p50,
            "p75": p75,
        },
        "expected_growth_proxy": {
            "p25": expected_p25 if expected_p25 is not None else p25,
            "p50": expected_p50 if expected_p50 is not None else p50,
            "p75": expected_p75 if expected_p75 is not None else p75,
        },
        "revenue_cagr_band": {
            "p10": p10,
            "p25": p25,
            "p50": p50,
            "p75": p75,
            "p90": p90,
        },
        "terminal_growth_band": {
            "p25": expected_p25 if expected_p25 is not None else p25,
            "p50": expected_p50 if expected_p50 is not None else p50,
            "p75": expected_p75 if expected_p75 is not None else p75,
        },
        "confidence_score": confidence_score,
        "confidence": confidence,
        "number_of_firms": _safe_float(_first_non_null(raw, "number_of_firms", "numberOfFirms")),
        "source": str(_first_non_null(raw, "source") or "historical_growth_snapshot"),
        "warnings": raw.get("warnings") if isinstance(raw.get("warnings"), list) else [],
    }
    return result


# ---------------------------------------------------------------------------
# Main bundle builder
# ---------------------------------------------------------------------------

def build_skill_bundle(
    industry: str,
    segments_payload: Dict[str, Any],
    yahoo_industry: str = "",
    region: str = "",
    growth_skill_override: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    """
    Build a provider-neutral skill bundle for analyzer/analyst prompts.
    Now includes `growth_skill` from historical growth anchor data.
    """
    industry_key = _normalize_key(industry) or "default"
    narrative_industry = NARRATIVE_INDUSTRY_CONTEXTS.get(
        industry_key,
        NARRATIVE_INDUSTRY_CONTEXTS["default"],
    )

    # Resolve growth skill from valuation-service override first, then fallback lookup.
    growth_skill = _standardize_growth_skill(growth_skill_override or {})
    if not growth_skill:
        entity_for_growth = _lookup_entity_for_yahoo_industry(yahoo_industry or industry)
        growth_skill = _standardize_growth_skill(_lookup_growth_skill(entity_for_growth, region))
    has_growth_skill = bool(growth_skill and growth_skill.get("entity"))

    bundle: Dict[str, Any] = {
        "industry_skill": {
            "industry": industry_key,
            "valuation_context": INDUSTRY_CONTEXTS.get(industry_key, INDUSTRY_CONTEXTS["default"]).strip(),
            "narrative_context": str(narrative_industry.get("context", "")).strip(),
            "sensitivity_focus": str(narrative_industry.get("sensitivity_focus", "")).strip(),
        },
        "growth_skill": growth_skill,
        "has_growth_skill": has_growth_skill,
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
