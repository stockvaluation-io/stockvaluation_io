"""Source provenance validation for researched valuation inputs."""

from __future__ import annotations

from datetime import date
from typing import Any

from .security import sanitize_for_agent

SOURCE_CLASSES = {"primary_filing", "yahoo_normalized", "company_ir", "agent_researched"}
RETRIEVAL_STATUSES = {"retrieved", "missing", "unavailable", "fallback", "not_checked"}
NON_US_YAHOO_CROSS_CHECK_STATUSES = {
    "company_report_check_pending",
    "company_report_cross_checked",
    "company_report_unavailable",
}
RESEARCHED_RUN_MODES = {"full_researched", "autonomous_researched", "researched_baseline"}
STALE_SOURCE_DAYS = 550
SEC_YAHOO_FALLBACK_STATUSES = {
    "sec_missing_user_agent_yahoo_fallback",
    "sec_http_error_yahoo_fallback",
    "sec_rate_limited_yahoo_fallback",
    "sec_cik_not_found_yahoo_fallback",
    "sec_unsupported_filer_yahoo_fallback",
    "sec_unsupported_taxonomy_yahoo_fallback",
    "sec_insufficient_facts_yahoo_fallback",
    "sec_parse_error_yahoo_fallback",
}


def validate_source_provenance_packet(packet: Any) -> dict[str, Any]:
    """Validate compact source provenance metadata for researched valuations."""
    if not isinstance(packet, dict):
        return _result(
            ok=False,
            status="invalid_packet",
            sanitized_packet={},
            core_financials={},
            validation_warnings=["SourceProvenance packet must be a JSON object."],
        )

    sanitized_packet = sanitize_for_agent(packet)
    core = packet.get("core_financials")
    if not isinstance(core, dict):
        return _result(
            ok=False,
            status="invalid_packet",
            sanitized_packet=sanitized_packet,
            core_financials={},
            validation_warnings=["core_financials must be a JSON object."],
        )

    validation_warnings = _required_packet_warnings(packet) + _core_metadata_warnings(core)
    if _is_non_us_yahoo_researched(packet, core) and not str(core.get("cross_check_status") or "").strip():
        validation_warnings.append("core_financials.cross_check_status is required for non-US Yahoo-normalized researched valuations.")
    elif _is_non_us_yahoo_researched(packet, core) and str(core.get("cross_check_status") or "").strip() not in NON_US_YAHOO_CROSS_CHECK_STATUSES:
        validation_warnings.append(
            "core_financials.cross_check_status must be company_report_check_pending, company_report_cross_checked, or company_report_unavailable for non-US Yahoo-normalized researched valuations."
        )
    if _is_non_us_yahoo_researched(packet, core) and str(core.get("retrieval_status") or "").strip() != "retrieved":
        for key in ("source_date", "period_end"):
            if not _is_iso_date(str(core.get(key) or "")):
                validation_warnings.append(
                    f"core_financials.{key} must be YYYY-MM-DD for non-US Yahoo-normalized researched valuations."
                )
    normalized_core = _normalize_core_financials(core)
    policy_warnings: list[str] = []
    status = "valid_source_provenance"
    ok = not validation_warnings
    data_quality_warnings = _reconciliation_warnings(packet.get("material_cross_checks"))

    if ok and _is_us_researched_primary_available_not_used(packet, normalized_core):
        ok = False
        status = "primary_source_available_not_used"
        normalized_core["source_policy_status"] = status
        policy_warnings.append(
            "US researched valuation has primary filing data available; Yahoo-normalized data cannot be treated as the preferred source."
        )
    elif ok and _is_us_researched_fallback(packet, normalized_core):
        status = _phase9_sec_fallback_status(normalized_core)
        normalized_core["source_policy_status"] = status
        policy_warnings.append(
            f"US researched valuation is using Yahoo-normalized financials because SEC primary filing data was unavailable ({status})."
        )
        if _is_stale_source_date(packet, normalized_core):
            policy_warnings.append("Core financial source date is stale relative to the valuation as-of date.")
    elif ok and _is_non_us_yahoo_researched(packet, normalized_core):
        status = "primary_adapter_not_supported_yahoo_normalized"
        normalized_core["source_policy_status"] = status
        policy_warnings.append(
            "Non-US researched valuation may use Yahoo-normalized financials when company-report cross-check status is explicit."
        )
    elif ok and _is_stale_source_date(packet, normalized_core):
        status = "stale_source_date"
        normalized_core["source_policy_status"] = status
        policy_warnings.append("Core financial source date is stale relative to the valuation as-of date.")
    elif ok:
        normalized_core["source_policy_status"] = status

    return _result(
        ok=ok,
        status="invalid_packet" if validation_warnings else status,
        sanitized_packet=sanitized_packet,
        core_financials=normalized_core,
        validation_warnings=validation_warnings,
        policy_warnings=policy_warnings,
        data_quality_warnings=data_quality_warnings,
    )


def _required_packet_warnings(packet: dict[str, Any]) -> list[str]:
    warnings: list[str] = []
    for key in ("ticker", "company", "country", "run_mode"):
        if not str(packet.get(key) or "").strip():
            warnings.append(f"{key} is required.")
    return warnings


def _core_metadata_warnings(core: dict[str, Any]) -> list[str]:
    warnings: list[str] = []
    source_class = str(core.get("source_class") or "").strip()
    retrieval_status = str(core.get("retrieval_status") or "").strip()
    source_policy_status = str(core.get("source_policy_status") or "").strip()
    if source_class not in SOURCE_CLASSES:
        warnings.append("core_financials.source_class must be primary_filing, yahoo_normalized, company_ir, or agent_researched.")
    if source_policy_status == "primary_filing_used" and source_class != "primary_filing":
        warnings.append("core_financials.source_policy_status cannot be primary_filing_used unless source_class is primary_filing.")
    if not str(core.get("provider") or "").strip():
        warnings.append("core_financials.provider is required.")
    if retrieval_status not in RETRIEVAL_STATUSES:
        warnings.append("core_financials.retrieval_status is invalid.")
    if retrieval_status == "retrieved":
        for key in ("source_date", "period_end"):
            if not _is_iso_date(str(core.get(key) or "")):
                warnings.append(f"core_financials.{key} must be YYYY-MM-DD for retrieved sources.")
    return warnings


def _normalize_core_financials(core: dict[str, Any]) -> dict[str, Any]:
    normalized = {
        "source_class": str(core.get("source_class") or "").strip(),
        "provider": str(core.get("provider") or "").strip(),
        "source_date": str(core.get("source_date") or "").strip(),
        "period_end": str(core.get("period_end") or "").strip(),
        "retrieval_status": str(core.get("retrieval_status") or "").strip(),
        "cross_check_status": str(core.get("cross_check_status") or "").strip(),
        "source_policy_status": str(core.get("source_policy_status") or "").strip(),
        "primary_source_expected": bool(core.get("primary_source_expected")),
        "primary_source_available": bool(core.get("primary_source_available")),
    }
    return sanitize_for_agent(normalized)


def _is_us_researched_fallback(packet: dict[str, Any], core: dict[str, Any]) -> bool:
    return (
        str(packet.get("country") or "").strip().lower() == "united states"
        and str(packet.get("run_mode") or "").strip() in RESEARCHED_RUN_MODES
        and core.get("source_class") == "yahoo_normalized"
        and bool(core.get("primary_source_expected"))
        and not bool(core.get("primary_source_available"))
    )


def _phase9_sec_fallback_status(core: dict[str, Any]) -> str:
    status = str(core.get("source_policy_status") or "").strip()
    if status in SEC_YAHOO_FALLBACK_STATUSES:
        return status
    return "sec_missing_user_agent_yahoo_fallback"


def _is_us_researched_primary_available_not_used(packet: dict[str, Any], core: dict[str, Any]) -> bool:
    return (
        str(packet.get("country") or "").strip().lower() == "united states"
        and str(packet.get("run_mode") or "").strip() in RESEARCHED_RUN_MODES
        and core.get("source_class") != "primary_filing"
        and bool(core.get("primary_source_expected"))
        and bool(core.get("primary_source_available"))
    )


def _is_non_us_yahoo_researched(packet: dict[str, Any], core: dict[str, Any]) -> bool:
    country = str(packet.get("country") or "").strip().lower()
    return (
        country != "united states"
        and str(packet.get("run_mode") or "").strip() in RESEARCHED_RUN_MODES
        and core.get("source_class") == "yahoo_normalized"
    )


def _is_iso_date(value: str) -> bool:
    try:
        date.fromisoformat(value)
    except ValueError:
        return False
    return len(value) == 10


def _is_stale_source_date(packet: dict[str, Any], core: dict[str, Any]) -> bool:
    as_of_raw = str(packet.get("as_of_date") or "").strip()
    source_date_raw = str(core.get("source_date") or "").strip()
    if not _is_iso_date(as_of_raw) or not _is_iso_date(source_date_raw):
        return False
    return (date.fromisoformat(as_of_raw) - date.fromisoformat(source_date_raw)).days > STALE_SOURCE_DAYS


def _reconciliation_warnings(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    warnings: list[dict[str, Any]] = []
    for item in value:
        if not isinstance(item, dict):
            continue
        normalized_value = _number_or_none(item.get("normalized_value"))
        filing_value = _number_or_none(item.get("filing_value"))
        threshold = _number_or_none(item.get("threshold_pct"))
        status = str(item.get("status") or "").strip()
        if normalized_value is None or filing_value is None:
            continue
        threshold = threshold if threshold is not None else 0.05
        denominator = max(abs(filing_value), 1.0)
        difference_pct = abs(normalized_value - filing_value) / denominator
        if difference_pct <= threshold:
            continue
        if status in {"definitional_difference", "definitional_difference_lease"}:
            # Known provider-definition difference (e.g. SEC debt includes lease
            # liabilities; normalized reports interest-bearing debt only).
            warnings.append(
                sanitize_for_agent(
                    {
                        "field": str(item.get("field") or "").strip(),
                        "status": "definitional_difference",
                        "normalized_value": normalized_value,
                        "filing_value": filing_value,
                        "difference_pct": round(difference_pct, 4),
                        "threshold_pct": threshold,
                        "source_class": str(item.get("source_class") or "").strip(),
                        "source_date": str(item.get("source_date") or "").strip(),
                    }
                )
            )
            continue
        warnings.append(
            sanitize_for_agent(
                {
                    "field": str(item.get("field") or "").strip(),
                    "status": "material_mismatch",
                    "normalized_value": normalized_value,
                    "filing_value": filing_value,
                    "difference_pct": round(difference_pct, 4),
                    "threshold_pct": threshold,
                    "source_class": str(item.get("source_class") or "").strip(),
                    "source_date": str(item.get("source_date") or "").strip(),
                }
            )
        )
    return warnings


def _number_or_none(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _result(
    *,
    ok: bool,
    status: str,
    sanitized_packet: dict[str, Any],
    core_financials: dict[str, Any],
    validation_warnings: list[str] | None = None,
    policy_warnings: list[str] | None = None,
    data_quality_warnings: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    return {
        "ok": ok,
        "status": status,
        "sanitized_packet": sanitized_packet,
        "core_financials": core_financials,
        "validation_warnings": validation_warnings or [],
        "policy_warnings": policy_warnings or [],
        "data_quality_warnings": data_quality_warnings or [],
    }
