"""StockValuation MCP tool contracts and implementation."""

from __future__ import annotations

import copy
import hashlib
import json
import math
import re
from typing import Any, Callable

from . import __version__
from .accounting_and_claims import (
    accounting_metadata,
    merge_accounting_metadata,
    validate_accounting_override,
)
from .driver_anchors import (
    ANCHOR_FIELD_TO_PROSPECTUS_KEYS,
    NUMERIC_DRIVER_KEYS,
    anchor_values,
    anchors_from_prospectus_packet,
    anchors_from_valuation_baseline,
    driver_field_for_key,
    matches_anchor,
)
from .evidence_packet import validate_evidence_packet
from .guided_question_planner import (
    SEGMENT_DRIVER_TO_ANSWER_FIELD,
    build_guided_question_plan,
    build_user_judgment_package,
)
from .investment_reasoning import (
    build_revealed_thesis,
    framing_forks_input_schema,
    revealed_thesis_output_schema,
)
from .security import sanitize_for_agent
from .segment_discovery import parse_revenue_weight, sanitize_segment_package
from .segment_economics import validate_segment_economics
from .scenario_book import scenario_book_metadata, validate_scenario_book
from .valuation_audit_packet import build_valuation_audit_packet, valuation_audit_packet_metadata
from .workflow_run_state import (
    GATE_EVIDENCE_REVIEW,
    GATE_GUIDED_REFINEMENT,
    WorkflowRunStore,
    validate_gate_record,
)
from .service_client import (
    DEFAULT_SERVICE_URL,
    NonJsonServiceResponse,
    ServiceHTTPError,
    ServiceUnavailable,
    ValuationServiceClient,
    ValuationServiceError,
)

TICKER_RE = re.compile(r"^[A-Z0-9][A-Z0-9.\-]{0,14}$")
SEC_PROSPECTUS_URL_PATTERN = r"^https://www\.sec\.gov/Archives/edgar/data/[0-9]+/[0-9]+/[A-Za-z0-9._-]+\.html?$"
SEC_PROSPECTUS_URL_RE = re.compile(SEC_PROSPECTUS_URL_PATTERN)
MIN_MARGIN_CONVERGENCE_YEAR = 1.0
MAX_MARGIN_CONVERGENCE_YEAR = 10.0
MIN_SALES_TO_CAPITAL = 0.05
MAX_SALES_TO_CAPITAL = 20.0
MIN_TERMINAL_ROIC = 0.01
MAX_TERMINAL_ROIC = 100.0
MIN_TERMINAL_REVENUE_YEAR = 1.0
MAX_TERMINAL_REVENUE_YEAR = 15.0

SUPPORTED_OVERRIDE_FIELDS = {
    "revenue_growth",
    "terminal_revenue",
    "target_revenue",
    "revenue_year_10",
    "year_10_revenue",
    "terminal_revenue_year",
    "target_revenue_year",
    "revenue_target_year",
    "operating_margin_next_year",
    "operating_margin",
    "target_operating_margin",
    "target_pre_tax_operating_margin",
    "margin_convergence_year",
    "convergence_year_margin",
    "sales_to_capital",
    "sales_to_capital_years_1_to_5",
    "sales_to_capital_years_6_to_10",
    "wacc",
    "terminal_growth",
    "terminal_roic",
    "terminal_return_on_capital",
    "terminal_return_on_invested_capital",
    "tax_rate",
    "segments",
    "sector_overrides",
    "segment_economics",
    "growth_pattern_override",
}

REQUEST_POLICY_MODES = {
    "mechanical_baseline",
    "autonomous_researched",
    "user_refined_scenario",
    "explicit_scenario",
    "researched_autonomous",
    "researched_baseline",
}
SOURCE_QUALITY_GATE_BYPASS_STATUSES = {
    "bypassed_by_quick_mode",
    "bypassed_by_no_questions",
    "bypassed_by_smoke_test",
    "bypassed_by_automation",
}
SEGMENT_SERVICE_SECTOR_KEY_FIELDS = (
    "sector_key",
    "sectorKey",
    "yahoo_industry_key",
    "yahooIndustryKey",
    "service_sector_key",
    "serviceSectorKey",
    "service_sector",
    "serviceSector",
)

RECALCULATE_METADATA_FIELDS = {
    "rationale",
    "evidence_used",
    "evidence_packet",
    "request_policy",
    "user_judgment",
    "baseline_plausibility",
    "assumption_judgment",
    "guided_refinement",
}
AUTONOMOUS_RESEARCHED_FIELDS = {"revenue_growth", "operating_margin", "sales_to_capital", "segments", "sector_overrides"}
USER_REFINED_SCENARIO_FIELDS = {
    "net_proceeds",
    "revenue_growth",
    "terminal_revenue",
    "target_revenue",
    "revenue_year_10",
    "year_10_revenue",
    "terminal_revenue_year",
    "target_revenue_year",
    "revenue_target_year",
    "operating_margin_next_year",
    "operating_margin",
    "target_operating_margin",
    "target_pre_tax_operating_margin",
    "margin_convergence_year",
    "convergence_year_margin",
    "sales_to_capital",
    "sales_to_capital_years_1_to_5",
    "sales_to_capital_years_6_to_10",
    "wacc",
    "segments",
    "sector_overrides",
}
REPORT_ONLY_OVERRIDE_FIELDS = {
    "rd_capitalization",
    "r_and_d_capitalization",
    "sbc_dilution",
    "leases",
    "operating_leases",
    "options",
    "warrants",
    "options_warrants",
    "nols",
    "nol_tax",
    "cash",
    "debt",
    "share_count",
    "accounting_adjustments",
}
DIRECT_VALUATION_OUTPUT_FIELDS = {
    "fair_value",
    "fair_value_per_share",
    "target_price",
    "price_target",
    "equity_value",
    "terminal_value",
    "intrinsic_value",
    "intrinsic_value_per_share",
    "estimated_value_per_share",
    "upside",
    "downside",
    "upside_downside",
    "market_price",
    "price_value_gap",
    "direct_market_price_calibration",
}

TOOL_NAMES = [
    "stockvaluation.health",
    "stockvaluation.value_ticker",
    "stockvaluation.researched_baseline",
    "stockvaluation.propose_segment_mappings",
    "stockvaluation.extract_prospectus",
    "stockvaluation.value_prospectus",
    "stockvaluation.plan_guided_questions",
    "stockvaluation.recalculate",
    "stockvaluation.get_assumptions",
    "stockvaluation.get_growth_anchor",
    "stockvaluation.get_reference_data_status",
    "stockvaluation.explain_failure",
]

KNOWN_FAILURE_CATEGORIES = {
    "unsupported_company",
    "insufficient_financial_data",
    "missing_configuration",
    "stale_reference_data",
    "non_json_service_response",
    "missing_local_service",
    "currency_conversion_failed",
    "upstream_service_error",
    "invalid_ticker",
    "unsupported_overrides",
    "invalid_prospectus_source",
    "prospectus_review_required",
    "unknown_failure",
}

SUPPORTED_PROTOCOL_VERSIONS = (
    "2025-11-25",
    "2025-06-18",
    "2025-03-26",
    "2024-11-05",
    "2024-10-07",
)


def _object_schema(properties: dict[str, Any] | None = None, required: list[str] | None = None) -> dict[str, Any]:
    schema: dict[str, Any] = {
        "type": "object",
        "properties": properties or {},
        "additionalProperties": False,
    }
    if required:
        schema["required"] = required
    return schema


def _output_schema() -> dict[str, Any]:
    return {
        "type": "object",
        "properties": {
            "ok": {"type": "boolean"},
            "tool": {"type": "string"},
            "error": {"type": "object"},
            "revealedThesis": revealed_thesis_output_schema(),
        },
        "required": ["ok", "tool"],
        "additionalProperties": True,
    }


def _run_tracking_properties() -> dict[str, Any]:
    return {
        "run_id": {
            "type": "string",
            "description": "Optional workflow run id issued by stockvaluation.extract_prospectus, stockvaluation.researched_baseline, or stockvaluation.value_ticker. When supplied, the server enforces workflow gates for this run.",
        },
        "gate_records": {
            "type": "array",
            "description": "Explicit gate outcome records for a tracked run. Each record: {gate: evidence_review|guided_refinement, outcome: approved|corrected|caveated|applied|bypassed, reason: quick|no_questions|automation|smoke_test (required when outcome is bypassed)}. Bypasses are recorded, never inferred.",
            "items": {"type": "object", "additionalProperties": True},
        },
        "value_sources": {
            "type": "object",
            "description": "On a tracked run, declares the source of a numeric driver value, e.g. {\"target_operating_margin\": \"user_input\"}. A numeric driver value must be one of the run's recorded anchors, or must match a recorded guided answer with source=user_input; otherwise it is refused as unanchored_scenario_value or unverified_user_input.",
            "additionalProperties": True,
        },
    }


def tool_definitions() -> list[dict[str, Any]]:
    ticker_property = {
        "ticker": {
            "type": "string",
            "description": "Public equity ticker symbol, e.g. MSFT. No company names or shell syntax.",
        }
    }
    prospectus_value_input_schema = _object_schema(
        {
            **_run_tracking_properties(),
            "packet": {
                "type": "object",
                "description": "A ProspectusFinancialPacket returned by stockvaluation.extract_prospectus after user review. Prefer review_reference when no packet correction is needed.",
                "additionalProperties": True,
            },
            "review_reference": {
                "type": "string",
                "description": "Preferred after approval: prospectus.reviewReference returned by stockvaluation.extract_prospectus. Also pass review_status=reviewed.",
            },
            "review_status": {
                "type": "string",
                "enum": ["reviewed"],
                "description": "Required when using review_reference. Set to reviewed only after the prospectus extraction packet has been approved or corrected.",
            },
            "packet_overrides": {
                "type": "object",
                "description": "Optional source-backed corrections to merge into the cached packet when using review_reference.",
                "additionalProperties": True,
            },
            "scenario": {
                "type": "object",
                "description": "Optional explicit prospectus scenario assumptions: segment revenue paths, target margins, sales-to-capital, R&D capitalization, net proceeds, terminal growth, terminal cost of capital, and terminal return on capital.",
                "additionalProperties": True,
            },
            "prospectusScenarioCandidate": {
                "type": "object",
                "description": "Optional prospectusScenarioCandidate returned by stockvaluation.apply_guided_answers. When supplied, the tool uses its scenario and source metadata together.",
                "additionalProperties": True,
            },
            "prospectus_scenario_candidate": {
                "type": "object",
                "description": "Snake-case alias for prospectusScenarioCandidate.",
                "additionalProperties": True,
            },
        },
        [],
    )
    return [
        {
            "name": "stockvaluation.health",
            "title": "StockValuation Health",
            "description": "Check whether the local valuation service is reachable.",
            "inputSchema": _object_schema(),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.value_ticker",
            "title": "Value Ticker",
            "description": "Fetch deterministic local DCF JSON for a supported ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.researched_baseline",
            "title": "Researched Baseline",
            "description": "Fetch the default full researched baseline with source policy enabled for a supported ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.propose_segment_mappings",
            "title": "Propose Segment Mappings",
            "description": "Ask the Java valuation service to propose deterministic segment-to-sector mappings for supplied segment rows before a human gate.",
            "inputSchema": _object_schema(
                {
                    "segments": {
                        "type": "array",
                        "description": "Reported segment rows. Each row may include name, revenueAmount or revenueWeight, components, rowRole, tableTitle, and warnings.",
                        "items": {"type": "object", "additionalProperties": True},
                    },
                    "consolidated_revenue": {
                        "type": "number",
                        "description": "Optional consolidated revenue used to derive revenue weights from revenue amounts.",
                    },
                },
                ["segments"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.extract_prospectus",
            "title": "Extract Prospectus",
            "description": "Extract a review-required ProspectusFinancialPacket from a SEC EDGAR HTML prospectus filing URL.",
            "inputSchema": _object_schema(
                {
                    "filing_url": {
                        "type": "string",
                        "pattern": SEC_PROSPECTUS_URL_PATTERN,
                        "description": "SEC EDGAR Archives HTML URL for an S-1, S-1/A, or 424B prospectus.",
                    },
                    "expected_company": {
                        "type": "string",
                        "description": "Optional company name expected by the user for review context.",
                    },
                    "expected_symbol": {
                        "type": "string",
                        "description": "Optional ticker or expected symbol for review context.",
                    },
                },
                ["filing_url"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.value_prospectus",
            "title": "Value Prospectus",
            "description": "Run a local educational valuation from a user-reviewed ProspectusFinancialPacket. Prefer review_reference from stockvaluation.extract_prospectus after review to avoid copying a large packet by hand.",
            "inputSchema": prospectus_value_input_schema,
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.plan_guided_questions",
            "title": "Plan Guided Questions",
            "description": "Build a materiality-ranked story-to-driver guided-question plan from compact valuation context.",
            "inputSchema": _object_schema(
                {
                    **_run_tracking_properties(),
                    "company": {"type": "string"},
                    "ticker": {"type": "string"},
                    "workflow_type": {"type": "string", "enum": ["ticker", "prospectus"]},
                    "baseline_assumptions": {"type": "object", "additionalProperties": True},
                    "baseline_plausibility": {"type": "object", "additionalProperties": True},
                    "evidence_packet": {"type": "object", "additionalProperties": True},
                    "evidence_items": {
                        "type": "array",
                        "description": "Compact driver-specific evidence. Each item should include driver, evidence_summary or fact, source_url/sourceUrl, source_date/sourceDate, and non-low confidence.",
                        "items": {"type": "object", "additionalProperties": True},
                    },
                    "framing_forks": framing_forks_input_schema(),
                    "segments": {"type": "array", "items": {"type": "object", "additionalProperties": True}},
                    "market_implied_diagnostics": {"type": "object", "additionalProperties": True},
                    "prospectus_recalculate_supported": {"type": "boolean"},
                    "deep_mode": {"type": "boolean"},
                    "max_visible_questions": {"type": "integer", "minimum": 0, "maximum": 15},
                }
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.apply_guided_answers",
            "title": "Apply Guided Answers",
            "description": "Convert selected guided-question choices into user_judgment metadata and governed scenario-input candidates.",
            "inputSchema": _object_schema(
                {
                    **_run_tracking_properties(),
                    "guided_question_plan": {
                        "type": "object",
                        "description": "The guidedQuestionPlan returned by stockvaluation.plan_guided_questions. Optional on tracked runs: when run_id is supplied the server uses its stored copy of the plan, so the full plan never needs to be echoed back.",
                        "additionalProperties": True,
                    },
                    "answers": {
                        "type": "object",
                        "description": "Map of question id to selected choice label, such as A, B, C, D, or default. For custom D values, pass an object like {\"choice\":\"D\",\"value\":15.5}; the value is recorded as user_input. For segment-scoped questions, structured custom D is {\"choice\":\"D\",\"value\":[{\"segment\":\"<name>\",\"field\":\"<driver>\",\"value\":12.0}]}; rows stay segment-level and route into scenario.segments.",
                        "additionalProperties": True,
                    },
                    "use_defaults": {
                        "type": "boolean",
                        "description": "When true, accept default choices for unanswered questions.",
                    },
                    "accept_coherence_caveat": {
                        "type": "boolean",
                        "description": "On a tracked run with one unresolved coherence challenge, explicitly accept the caveat and clear guided refinement without changing answers.",
                    },
                    "coherence_caveat_reason": {
                        "type": "string",
                        "description": "Optional user-facing reason recorded when accept_coherence_caveat is true.",
                    },
                },
                [],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.recalculate",
            "title": "Recalculate Valuation",
            "description": "Recalculate local DCF JSON using governed scenario overrides.",
            "inputSchema": _object_schema(
                {
                    **_run_tracking_properties(),
                    **ticker_property,
                    "overrides": {
                        "type": "object",
                        "description": "Supported keys: revenue_growth, terminal_revenue, terminal_revenue_year, operating_margin_next_year, operating_margin/target_operating_margin, margin_convergence_year, sales_to_capital, sales_to_capital_years_1_to_5, sales_to_capital_years_6_to_10, segments, sector_overrides, segment_economics, rd_capitalization (automatic in autonomous_researched when source-backed; explicit governed scenario also supported), leases (report-only AccountingAndClaims status), wacc, terminal_growth, terminal_roic (explicit scenario only), tax_rate, growth_pattern_override, request_policy, rationale, evidence_used, evidence_packet, user_judgment, baseline_plausibility, assumption_judgment, guided_refinement.",
                        "additionalProperties": True,
                    },
                },
                ["ticker", "overrides"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": False, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_assumptions",
            "title": "Get Assumptions",
            "description": "Return the assumption transparency slice for a ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_growth_anchor",
            "title": "Get Growth Anchor",
            "description": "Return mapped Damodaran growth-anchor context for a ticker.",
            "inputSchema": _object_schema(ticker_property, ["ticker"]),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.get_reference_data_status",
            "title": "Get Reference Data Status",
            "description": "Return service and reference-data status used for reproducibility notes.",
            "inputSchema": _object_schema({**ticker_property}, []),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
        {
            "name": "stockvaluation.explain_failure",
            "title": "Explain Failure",
            "description": "Classify an MCP or valuation-service failure into an agent-readable recovery path.",
            "inputSchema": _object_schema(
                {
                    "error": {
                        "description": "Error string or structured error object from another StockValuation tool.",
                    }
                },
                ["error"],
            ),
            "outputSchema": _output_schema(),
            "annotations": {"readOnlyHint": True, "destructiveHint": False, "openWorldHint": False},
        },
    ]


class MCPToolRegistry:
    """Callable registry for StockValuation MCP tools."""

    def __init__(
        self,
        service_client: Any | None = None,
        home: Any | None = None,
        run_store: WorkflowRunStore | None = None,
    ):
        self.service_client = service_client or ValuationServiceClient()
        self._home = home
        self.run_store = run_store or WorkflowRunStore(home=home)
        self._prospectus_packet_cache: dict[str, dict[str, Any]] = {}
        self._handlers: dict[str, Callable[[dict[str, Any]], dict[str, Any]]] = {
            "stockvaluation.health": self._health,
            "stockvaluation.value_ticker": self._value_ticker,
            "stockvaluation.researched_baseline": self._researched_baseline,
            "stockvaluation.propose_segment_mappings": self._propose_segment_mappings,
            "stockvaluation.extract_prospectus": self._extract_prospectus,
            "stockvaluation.value_prospectus": self._value_prospectus,
            "stockvaluation.plan_guided_questions": self._plan_guided_questions,
            "stockvaluation.apply_guided_answers": self._apply_guided_answers,
            "stockvaluation.recalculate": self._recalculate,
            "stockvaluation.get_assumptions": self._get_assumptions,
            "stockvaluation.get_growth_anchor": self._get_growth_anchor,
            "stockvaluation.get_reference_data_status": self._get_reference_data_status,
            "stockvaluation.explain_failure": self._explain_failure,
        }

    def list_tools(self) -> list[dict[str, Any]]:
        return tool_definitions()

    def call(self, name: str, arguments: dict[str, Any] | None = None) -> dict[str, Any]:
        args = arguments or {}
        handler = self._handlers.get(name)
        if handler is None:
            # Some MCP clients (e.g. mcporter) send the bare tool name while the
            # server advertises namespaced tools (stockvaluation.<tool>).
            handler = self._handlers.get(f"stockvaluation.{name}")
        if handler is None:
            content = error_payload(name, "UNKNOWN_TOOL", "Unknown StockValuation tool.", "unknown_tool")
            return tool_result(content, is_error=True)
        content = handler(args)
        return tool_result(content, is_error=not bool(content.get("ok")))

    def _start_tracked_run(
        self,
        payload: dict[str, Any],
        *,
        workflow_type: str,
        subject: str | None,
        tool: str,
        anchors: dict[str, Any] | None = None,
    ) -> None:
        if not payload.get("ok"):
            return
        try:
            run = self.run_store.create_run(workflow_type=workflow_type, subject=subject)
            if anchors:
                run["anchors"] = sanitize_for_agent(anchors)
                self.run_store.update_run(run)
            self.run_store.record_event(run["run_id"], "tool_call", {"tool": tool})
            run = self.run_store.get_run(run["run_id"]) or run
        except OSError:
            return
        payload["run_id"] = run["run_id"]
        payload["workflow_state"] = self.run_store.workflow_state(run)
        return run

    def _resolve_run(self, args: dict[str, Any], tool: str) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
        run_id = _string_or_none(args.get("run_id") or args.get("runId"))
        if run_id is None:
            return None, None
        run = self.run_store.get_run(run_id)
        if run is None:
            return None, error_payload(
                tool,
                "UNKNOWN_RUN_ID",
                "run_id was not found or has expired. Start a new run from the baseline or extraction tool.",
                "unknown_run_id",
                extra={"run_id": run_id},
            )
        return run, None

    def _apply_gate_records(self, run: dict[str, Any], args: dict[str, Any], tool: str) -> dict[str, Any] | None:
        records = args.get("gate_records") or args.get("gateRecords")
        if records is None:
            return None
        if not isinstance(records, list):
            records = [records]
        for record in records:
            problem = validate_gate_record(record)
            if problem:
                return error_payload(
                    tool,
                    "INVALID_GATE_RECORD",
                    f"Invalid gate record: {problem}.",
                    "invalid_gate_record",
                    extra={"gate_record": record if isinstance(record, dict) else None},
                )
            self.run_store.record_gate(
                run["run_id"],
                record["gate"],
                record["outcome"],
                record.get("reason"),
            )
        refreshed = self.run_store.get_run(run["run_id"])
        if refreshed is not None:
            run.clear()
            run.update(refreshed)
        return None

    def _gate_refusal(self, tool: str, gate: str, run: dict[str, Any]) -> dict[str, Any]:
        return error_payload(
            tool,
            "GATE_NOT_CLEARED",
            f"The {gate} gate has not been cleared for this tracked run. Record the gate outcome (or an explicit bypass) before this call.",
            "gate_not_cleared",
            extra={"gate": gate, "run_id": run.get("run_id"), "workflow_state": self.run_store.workflow_state(run)},
        )

    def _finish_tracked(self, payload: dict[str, Any], run: dict[str, Any] | None, tool: str) -> dict[str, Any]:
        if run is None:
            payload.setdefault("gate_enforcement", "untracked")
            return payload
        self.run_store.record_event(run["run_id"], "tool_call", {"tool": tool, "ok": bool(payload.get("ok"))})
        refreshed = self.run_store.get_run(run["run_id"]) or run
        payload["run_id"] = run["run_id"]
        payload["workflow_state"] = self.run_store.workflow_state(refreshed)
        return payload

    def _validate_anchored_values(
        self,
        run: dict[str, Any],
        values: Any,
        args: dict[str, Any],
        tool: str,
    ) -> dict[str, Any] | None:
        """Refuse recognized numeric driver values that are neither recorded
        anchors nor explicitly flagged as user input."""
        if not isinstance(values, dict):
            return None
        anchors = run.get("anchors") or {}
        sources = value_sources_from_args(args)
        for key, value in values.items():
            if key not in NUMERIC_DRIVER_KEYS:
                continue
            if not isinstance(value, (int, float)) or isinstance(value, bool):
                continue
            field = driver_field_for_key(key) or key
            declared = str(sources.get(key) or sources.get(field) or "").lower()
            if self._matches_recorded_user_input(run, field, value):
                continue
            if declared == "user_input":
                return error_payload(
                    tool,
                    "UNVERIFIED_USER_INPUT",
                    f"The value for {key} is marked value_source=user_input, but this tracked run has no matching user-entered guided answer for driver {field}.",
                    "unverified_user_input",
                    extra={
                        "driver": field,
                        "scenario_key": key,
                        "run_id": run.get("run_id"),
                        "workflow_state": self.run_store.workflow_state(run),
                    },
                )
            if not anchors:
                continue
            anchor_set = anchors.get(field)
            if anchor_set and matches_anchor(anchor_set, value):
                continue
            return error_payload(
                tool,
                "UNANCHORED_SCENARIO_VALUE",
                f"The value for {key} is not one of the recorded anchors for driver {field} and is not flagged "
                "value_source=user_input. Defaults must come from server-computed anchors; only the user may "
                "supply a different number.",
                "unanchored_scenario_value",
                extra={
                    "driver": field,
                    "scenario_key": key,
                    "run_id": run.get("run_id"),
                    "anchor_set": anchor_set,
                    "workflow_state": self.run_store.workflow_state(run),
                },
            )
        return None

    @staticmethod
    def _matches_recorded_user_input(run: dict[str, Any], field: str, value: float) -> bool:
        record = _dict(_dict(run.get("guided_answers")).get(field))
        if _string_or_none(record.get("source")) != "user_input":
            return False
        recorded_value = _number_or_none(record.get("value"))
        return recorded_value is not None and math.isclose(recorded_value, value, rel_tol=0.0, abs_tol=0.005)

    def _unresolved_anchor_fields(self, run: dict[str, Any], pinned_values: Any) -> list[str]:
        anchors = run.get("anchors") or {}
        answers = run.get("guided_answers") or {}
        # Before a guided plan exists every multi-valued anchored driver is
        # treated as material; after planning, only fields the plan asked about.
        material = run.get("material_anchor_fields")
        if isinstance(material, list):
            fields = [field for field in sorted(anchors) if field in set(material)]
        else:
            fields = sorted(anchors)
        pinned: set[str] = set()
        if isinstance(pinned_values, dict):
            for key, value in pinned_values.items():
                if isinstance(value, (int, float)) and not isinstance(value, bool):
                    pinned.add(driver_field_for_key(key) or key)
        unresolved = []
        for field in fields:
            values = anchor_values(anchors[field])
            if len(set(values.values())) <= 1:
                continue
            if field in answers or field in pinned:
                continue
            unresolved.append(field)
        return unresolved

    def _health(self, _: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.health"
        try:
            health = self.service_client.health()
            return {
                "ok": True,
                "tool": tool,
                "service": {
                    "name": "stockvaluation-service",
                    "status": health.get("status", "unknown"),
                    "raw": sanitize_for_agent(health),
                },
                "mcp": mcp_metadata(),
                "policy": policy_metadata(),
                "skill": skill_metadata(self._home),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc)

    def _value_ticker(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.value_ticker"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            payload = valuation_success_payload(tool, ticker, valuation)
            run = self._start_tracked_run(
                payload,
                workflow_type="ticker",
                subject=ticker,
                tool=tool,
                anchors=anchors_from_valuation_baseline(valuation),
            )
            if run is not None:
                try:
                    run["valuation_snapshot"] = sanitize_for_agent(
                        {
                            "valuation": valuation,
                            "assumptionTransparency": (
                                valuation.get("assumptionTransparency")
                                if isinstance(valuation, dict)
                                else None
                            ),
                            "provenance": extract_source_provenance(valuation),
                            "segments": (
                                (valuation.get("financialDTO") or {}).get("revenuesBySector")
                                if isinstance(valuation, dict)
                                else None
                            ),
                        }
                    )
                    self.run_store.update_run(run)
                except OSError:
                    pass
            return payload
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _researched_baseline(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.researched_baseline"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(
                ticker,
                {"researchedBaselineMode": True, "requestPolicyMode": "researched_baseline"},
            )
            payload = valuation_success_payload(
                tool,
                ticker,
                valuation,
                {"researchedBaselineMode": True, "requestPolicyMode": "researched_baseline"},
            )
            run = self._start_tracked_run(
                payload,
                workflow_type="ticker",
                subject=ticker,
                tool=tool,
                anchors=anchors_from_valuation_baseline(valuation),
            )
            if run is not None:
                try:
                    run["valuation_snapshot"] = sanitize_for_agent(
                        {
                            "valuation": valuation,
                            "assumptionTransparency": (
                                valuation.get("assumptionTransparency")
                                if isinstance(valuation, dict)
                                else None
                            ),
                            "provenance": extract_source_provenance(valuation),
                            "segments": (
                                (valuation.get("financialDTO") or {}).get("revenuesBySector")
                                if isinstance(valuation, dict)
                                else None
                            ),
                        }
                    )
                    self.run_store.update_run(run)
                except OSError:
                    pass
            return payload
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _propose_segment_mappings(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.propose_segment_mappings"
        segments = args.get("segments")
        if not isinstance(segments, list) or not segments:
            return error_payload(
                tool,
                "INVALID_SEGMENT_ROWS",
                "segments must be a non-empty list.",
                "invalid_segments",
            )
        if not all(isinstance(segment, dict) for segment in segments):
            return error_payload(
                tool,
                "INVALID_SEGMENT_ROWS",
                "each segment row must be a JSON object.",
                "invalid_segments",
            )
        consolidated_revenue = _number_or_none(
            _first_present(args.get("consolidated_revenue"), args.get("consolidatedRevenue"))
        )
        try:
            result = self.service_client.propose_segment_mappings(
                sanitize_for_agent(segments),
                consolidated_revenue,
            )
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc)
        return {
            "ok": True,
            "tool": tool,
            "segmentReview": segment_mapping_proposal_review(result),
            "proposalResult": sanitize_for_agent(result),
            "policy": policy_metadata(),
        }

    def _extract_prospectus(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.extract_prospectus"
        filing_url, error = normalize_prospectus_url(args.get("filing_url") or args.get("filingUrl"))
        if error:
            return error_payload(tool, "INVALID_PROSPECTUS_URL", error, "invalid_prospectus_source")
        expected_company = _string_or_none(args.get("expected_company") or args.get("expectedCompany"))
        expected_symbol = _string_or_none(args.get("expected_symbol") or args.get("expectedSymbol"))
        try:
            result = self.service_client.extract_prospectus(filing_url, expected_company, expected_symbol)
            packet = _dict(result.get("packet"))
            review_reference = prospectus_review_token(packet)
            if review_reference:
                self._prospectus_packet_cache[review_reference] = copy.deepcopy(packet)
            payload = prospectus_extraction_success_payload(tool, result, review_reference=review_reference)
            anchors = anchors_from_prospectus_packet(packet)
            service_anchors = _dict(result.get("driverAnchors") or result.get("driver_anchors"))
            if service_anchors:
                anchors.update(service_anchors)
            self._start_tracked_run(
                payload,
                workflow_type="prospectus",
                subject=filing_url,
                tool=tool,
                anchors=anchors,
            )
            return payload
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc)

    def _value_prospectus(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.value_prospectus"
        run, run_error = self._resolve_run(args, tool)
        if run_error is not None:
            return run_error
        if run is not None:
            record_error = self._apply_gate_records(run, args, tool)
            if record_error is not None:
                return record_error
        packet, packet_error = self._prospectus_packet_from_args(args)
        if packet_error is not None:
            return self._finish_tracked(packet_error, run, tool)
        if not isinstance(packet, dict):
            return self._finish_tracked(
                error_payload(
                    tool,
                    "INVALID_PROSPECTUS_PACKET",
                    "packet must be a ProspectusFinancialPacket returned by stockvaluation.extract_prospectus, or use review_reference with review_status=reviewed.",
                    "prospectus_review_required",
                ),
                run,
                tool,
            )
        review_status = prospectus_review_status(packet)
        if review_status != "reviewed":
            return self._finish_tracked(
                error_payload(
                    tool,
                    "PROSPECTUS_REVIEW_REQUIRED",
                    "ProspectusFinancialPacket reviewStatus must be reviewed before valuation.",
                    "prospectus_review_required",
                    extra={"prospectus": {"reviewStatus": review_status or "missing"}},
                ),
                run,
                tool,
            )
        try:
            scenario_candidate = prospectus_scenario_candidate_from_args(args)
            scenario = args.get("scenario")
            if scenario is None and scenario_candidate:
                scenario = scenario_candidate.get("scenario")
            if scenario is not None and not isinstance(scenario, dict):
                return self._finish_tracked(
                    error_payload(
                        tool,
                        "INVALID_PROSPECTUS_SCENARIO",
                        "scenario must be an object when supplied.",
                        "invalid_prospectus_scenario",
                    ),
                    run,
                    tool,
                )
            if run is not None and scenario and not self.run_store.gate_cleared(run, GATE_EVIDENCE_REVIEW):
                return self._gate_refusal(tool, GATE_EVIDENCE_REVIEW, run)
            if run is not None and scenario:
                refusal = self._validate_anchored_values(run, scenario, args, tool)
                if refusal is not None:
                    return self._finish_tracked(refusal, run, tool)
                unresolved = self._unresolved_anchor_fields(run, scenario)
                if unresolved:
                    return self._finish_tracked(
                        self._prospectus_range_payload(tool, run, packet, scenario, unresolved),
                        run,
                        tool,
                    )
            if (
                run is not None
                and not scenario
                and isinstance(run.get("material_anchor_fields"), list)
                and self.run_store.gate_cleared(run, GATE_EVIDENCE_REVIEW)
            ):
                # After a guided plan exists, a scenario-less valuation with
                # unresolved material drivers must not yield a falsely precise
                # fallback point; value the low/high anchor sets instead.
                unresolved = self._unresolved_anchor_fields(run, None)
                if unresolved:
                    return self._finish_tracked(
                        self._prospectus_range_payload(tool, run, packet, {}, unresolved),
                        run,
                        tool,
                    )
            result = self.service_client.value_prospectus(packet, scenario)
            if run is not None:
                anchors = anchors_from_prospectus_packet(packet)
                service_anchors = _dict(result.get("driverAnchors") or result.get("driver_anchors"))
                if service_anchors:
                    anchors.update(service_anchors)
                if anchors:
                    run["anchors"] = sanitize_for_agent(anchors)
                    self.run_store.update_run(run)
            payload = prospectus_valuation_success_payload(tool, result)
            value_sources = value_sources_from_args(args)
            source_metadata = scenario_source_metadata_from_args(args)
            if value_sources:
                payload["scenarioValueSources"] = sanitize_for_agent(value_sources)
            if source_metadata:
                payload["scenarioSourceMetadata"] = sanitize_for_agent(source_metadata)
            return self._finish_tracked(payload, run, tool)
        except ValuationServiceError as exc:
            return self._finish_tracked(service_exception_payload(tool, exc), run, tool)

    def _prospectus_range_payload(
        self,
        tool: str,
        run: dict[str, Any],
        packet: dict[str, Any],
        scenario: dict[str, Any],
        unresolved: list[str],
    ) -> dict[str, Any]:
        anchors = run.get("anchors") or {}
        cases: dict[str, dict[str, Any]] = {}
        for label in ("low", "high"):
            case_scenario = dict(scenario)
            anchor_labels: dict[str, str] = {}
            for field in unresolved:
                value = anchor_values(anchors.get(field) or {}).get(label)
                if value is None:
                    continue
                for key in ANCHOR_FIELD_TO_PROSPECTUS_KEYS.get(field, ()):
                    case_scenario[key] = value
                anchor_labels[field] = label
            result = self.service_client.value_prospectus(packet, case_scenario)
            cases[label] = {
                "value_per_share": _prospectus_value_per_share(result),
                "scenario": sanitize_for_agent(case_scenario),
                "anchor_labels": anchor_labels,
            }
        per_share = [
            case["value_per_share"]
            for case in cases.values()
            if isinstance(case.get("value_per_share"), (int, float))
        ]
        return {
            "ok": True,
            "tool": tool,
            "valuationRange": {
                "status": "unresolved_material_drivers",
                "reason": "Material drivers remain unresolved; a point estimate appears only when all material drivers are pinned.",
                "unresolved_drivers": unresolved,
                "spread_drivers": unresolved,
                "low": cases.get("low"),
                "high": cases.get("high"),
                "value_spread": {"min": min(per_share), "max": max(per_share)} if per_share else None,
            },
            "policy": policy_metadata(),
        }

    def _prospectus_packet_from_args(self, args: dict[str, Any]) -> tuple[dict[str, Any] | None, dict[str, Any] | None]:
        tool = "stockvaluation.value_prospectus"
        review_reference = _string_or_none(
            args.get("review_reference")
            or args.get("reviewReference")
            or args.get("prospectusReviewReference")
            or args.get("review_token")
            or args.get("reviewToken")
            or args.get("prospectusReviewToken")
        )
        packet_arg = args.get("packet")
        if review_reference:
            review_status = _string_or_none(args.get("review_status") or args.get("reviewStatus"))
            if review_status != "reviewed":
                return None, error_payload(
                    tool,
                    "PROSPECTUS_REVIEW_REQUIRED",
                    "review_status must be reviewed when using review_reference.",
                    "prospectus_review_required",
                    extra={"prospectus": {"reviewReference": review_reference, "reviewStatus": review_status or "missing"}},
                )
            cached_packet = self._prospectus_packet_cache.get(review_reference)
            if cached_packet is None:
                return None, error_payload(
                    tool,
                    "UNKNOWN_PROSPECTUS_REVIEW_TOKEN",
                    "review_reference was not found in this MCP session. Call stockvaluation.extract_prospectus again, then approve and retry.",
                    "prospectus_review_required",
                    extra={"prospectus": {"reviewReference": review_reference}},
                )
            packet = copy.deepcopy(cached_packet)
            overrides = args.get("packet_overrides") or args.get("packetOverrides") or args.get("packet_corrections")
            if overrides is not None:
                if not isinstance(overrides, dict):
                    return None, error_payload(
                        tool,
                        "INVALID_PROSPECTUS_PACKET_OVERRIDES",
                        "packet_overrides must be an object when supplied.",
                        "invalid_prospectus_packet",
                    )
                packet = deep_merge_dict(packet, overrides)
            packet["reviewStatus"] = "reviewed"
            return packet, None
        packet = unwrap_prospectus_packet(packet_arg)
        if isinstance(packet, dict):
            return copy.deepcopy(packet), None
        return None, None

    def _plan_guided_questions(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.plan_guided_questions"
        run, run_error = self._resolve_run(args, tool)
        if run_error is not None:
            return run_error
        if run is not None:
            record_error = self._apply_gate_records(run, args, tool)
            if record_error is not None:
                return record_error
        planner_args = dict(args)
        if run is not None:
            # Anchors come from run state (server-computed), never from the model.
            planner_args["driver_anchors"] = run.get("anchors") or {}
            if run.get("workflow_type") == "prospectus":
                # The MCP layer knows prospectus scenario recalculation is
                # supported; do not depend on the agent remembering the flag.
                planner_args.setdefault("prospectus_recalculate_supported", True)
            snapshot = _dict(run.get("valuation_snapshot"))
            if not planner_args.get("evidence_packet") and snapshot:
                # Auto-build a compact evidence packet from the stored baseline so
                # guided planning works without the caller hand-feeding evidence.
                planner_args["evidence_packet"] = _auto_evidence_packet_from_snapshot(
                    _string_or_none(args.get("ticker")), snapshot
                )
        plan = build_guided_question_plan(planner_args)
        if run is not None:
            anchored_fields = sorted(
                {
                    field
                    for question in plan.get("questions") or []
                    if isinstance(question, dict)
                    for field in [_string_or_none(_dict(question.get("anchor_set")).get("field"))]
                    if field
                }
            )
            run["material_anchor_fields"] = anchored_fields
            # The server copy is canonical: apply_guided_answers uses it so a
            # truncated or hand-rebuilt plan echo can never degrade mapping.
            run["guided_plan"] = sanitize_for_agent(plan)
            self.run_store.update_run(run)
            self.run_store.record_event(
                run["run_id"],
                "guided_plan_created",
                {
                    "question_count": len(plan.get("questions") or []),
                    "anchored_fields": anchored_fields,
                },
            )
        payload = {
            "ok": True,
            "tool": tool,
            "guidedQuestionPlan": plan,
            "policy": policy_metadata(),
        }
        return self._finish_tracked(payload, run, tool)


    def _apply_guided_answers(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.apply_guided_answers"
        run, run_error = self._resolve_run(args, tool)
        if run_error is not None:
            return run_error
        if run is not None:
            record_error = self._apply_gate_records(run, args, tool)
            if record_error is not None:
                return record_error
        plan = args.get("guided_question_plan") or args.get("guidedQuestionPlan")
        plan_source = "request"
        stored_plan = run.get("guided_plan") if run is not None else None
        if isinstance(stored_plan, dict) and stored_plan:
            plan = stored_plan
            plan_source = "run_state"
        if not isinstance(plan, dict):
            return error_payload(
                tool,
                "INVALID_GUIDED_QUESTION_PLAN",
                "guided_question_plan must be the guidedQuestionPlan object returned by stockvaluation.plan_guided_questions, or pass run_id for a tracked run so the server uses its stored plan.",
                "invalid_guided_question_plan",
            )
        answers = args.get("answers")
        if answers is not None and not isinstance(answers, (dict, list)):
            return error_payload(
                tool,
                "INVALID_GUIDED_ANSWERS",
                "answers must be an object mapping question ids to selected choices, or a list of answer records.",
                "invalid_guided_answers",
            )
        use_defaults = bool(args.get("use_defaults") or args.get("useDefaults"))
        judgment = build_user_judgment_package(plan, answers, use_defaults=use_defaults)
        accept_caveat = bool(args.get("accept_coherence_caveat") or args.get("acceptCoherenceCaveat"))
        caveat_reason = _string_or_none(args.get("coherence_caveat_reason") or args.get("coherenceCaveatReason"))
        coherence_decision = self._coherence_decision(run, judgment, accept_caveat, caveat_reason, tool)
        if coherence_decision.get("error") is not None:
            return self._finish_tracked(coherence_decision["error"], run, tool)
        revealed_thesis = build_revealed_thesis(plan, judgment, coherence_decision.get("review"))
        guided_answer_record: dict[str, Any] = {}
        if run is not None:
            run_anchors = run.get("anchors") or {}
            segment_answers: dict[str, list[dict[str, Any]]] = {}
            for answer in judgment.get("answers") or []:
                if not isinstance(answer, dict):
                    continue
                # Record only answers that actually mapped into the scenario
                # (user story 18): an unmapped answer must stay unresolved so
                # the range safety net can fire.
                if answer.get("unsupported_or_report_only_reason") is not None:
                    continue
                if (answer.get("model_action") or "") != "user scenario override":
                    continue
                override = _dict(answer.get("requested_override"))
                field = _string_or_none(override.get("field"))
                value = override.get("value")
                if field == "segments" and isinstance(value, list):
                    driver_field = SEGMENT_DRIVER_TO_ANSWER_FIELD.get(_string_or_none(answer.get("mapped_driver")) or "")
                    if driver_field:
                        segment_answers.setdefault(driver_field, []).append(answer)
                    continue
                if not field or not isinstance(value, (int, float)) or isinstance(value, bool):
                    continue
                field = driver_field_for_key(field) or field
                anchor_label = answer.get("anchor_label")
                if anchor_label not in {"low", "base", "high"}:
                    anchor_label = matches_anchor(_dict(run_anchors.get(field)), value)
                guided_answer_record[field] = {
                    "value": value,
                    "source": f"anchor:{anchor_label}" if anchor_label else "user_input",
                    "question_id": answer.get("question_id"),
                    "selected_choice": answer.get("selected_choice"),
                }
                if answer.get("anchor_explanation"):
                    guided_answer_record[field]["anchor_explanation"] = sanitize_for_agent(answer.get("anchor_explanation"))
                if answer.get("anchor_provenance"):
                    guided_answer_record[field]["anchor_provenance"] = answer.get("anchor_provenance")
            self._record_segment_level_answers(guided_answer_record, segment_answers, plan)
            run["guided_answers"] = sanitize_for_agent(guided_answer_record)
            run["revealed_thesis"] = copy.deepcopy(revealed_thesis)
            if coherence_decision.get("persist"):
                run["coherence_review"] = sanitize_for_agent(coherence_decision["review"])
                run["coherence_answer_fingerprint"] = coherence_decision.get("fingerprint")
                if coherence_decision.get("challenge_count") is not None:
                    run["coherence_challenge_count"] = coherence_decision["challenge_count"]
                run["coherence_requires_caveat_decision"] = bool(coherence_decision.get("requires_caveat_decision"))
                if coherence_decision.get("event_type"):
                    run.setdefault("events", []).append(
                        {
                            "type": coherence_decision["event_type"],
                            "at": self.run_store._now(),
                            "coherence_status": coherence_decision["review"].get("status"),
                            "issues": coherence_decision["review"].get("issues", []),
                        }
                    )
            self.run_store.update_run(run)
            if coherence_decision.get("clear_gate", True):
                outcome = "caveated" if coherence_decision.get("caveated") else "applied"
                self.run_store.record_gate(run["run_id"], GATE_GUIDED_REFINEMENT, outcome, caveat_reason)
        payload = {
            "ok": True,
            "tool": tool,
            "userJudgment": judgment,
            "tickerOverridesCandidate": guided_ticker_overrides_candidate(judgment),
            "prospectusScenarioCandidate": guided_prospectus_scenario_candidate(judgment),
            "scenarioRange": sanitize_for_agent(plan.get("scenario_range") or {}),
            "revealedThesis": revealed_thesis,
            "policy": policy_metadata(),
        }
        if run is not None:
            payload["guidedAnswerRecord"] = guided_answer_record
            payload["planSource"] = plan_source
            if coherence_decision.get("review") is not None:
                payload["coherenceReview"] = coherence_decision["review"]
                payload["challenge_count"] = coherence_decision.get("challenge_count", run.get("coherence_challenge_count", 0))
        return self._finish_tracked(payload, run, tool)

    def _coherence_decision(
        self,
        run: dict[str, Any] | None,
        judgment: dict[str, Any],
        accept_caveat: bool,
        caveat_reason: str | None,
        tool: str,
    ) -> dict[str, Any]:
        review = coherence_review(judgment)
        if run is None:
            return {"review": review}
        challenge_count = int(run.get("coherence_challenge_count") or 0)
        previous_review = _dict(run.get("coherence_review"))
        previous_pending = previous_review.get("status") in {"challenge_required", "awaiting_caveat_acceptance"}
        fingerprint = coherence_answer_fingerprint(judgment)
        previous_fingerprint = _string_or_none(run.get("coherence_answer_fingerprint"))
        requires_decision = bool(run.get("coherence_requires_caveat_decision"))

        if accept_caveat and previous_pending and challenge_count >= 1:
            accepted = dict(previous_review or review)
            accepted["status"] = "caveat_accepted"
            accepted["explicit_caveat"] = {
                "accepted": True,
                "reason": caveat_reason,
            }
            return {
                "review": accepted,
                "fingerprint": fingerprint,
                "persist": True,
                "clear_gate": True,
                "caveated": True,
                "challenge_count": challenge_count,
                "event_type": "coherence_caveat_accepted",
            }

        if requires_decision:
            return {
                "error": error_payload(
                    tool,
                    "COHERENCE_CAVEAT_DECISION_REQUIRED",
                    "The prior changed answer is still materially inconsistent. Accept the coherence caveat explicitly before changing answers again.",
                    "coherence_caveat_decision_required",
                    extra={
                        "coherenceReview": previous_review,
                        "challenge_count": challenge_count,
                    },
                )
            }

        if not review["issues"]:
            review["status"] = "clean" if not previous_pending else "resolved_by_changed_answers"
            return {
                "review": review,
                "fingerprint": fingerprint,
                "persist": True,
                "clear_gate": True,
                "challenge_count": challenge_count,
                "event_type": "coherence_resolved" if previous_pending else "coherence_clean",
            }

        if challenge_count < 1:
            review["status"] = "challenge_required"
            return {
                "review": review,
                "fingerprint": fingerprint,
                "persist": True,
                "clear_gate": False,
                "challenge_count": 1,
                "event_type": "coherence_challenge",
            }

        changed = previous_fingerprint is not None and fingerprint != previous_fingerprint
        review["status"] = "awaiting_caveat_acceptance" if changed else "challenge_required"
        return {
            "review": review,
            "fingerprint": fingerprint,
            "persist": True,
            "clear_gate": False,
            "challenge_count": challenge_count,
            "requires_caveat_decision": changed,
            "event_type": "coherence_changes_still_inconsistent" if changed else None,
        }

    @staticmethod
    def _record_segment_level_answers(
        guided_answer_record: dict[str, Any],
        segment_answers: dict[str, list[dict[str, Any]]],
        plan: dict[str, Any],
    ) -> None:
        """Mark a driver resolved by segment-level answers only when every
        planned segment question for that driver mapped; partial segment
        coverage keeps the driver unresolved so the range safety net fires."""
        planned_counts: dict[str, int] = {}
        for question in plan.get("questions") or []:
            scope = question.get("segment_scope") if isinstance(question, dict) else None
            field = _string_or_none(_dict(scope).get("field")) if isinstance(scope, dict) else None
            if field:
                planned_counts[field] = planned_counts.get(field, 0) + 1
        for field, answers in segment_answers.items():
            if field in guided_answer_record:
                continue
            if len(answers) < planned_counts.get(field, 0):
                continue
            labels = {_string_or_none(answer.get("anchor_label")) for answer in answers}
            fallback_segments = sorted(
                {
                    segment
                    for answer in answers
                    for segment in _string_list(answer.get("fallback_segments"))
                }
            )
            if fallback_segments and labels == {"user_input"}:
                source = "segments:mixed"
            elif labels == {"user_input"}:
                source = "segments:user_input"
            elif len(labels) == 1 and labels & {"low", "base", "high"}:
                source = f"segments:anchor:{labels.pop()}"
            elif fallback_segments:
                source = "segments:mixed"
            else:
                source = "segments:mixed"
            rows: list[Any] = []
            for answer in answers:
                value = _dict(answer.get("requested_override")).get("value")
                if isinstance(value, list):
                    rows.extend(value)
            guided_answer_record[field] = {
                "value": {"segments": rows},
                "source": source,
                "question_ids": [answer.get("question_id") for answer in answers],
                "selected_choices": [answer.get("selected_choice") for answer in answers],
            }
            if fallback_segments:
                guided_answer_record[field]["fallback_segments"] = fallback_segments
            if answers and answers[0].get("anchor_explanation"):
                guided_answer_record[field]["anchor_explanation"] = sanitize_for_agent(answers[0].get("anchor_explanation"))

    def _recalculate(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.recalculate"
        run, run_error = self._resolve_run(args, tool)
        if run_error is not None:
            return run_error
        if run is not None:
            record_error = self._apply_gate_records(run, args, tool)
            if record_error is not None:
                return record_error
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        revealed_thesis = stored_revealed_thesis(run)
        requested = args.get("overrides")
        if run is not None and isinstance(requested, dict):
            scenario_bearing = any(key not in RECALCULATE_METADATA_FIELDS for key in requested)
            if scenario_bearing and not self.run_store.gate_cleared(run, GATE_EVIDENCE_REVIEW):
                return self._gate_refusal(tool, GATE_EVIDENCE_REVIEW, run)
            request_policy_mode, _request_policy_error = normalize_request_policy_mode(requested.get("request_policy"))
            guided_flow = any(key in {"user_judgment", "guided_refinement"} for key in requested) or (
                scenario_bearing and request_policy_mode == "user_refined_scenario"
            )
            if guided_flow and not self.run_store.gate_cleared(run, GATE_GUIDED_REFINEMENT):
                return self._gate_refusal(tool, GATE_GUIDED_REFINEMENT, run)
            refusal = self._validate_anchored_values(run, requested, args, tool)
            if refusal is not None:
                return self._finish_tracked(refusal, run, tool)
            if scenario_bearing:
                unresolved = self._unresolved_anchor_fields(run, requested)
                if unresolved:
                    payload = self._recalculate_range_payload(tool, run, ticker, requested, unresolved)
                    if revealed_thesis:
                        payload["revealedThesis"] = copy.deepcopy(revealed_thesis)
                    if _dict(payload.get("valuationRange")).get("status") == "unresolved_material_drivers":
                        mapped, unsupported, metadata = map_recalculate_overrides(requested)
                        assumption_meta = {
                            "requested": sanitize_for_agent(requested),
                            "mapped": mapped,
                            "unsupported": unsupported,
                            "effective": {},
                        }
                        if metadata:
                            assumption_meta["metadata"] = metadata
                        payload["assumptions"] = assumption_meta
                        apply_request_policy_source_quality_gate(payload, assumption_meta)
                        attach_valuation_audit_packet(
                            payload,
                            ticker=ticker,
                            assumption_meta=assumption_meta,
                            recalculate_status="blocked_pre_service",
                        )
                        attach_scenario_book(
                            payload,
                            ticker=ticker,
                            assumption_meta=assumption_meta,
                            recalculate_status="blocked_pre_service",
                        )
                    return self._finish_tracked(payload, run, tool)
        if not isinstance(requested, dict):
            return error_payload(
                tool,
                "INVALID_OVERRIDES",
                "overrides must be a JSON object.",
                "invalid_overrides",
                extra={"assumptions": {"requested": requested, "mapped": {}, "unsupported": {}, "effective": {}}},
            )

        mapped, unsupported, metadata = map_recalculate_overrides(requested)
        assumption_meta = {
            "requested": sanitize_for_agent(requested),
            "mapped": mapped,
            "unsupported": unsupported,
            "effective": {},
        }
        if metadata:
            assumption_meta["metadata"] = metadata
        if unsupported:
            blocked_baseline = blocked_baseline_contract(unsupported)
            payload = error_payload(
                tool,
                "UNSUPPORTED_OVERRIDES",
                "One or more override fields are not governed by the MCP contract.",
                "unsupported_overrides",
                extra={"ticker": ticker, "assumptions": assumption_meta, "baseline": blocked_baseline},
            )
            if revealed_thesis:
                payload["revealedThesis"] = copy.deepcopy(revealed_thesis)
            apply_request_policy_source_quality_gate(payload, assumption_meta)
            attach_valuation_audit_packet(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="blocked_pre_service",
            )
            attach_scenario_book(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="blocked_pre_service",
            )
            return self._finish_tracked(payload, run, tool)
        try:
            valuation = self.service_client.value_ticker(ticker, mapped)
            assumption_meta["effective"] = effective_assumptions(valuation)
            payload = valuation_success_payload(
                tool,
                ticker,
                valuation,
                {
                    "researchedBaselineMode": mapped.get("researchedBaselineMode"),
                    "requestPolicyMode": mapped.get("requestPolicyMode"),
                },
            )
            if revealed_thesis:
                payload["revealedThesis"] = copy.deepcopy(revealed_thesis)
            payload["assumptions"] = assumption_meta
            apply_request_policy_source_quality_gate(payload, assumption_meta)
            attach_valuation_audit_packet(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="executed",
            )
            attach_scenario_book(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="executed",
            )
            return self._finish_tracked(payload, run, tool)
        except ValuationServiceError as exc:
            payload = service_exception_payload(tool, exc, ticker=ticker)
            if revealed_thesis:
                payload["revealedThesis"] = copy.deepcopy(revealed_thesis)
            payload["assumptions"] = assumption_meta
            apply_request_policy_source_quality_gate(payload, assumption_meta)
            attach_valuation_audit_packet(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="service_error",
            )
            attach_scenario_book(
                payload,
                ticker=ticker,
                assumption_meta=assumption_meta,
                recalculate_status="service_error",
            )
            return self._finish_tracked(payload, run, tool)

    def _recalculate_range_payload(
        self,
        tool: str,
        run: dict[str, Any],
        ticker: str,
        requested: dict[str, Any],
        unresolved: list[str],
    ) -> dict[str, Any]:
        anchors = run.get("anchors") or {}
        cases: dict[str, dict[str, Any]] = {}
        for label in ("low", "high"):
            case_requested = dict(requested)
            anchor_labels: dict[str, str] = {}
            for field in unresolved:
                if field not in SUPPORTED_OVERRIDE_FIELDS:
                    continue
                value = anchor_values(anchors.get(field) or {}).get(label)
                if value is None:
                    continue
                case_requested[field] = value
                anchor_labels[field] = label
            mapped, unsupported, _metadata = map_recalculate_overrides(case_requested)
            if unsupported:
                return error_payload(
                    tool,
                    "UNSUPPORTED_OVERRIDES",
                    "Range valuation for unresolved drivers was blocked by unsupported override fields.",
                    "unsupported_overrides",
                    extra={"ticker": ticker, "assumptions": {"requested": sanitize_for_agent(case_requested), "unsupported": unsupported}},
                )
            try:
                valuation = self.service_client.value_ticker(ticker, mapped)
            except ValuationServiceError as exc:
                return service_exception_payload(tool, exc, ticker=ticker)
            value = _dict(valuation.get("companyDTO")).get("estimatedValuePerShare")
            cases[label] = {
                "value_per_share": float(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else None,
                "overrides": sanitize_for_agent(case_requested),
                "anchor_labels": anchor_labels,
            }
        per_share = [
            case["value_per_share"]
            for case in cases.values()
            if isinstance(case.get("value_per_share"), (int, float))
        ]
        return {
            "ok": True,
            "tool": tool,
            "ticker": ticker,
            "valuationRange": {
                "status": "unresolved_material_drivers",
                "reason": "Material drivers remain unresolved; a point estimate appears only when all material drivers are pinned.",
                "unresolved_drivers": unresolved,
                "spread_drivers": unresolved,
                "low": cases.get("low"),
                "high": cases.get("high"),
                "value_spread": {"min": min(per_share), "max": max(per_share)} if per_share else None,
            },
            "policy": policy_metadata(),
        }

    def _get_assumptions(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_assumptions"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            return {
                "ok": True,
                "tool": tool,
                "ticker": ticker,
                "assumptions": extract_assumptions(valuation),
                "policy": policy_metadata(),
                "version": version_metadata(valuation),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _get_growth_anchor(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_growth_anchor"
        ticker, error = normalize_ticker(args.get("ticker"))
        if error:
            return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
        try:
            valuation = self.service_client.value_ticker(ticker)
            return {
                "ok": True,
                "tool": tool,
                "ticker": ticker,
                "growthAnchor": extract_growth_anchor(valuation),
                "version": version_metadata(valuation),
            }
        except ValuationServiceError as exc:
            return service_exception_payload(tool, exc, ticker=ticker)

    def _get_reference_data_status(self, args: dict[str, Any]) -> dict[str, Any]:
        tool = "stockvaluation.get_reference_data_status"
        ticker_value = args.get("ticker")
        if ticker_value:
            ticker, error = normalize_ticker(ticker_value)
            if error:
                return error_payload(tool, "INVALID_TICKER", error, "invalid_ticker")
            try:
                valuation = self.service_client.value_ticker(ticker)
                return {
                    "ok": True,
                    "tool": tool,
                    "ticker": ticker,
                    "referenceData": reference_data_status(valuation),
                    "version": version_metadata(valuation),
                }
            except ValuationServiceError as exc:
                return service_exception_payload(tool, exc, ticker=ticker)
        health_payload = self._health({})
        return {
            "ok": health_payload.get("ok", False),
            "tool": tool,
            "service": health_payload.get("service"),
            "referenceData": reference_data_status({}),
            "version": {"mcp": mcp_metadata()},
        }

    def _explain_failure(self, args: dict[str, Any]) -> dict[str, Any]:
        return explain_failure(args.get("error"))


COHERENCE_PRIORITY = {
    "risk_double_count": 0,
    "linked_theme_contradiction": 1,
    "growth_reinvestment_mismatch": 2,
    "optimistic_stack": 3,
}


def _auto_evidence_packet_from_snapshot(ticker: str | None, snapshot: dict[str, Any]) -> dict[str, Any]:
    """Build a planner-ready evidence packet from a stored researched baseline.

    Derives driver-specific evidence rows from the deterministic valuation output
    (assumption transparency + provenance), so plan_guided_questions works without
    hand-fed evidence. Source metadata always points at the filing/Yahoo provider
    used by the baseline, not at the user.
    """
    transparency = _dict(snapshot.get("assumptionTransparency"))
    operating = _dict(transparency.get("operatingAssumptions"))
    discount = _dict(transparency.get("discountRate"))
    provenance = _dict(snapshot.get("provenance"))
    def _text_or_empty(value: Any) -> str:
        return value if isinstance(value, str) else ("" if value is None else str(value))

    source_date = _text_or_empty(
        provenance.get("sourceDate")
        or provenance.get("source_date")
        or ""
    )
    source_class = _text_or_empty(
        provenance.get("sourceClass")
        or provenance.get("source_class")
        or "service_baseline"
    )
    source_url = (
        "https://www.sec.gov/"
        if "filing" in source_class.lower()
        else ""
    )
    source_title = (
        "SEC primary filing (company facts)"
        if "filing" in source_class.lower()
        else "Provider-normalized financial data"
    )

    def row(driver: str, summary: str, confidence: str = "high", material: bool = True) -> dict[str, Any]:
        return {
            "driver": driver,
            "evidence_summary": summary,
            "fact": summary,
            "source_url": source_url,
            "source_date": source_date or "unknown",
            "source_title": source_title,
            "confidence": confidence,
            "material": material,
        }

    items = []
    growth = _number_or_none(
        _first_present(
            operating.get("revenueGrowthRateYears2To5"),
            operating.get("revenueGrowthRate"),
        )
    )
    if growth is not None:
        items.append(
            row(
                "revenue_growth",
                f"Baseline revenue growth {growth}% from the researched baseline; runway beyond near-term evidence is the open question.",
            )
        )
    margin = _number_or_none(
        _first_present(
            operating.get("operatingMarginNextYear"),
            operating.get("targetOperatingMargin"),
        )
    )
    if margin is not None:
        items.append(
            row(
                "operating_margin",
                f"Baseline operating margin {margin}% from the researched baseline; margin path to maturity is the open question.",
            )
        )
    s2c = _number_or_none(
        _first_present(
            operating.get("salesToCapitalYears1To5"),
            operating.get("salesToCapital"),
        )
    )
    if s2c is not None:
        items.append(
            row(
                "reinvestment_sales_to_capital",
                f"Baseline sales-to-capital {s2c} from the researched baseline; reinvestment intensity is the open question.",
                confidence="medium",
            )
        )
    coc = _number_or_none(
        _first_present(
            discount.get("initialCostOfCapital"),
            discount.get("costOfCapital"),
        )
    )
    rf = _number_or_none(discount.get("riskFreeRate"))
    if coc is not None:
        detail = f"Cost of capital {coc}%"
        if rf is not None:
            detail += f" with risk-free rate {rf}%"
        detail += " from the researched baseline; discount-rate risk is the open question."
        items.append(row("risk_wacc", detail))
    return {
        "schema_version": "evidence_packet.v1",
        "ticker": ticker or "",
        "source_type": "service_baseline",
        "evidence_items": items,
    }


def coherence_answer_fingerprint(judgment: dict[str, Any]) -> str:
    answers = [
        {
            "question_id": answer.get("question_id"),
            "selected_choice": answer.get("selected_choice"),
            "requested_override": answer.get("requested_override"),
        }
        for answer in _coherence_list(judgment.get("answers"))
        if isinstance(answer, dict)
    ]
    return hashlib.sha256(json.dumps(answers, sort_keys=True, default=str).encode("utf-8")).hexdigest()


def coherence_review(judgment: dict[str, Any]) -> dict[str, Any]:
    answers = [
        answer
        for answer in _coherence_list(judgment.get("answers"))
        if isinstance(answer, dict)
        and answer.get("unsupported_or_report_only_reason") is None
        and (answer.get("model_action") or "") == "user scenario override"
    ]
    issues = []
    issues.extend(_coherence_risk_double_count_issues(answers))
    issues.extend(_coherence_theme_issues(answers))
    mismatch = _coherence_growth_reinvestment_issue(answers)
    if mismatch:
        issues.append(mismatch)
    optimistic = _coherence_optimistic_stack_issue(answers)
    if optimistic:
        issues.append(optimistic)
    issues = sorted(issues, key=lambda issue: (COHERENCE_PRIORITY.get(issue["type"], 99), issue["type"]))
    return {
        "status": "clean" if not issues else "challenge_required",
        "issues": sanitize_for_agent(issues[:1]),
        "issue_count": len(issues),
        "rule": "one_material_challenge_max",
        "favorability": _coherence_favorability_summary(answers),
    }


def _coherence_risk_double_count_issues(answers: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_factor: dict[str, list[dict[str, Any]]] = {}
    for answer in answers:
        factor_id = _string_or_none(answer.get("factor_id"))
        if factor_id:
            by_factor.setdefault(factor_id, []).append(answer)
    issues = []
    for factor_id, group in by_factor.items():
        if len(group) < 2:
            continue
        reasons = {_string_or_none(answer.get("non_overlap_reason")) for answer in group}
        if len(reasons) == 1 and next(iter(reasons)) is not None:
            continue
        issues.append(
            {
                "type": "risk_double_count",
                "factor_id": factor_id,
                "drivers": sorted({_coherence_driver(answer) for answer in group}),
                "message": "The same risk factor appears in multiple assumptions without the same non-empty non-overlap reason.",
            }
        )
    return issues


def _coherence_theme_issues(answers: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_theme: dict[str, list[dict[str, Any]]] = {}
    for answer in answers:
        theme = _string_or_none(answer.get("theme_id"))
        if theme:
            by_theme.setdefault(theme, []).append(answer)
    issues = []
    for theme, group in by_theme.items():
        keys = {_string_or_none(answer.get("scenario_key")) for answer in group}
        keys.discard(None)
        if len(keys) <= 1:
            continue
        issues.append(
            {
                "type": "linked_theme_contradiction",
                "theme_id": theme,
                "scenario_keys": sorted(keys),
                "message": "Linked theme answers are compatible only when they select the same scenario key.",
            }
        )
    return issues


def _coherence_growth_reinvestment_issue(answers: list[dict[str, Any]]) -> dict[str, Any] | None:
    by_field = _coherence_by_field(answers)
    growth = by_field.get("revenue_growth") or by_field.get("terminal_revenue")
    reinvestment = by_field.get("sales_to_capital")
    if not growth or not reinvestment:
        return None
    if _coherence_anchor_label(growth) == "high" and _coherence_anchor_label(reinvestment) == "low":
        return {
            "type": "growth_reinvestment_mismatch",
            "drivers": ["revenue_growth", "sales_to_capital"],
            "message": "High growth paired with the low sales-to-capital anchor needs resolution because growth and reinvestment are linked.",
        }
    return None


def _coherence_optimistic_stack_issue(answers: list[dict[str, Any]]) -> dict[str, Any] | None:
    favorable = [
        answer
        for answer in answers
        if _coherence_favorability(answer) == "favorable"
    ]
    covered = {_coherence_driver(answer) for answer in favorable}
    required = {"revenue_growth", "target_operating_margin", "sales_to_capital", "wacc"}
    if not required <= covered:
        return None
    weak = [
        _coherence_driver(answer)
        for answer in favorable
        if _coherence_driver(answer) in required and not _coherence_high_support(answer)
    ]
    if not weak:
        return None
    return {
        "type": "optimistic_stack",
        "drivers": sorted(required),
        "weak_support_drivers": sorted(set(weak)),
        "message": "Growth, margin, capital efficiency and WACC are all favorable, but at least one selection lacks high support.",
    }


def _coherence_favorability_summary(answers: list[dict[str, Any]]) -> dict[str, str]:
    return {
        _coherence_driver(answer): _coherence_favorability(answer)
        for answer in answers
        if _coherence_favorability(answer) != "neutral"
    }


def _coherence_by_field(answers: list[dict[str, Any]]) -> dict[str, dict[str, Any]]:
    by_field: dict[str, dict[str, Any]] = {}
    for answer in answers:
        by_field.setdefault(_coherence_driver(answer), answer)
    return by_field


def _coherence_driver(answer: dict[str, Any]) -> str:
    override = _dict(answer.get("requested_override"))
    field = _string_or_none(override.get("field"))
    driver = driver_field_for_key(field or "") if field else None
    mapped = _string_or_none(answer.get("mapped_driver"))
    if driver:
        return driver
    if mapped in {"operating_margin", "margin_path"}:
        return "target_operating_margin"
    if mapped == "reinvestment_sales_to_capital":
        return "sales_to_capital"
    if mapped == "risk_wacc":
        return "wacc"
    return mapped or field or "unknown"


def _coherence_anchor_label(answer: dict[str, Any]) -> str | None:
    label = _string_or_none(answer.get("anchor_label"))
    if label in {"low", "base", "high"}:
        return label
    return None


def _coherence_favorability(answer: dict[str, Any]) -> str:
    driver = _coherence_driver(answer)
    label = _coherence_anchor_label(answer)
    if label is None:
        return "neutral"
    if driver in {"revenue_growth", "target_operating_margin", "sales_to_capital"} and label == "high":
        return "favorable"
    if driver == "wacc" and label == "low":
        return "favorable"
    if driver in {"revenue_growth", "target_operating_margin", "sales_to_capital"} and label == "low":
        return "unfavorable"
    if driver == "wacc" and label == "high":
        return "unfavorable"
    return "neutral"


def _coherence_high_support(answer: dict[str, Any]) -> bool:
    refs: list[dict[str, Any]] = []
    for item in _coherence_list(answer.get("evidence_used")):
        if isinstance(item, dict):
            refs.append(item)
    for item in _coherence_list(answer.get("supporting_evidence_refs")):
        if isinstance(item, dict):
            refs.append(item)
    if not refs:
        return False
    return all(str(ref.get("confidence") or "").strip().lower() == "high" for ref in refs)


def _coherence_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def normalize_ticker(raw: Any) -> tuple[str, str | None]:
    if not isinstance(raw, str):
        return "", "ticker must be a string."
    ticker = raw.strip().upper()
    if not ticker or not TICKER_RE.fullmatch(ticker):
        return "", "ticker must be 1-15 characters using letters, numbers, dots, or hyphens only."
    return ticker, None


def normalize_prospectus_url(raw: Any) -> tuple[str, str | None]:
    if not isinstance(raw, str):
        return "", "filing_url must be a SEC EDGAR Archives HTML URL."
    filing_url = raw.strip()
    lowered = filing_url.lower()
    if "<html" in lowered or "<table" in lowered or lowered.startswith("<"):
        return "", "filing_url must be a SEC EDGAR Archives HTML URL, not raw HTML filing text."
    if not SEC_PROSPECTUS_URL_RE.fullmatch(filing_url):
        return "", "filing_url must be a SEC EDGAR Archives HTML URL under https://www.sec.gov/Archives/edgar/data/."
    return filing_url, None


def _prospectus_value_per_share(result: dict[str, Any]) -> float | None:
    valuation = _dict(result.get("valuation"))
    value = _dict(valuation.get("companyDTO")).get("estimatedValuePerShare")
    return float(value) if isinstance(value, (int, float)) and not isinstance(value, bool) else None


def prospectus_review_status(packet: dict[str, Any]) -> str | None:
    value = _string_or_none(packet.get("reviewStatus")) or _string_or_none(packet.get("review_status"))
    return value.lower() if value else None


def prospectus_scenario_candidate_from_args(args: dict[str, Any]) -> dict[str, Any]:
    candidate = args.get("prospectusScenarioCandidate") or args.get("prospectus_scenario_candidate")
    return _dict(candidate)


def value_sources_from_args(args: dict[str, Any]) -> dict[str, Any]:
    sources = args.get("value_sources") or args.get("valueSources")
    if isinstance(sources, dict):
        return sources
    candidate = prospectus_scenario_candidate_from_args(args)
    sources = candidate.get("value_sources") or candidate.get("valueSources")
    return sources if isinstance(sources, dict) else {}


def scenario_source_metadata_from_args(args: dict[str, Any]) -> dict[str, Any]:
    metadata = args.get("scenario_source_metadata") or args.get("scenarioSourceMetadata")
    if isinstance(metadata, dict):
        return metadata
    candidate = prospectus_scenario_candidate_from_args(args)
    metadata = candidate.get("scenario_source_metadata") or candidate.get("scenarioSourceMetadata")
    return metadata if isinstance(metadata, dict) else {}


def guided_ticker_overrides_candidate(judgment: dict[str, Any]) -> dict[str, Any]:
    mapped = _dict(judgment.get("mapped_assumptions"))
    if not mapped:
        if _string_or_none(judgment.get("scenario_status")) == "candidate_values_required":
            return {
                "supported": False,
                "overrides": {},
                "reason": "Guided answers require numeric candidate values before governed ticker recalculation.",
                "candidateRequirements": sanitize_for_agent(judgment.get("candidate_requirements") or []),
            }
        return {
            "supported": False,
            "overrides": {},
            "reason": "No guided answers mapped to governed ticker recalculation inputs.",
        }
    overrides = {
        **mapped,
        "request_policy": {"mode": "user_refined_scenario"},
        "user_judgment": judgment,
    }
    return {"supported": True, "overrides": sanitize_for_agent(overrides)}


def guided_prospectus_scenario_candidate(judgment: dict[str, Any]) -> dict[str, Any]:
    mapped = _dict(judgment.get("mapped_assumptions"))
    scenario: dict[str, Any] = {}
    unsupported: dict[str, Any] = {}

    for key, value in mapped.items():
        if key == "net_proceeds":
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "net_proceeds", value)
        elif key == "revenue_growth":
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "compound_annual_growth_2_5", value)
        elif key in {"terminal_revenue", "target_revenue", "revenue_year_10", "year_10_revenue"}:
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "target_revenue", value)
        elif key == "operating_margin_next_year":
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "operating_margin_next_year", value)
        elif key in {"operating_margin", "target_operating_margin", "target_pre_tax_operating_margin"}:
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "target_operating_margin", value)
        elif key in {"margin_convergence_year", "convergence_year_margin"}:
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "margin_convergence_year", value)
        elif key == "sales_to_capital":
            if _put_numeric_prospectus_scenario_value(
                scenario, unsupported, key, "sales_to_capital_years_1_to_5", value
            ):
                scenario["sales_to_capital_years_6_to_10"] = scenario["sales_to_capital_years_1_to_5"]
        elif key == "sales_to_capital_years_1_to_5":
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "sales_to_capital_years_1_to_5", value)
        elif key == "sales_to_capital_years_6_to_10":
            _put_numeric_prospectus_scenario_value(scenario, unsupported, key, "sales_to_capital_years_6_to_10", value)
        elif key == "segments":
            scenario["segments"] = value
        else:
            unsupported[key] = {
                "value": sanitize_for_agent(value),
                "reason": "not_a_direct_prospectus_scenario_field",
            }

    if scenario:
        scenario = {"scenario_name": "guided_user_refined_scenario", **scenario}
    source_metadata = prospectus_scenario_source_metadata(judgment)
    value_sources = {
        key: "user_input"
        for key, metadata in source_metadata.items()
        if _string_or_none(_dict(metadata).get("source")) == "user_input"
    }
    for key in list(value_sources):
        field = driver_field_for_key(key)
        if field:
            value_sources.setdefault(field, "user_input")
    if not scenario and _string_or_none(judgment.get("scenario_status")) == "candidate_values_required":
        return {
            "supported": False,
            "scenario": {},
            "unsupportedMappedAssumptions": unsupported,
            "reason": "Guided answers require numeric candidate values before deterministic prospectus scenario inputs can be built.",
            "candidateRequirements": sanitize_for_agent(judgment.get("candidate_requirements") or []),
        }
    candidate = {
        "supported": bool(scenario),
        "scenario": sanitize_for_agent(scenario),
        "unsupportedMappedAssumptions": unsupported,
        "reason": None if scenario else "No guided answers mapped to deterministic prospectus scenario inputs.",
    }
    if value_sources:
        candidate["value_sources"] = sanitize_for_agent(value_sources)
    if source_metadata:
        candidate["scenario_source_metadata"] = sanitize_for_agent(source_metadata)
    if scenario and (value_sources or source_metadata):
        candidate["valueProspectusArgs"] = sanitize_for_agent(
            {
                "scenario": scenario,
                "value_sources": value_sources,
                "scenario_source_metadata": source_metadata,
            }
        )
    return candidate


def prospectus_scenario_source_metadata(judgment: dict[str, Any]) -> dict[str, Any]:
    metadata: dict[str, Any] = {}
    for answer in judgment.get("answers") or []:
        if not isinstance(answer, dict):
            continue
        if answer.get("unsupported_or_report_only_reason") is not None:
            continue
        if (answer.get("model_action") or "") != "user scenario override":
            continue
        override = _dict(answer.get("requested_override"))
        source_key = _string_or_none(override.get("field"))
        if not source_key:
            continue
        keys = prospectus_scenario_keys_for_source_field(source_key, override.get("value"))
        if not keys:
            continue
        label = _string_or_none(answer.get("anchor_label"))
        if label == "user_input":
            source = "user_input"
        elif label in {"low", "base", "high"}:
            source = f"anchor:{label}"
        else:
            source = "service"
        entry = {
            "source": source,
            "question_id": answer.get("question_id"),
            "selected_choice": answer.get("selected_choice"),
        }
        if answer.get("anchor_provenance"):
            entry["anchor_provenance"] = answer.get("anchor_provenance")
        if answer.get("anchor_explanation"):
            entry["anchor_explanation"] = answer.get("anchor_explanation")
        fallback_segments = _string_list(answer.get("fallback_segments"))
        if fallback_segments:
            entry["fallback_segments"] = fallback_segments
        if source == "service" and set(entry) <= {"source", "question_id", "selected_choice"}:
            continue
        for key in keys:
            metadata[key] = dict(entry)
    return metadata


def prospectus_scenario_keys_for_source_field(source_key: str, value: Any) -> list[str]:
    if source_key == "net_proceeds":
        return ["net_proceeds"]
    if source_key == "revenue_growth":
        return ["compound_annual_growth_2_5"]
    if source_key in {"terminal_revenue", "target_revenue", "revenue_year_10", "year_10_revenue"}:
        return ["target_revenue"]
    if source_key == "operating_margin_next_year":
        return ["operating_margin_next_year"]
    if source_key in {"operating_margin", "target_operating_margin", "target_pre_tax_operating_margin"}:
        return ["target_operating_margin"]
    if source_key in {"margin_convergence_year", "convergence_year_margin"}:
        return ["margin_convergence_year"]
    if source_key == "sales_to_capital":
        return ["sales_to_capital_years_1_to_5", "sales_to_capital_years_6_to_10"]
    if source_key == "sales_to_capital_years_1_to_5":
        return ["sales_to_capital_years_1_to_5"]
    if source_key == "sales_to_capital_years_6_to_10":
        return ["sales_to_capital_years_6_to_10"]
    if source_key == "segments" and isinstance(value, list):
        return ["segments"]
    return []


def _put_numeric_prospectus_scenario_value(
    scenario: dict[str, Any],
    unsupported: dict[str, Any],
    source_key: str,
    target_key: str,
    value: Any,
) -> bool:
    number = _number_or_none(value)
    if number is None:
        unsupported[source_key] = {
            "value": sanitize_for_agent(value),
            "reason": "prospectus_scenario_field_requires_numeric_value",
        }
        return False
    scenario[target_key] = number
    return True


def map_recalculate_overrides(requested: dict[str, Any]) -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    mapped: dict[str, Any] = {}
    unsupported: dict[str, Any] = {}
    metadata: dict[str, Any] = {}
    evidence_validation: dict[str, Any] | None = None
    request_policy_mode, request_policy_error = normalize_request_policy_mode(requested.get("request_policy"))
    if request_policy_error is not None:
        unsupported["request_policy"] = request_policy_error
    autonomous_researched = request_policy_mode == "autonomous_researched"
    user_refined_scenario = request_policy_mode == "user_refined_scenario"
    for key, value in requested.items():
        if key in REPORT_ONLY_OVERRIDE_FIELDS:
            validation = validate_accounting_override(key, value, request_policy_mode)
            metadata["accounting_and_claims"] = merge_accounting_metadata(
                metadata.get("accounting_and_claims"),
                validation,
            )
            if not validation["ok"]:
                unsupported[key] = accounting_override_unsupported(validation, key, value)
                continue
            mapped.update(validation.get("accepted_mcp_inputs", {}))
            continue
        if key == "evidence_packet":
            validation = validate_evidence_packet(value)
            evidence_validation = validation
            metadata[key] = evidence_packet_metadata(validation)
            if not validation["ok"]:
                unsupported[key] = evidence_packet_unsupported(validation)
            continue
        if key == "segment_economics":
            validation = validate_segment_economics(value)
            metadata[key] = segment_economics_metadata(validation)
            if not validation["ok"]:
                unsupported[key] = segment_economics_unsupported(validation)
                continue
            accepted = _dict(validation.get("accepted_mcp_inputs"))
            if accepted.get("segments") is not None:
                segments, segment_error = map_segments(accepted.get("segments"))
                if segment_error is not None:
                    unsupported[key] = {"value": segment_economics_metadata(validation), **segment_error}
                    continue
                mapped["segments"] = segments
            if accepted.get("sector_overrides"):
                sector_overrides = map_sector_overrides(accepted.get("sector_overrides"))
                if sector_overrides is None:
                    unsupported[key] = {
                        "value": segment_economics_metadata(validation),
                        "reason": "invalid_segment_economics_sector_overrides",
                    }
                    continue
                mapped["sectorOverrides"] = sector_overrides
            continue
        if key in RECALCULATE_METADATA_FIELDS:
            metadata[key] = sanitize_for_agent(value)
            continue
        if key not in SUPPORTED_OVERRIDE_FIELDS:
            unsupported[key] = unsupported_override_field(key, value)
            continue
        if autonomous_researched and key not in AUTONOMOUS_RESEARCHED_FIELDS:
            unsupported[key] = {
                "value": sanitize_for_agent(value),
                "status": "scenario_only_in_autonomous_researched_mode",
                "reason": "scenario_only_in_autonomous_researched_mode",
                "message": f"{key} is available only for explicit user scenarios, not autonomous researched recalculation.",
            }
            continue
        if user_refined_scenario and key not in USER_REFINED_SCENARIO_FIELDS:
            unsupported[key] = {
                "value": sanitize_for_agent(value),
                "status": "explicit_scenario_only_in_user_refined_scenario_mode",
                "reason": "explicit_scenario_only_in_user_refined_scenario_mode",
                "message": f"{key} is available only for explicit scenarios, not bounded user-refined guided refinement.",
            }
            continue
        if key == "segments":
            segments, segment_error = map_segments(value)
            if segment_error is not None:
                unsupported[key] = {"value": sanitize_for_agent(value), **segment_error}
            else:
                mapped["segments"] = segments
            continue
        if key == "sector_overrides":
            sector_overrides = map_sector_overrides(value)
            if sector_overrides is None:
                unsupported[key] = {"value": sanitize_for_agent(value), "reason": "invalid_sector_overrides"}
            else:
                mapped["sectorOverrides"] = sector_overrides
            continue
        if key == "growth_pattern_override":
            growth_pattern = map_growth_pattern_override(value)
            if growth_pattern is None:
                unsupported[key] = {"value": sanitize_for_agent(value), "reason": "invalid_growth_pattern_override"}
            else:
                mapped["growthPatternOverride"] = growth_pattern
            continue
        number = _number_or_none(value)
        if number is None:
            unsupported[key] = {"value": sanitize_for_agent(value), "reason": "not_numeric"}
            continue
        if not math.isfinite(number):
            unsupported[key] = {
                "value": sanitize_for_agent(value),
                "status": "invalid_numeric_value",
                "reason": "not_finite",
                "message": f"{key} must be a finite numeric value.",
            }
            continue
        if key == "revenue_growth":
            mapped["compoundAnnualGrowth2_5"] = round(normalize_percent(number), 2)
        elif key in {"terminal_revenue", "target_revenue", "revenue_year_10", "year_10_revenue"}:
            if number <= 0:
                unsupported[key] = {
                    "value": sanitize_for_agent(value),
                    "status": "scenario_input_out_of_bounds",
                    "reason": "scenario_input_out_of_bounds",
                    "message": f"{key} must be a positive finite revenue value.",
                    "minimum": 0.0,
                    "unit": "revenue",
                }
                continue
            mapped["terminalRevenue"] = round(number, 2)
        elif key in {"terminal_revenue_year", "target_revenue_year", "revenue_target_year"}:
            rounded_year = round(number)
            if (
                not math.isclose(number, rounded_year, rel_tol=0.0, abs_tol=0.0001)
                or not within_bounds(number, MIN_TERMINAL_REVENUE_YEAR, MAX_TERMINAL_REVENUE_YEAR)
            ):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_TERMINAL_REVENUE_YEAR,
                    MAX_TERMINAL_REVENUE_YEAR,
                    "projection year",
                )
                continue
            mapped["terminalRevenueYear"] = int(rounded_year)
        elif key == "operating_margin_next_year":
            mapped["operatingMarginNextYear"] = round(normalize_percent(number), 2)
        elif key == "operating_margin":
            mapped["targetPreTaxOperatingMargin"] = round(normalize_percent(number), 2)
        elif key in {"target_operating_margin", "target_pre_tax_operating_margin"}:
            mapped["targetPreTaxOperatingMargin"] = round(normalize_percent(number), 2)
        elif key in {"margin_convergence_year", "convergence_year_margin"}:
            if not within_bounds(number, MIN_MARGIN_CONVERGENCE_YEAR, MAX_MARGIN_CONVERGENCE_YEAR):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_MARGIN_CONVERGENCE_YEAR,
                    MAX_MARGIN_CONVERGENCE_YEAR,
                    "projection year",
                )
                continue
            mapped["convergenceYearMargin"] = round(number, 2)
        elif key == "sales_to_capital":
            normalized = normalize_sales_to_capital(number)
            if not within_bounds(normalized, MIN_SALES_TO_CAPITAL, MAX_SALES_TO_CAPITAL):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_SALES_TO_CAPITAL,
                    MAX_SALES_TO_CAPITAL,
                    "sales-to-capital multiple",
                )
                continue
            mapped["salesToCapitalYears1To5"] = round(normalized, 2)
            mapped["salesToCapitalYears6To10"] = round(normalized, 2)
        elif key == "sales_to_capital_years_1_to_5":
            normalized = normalize_sales_to_capital(number)
            if not within_bounds(normalized, MIN_SALES_TO_CAPITAL, MAX_SALES_TO_CAPITAL):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_SALES_TO_CAPITAL,
                    MAX_SALES_TO_CAPITAL,
                    "sales-to-capital multiple",
                )
                continue
            mapped["salesToCapitalYears1To5"] = round(normalized, 2)
        elif key == "sales_to_capital_years_6_to_10":
            normalized = normalize_sales_to_capital(number)
            if not within_bounds(normalized, MIN_SALES_TO_CAPITAL, MAX_SALES_TO_CAPITAL):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_SALES_TO_CAPITAL,
                    MAX_SALES_TO_CAPITAL,
                    "sales-to-capital multiple",
                )
                continue
            mapped["salesToCapitalYears6To10"] = round(normalized, 2)
        elif key == "wacc":
            mapped["initialCostCapital"] = round(normalize_percent(number), 2)
        elif key == "terminal_growth":
            mapped["terminalGrowthRate"] = round(normalize_percent(number), 2)
        elif key in {"terminal_roic", "terminal_return_on_capital", "terminal_return_on_invested_capital"}:
            if request_policy_mode != "explicit_scenario":
                unsupported[key] = {
                    "value": sanitize_for_agent(value),
                    "status": "explicit_scenario_required",
                    "reason": "explicit_scenario_required",
                    "message": f"{key} is available only when request_policy.mode is explicit_scenario.",
                }
                continue
            normalized = normalize_percent(number)
            if not within_bounds(normalized, MIN_TERMINAL_ROIC, MAX_TERMINAL_ROIC):
                unsupported[key] = bounded_numeric_unsupported(
                    key,
                    value,
                    MIN_TERMINAL_ROIC,
                    MAX_TERMINAL_ROIC,
                    "percent",
                )
                continue
            mapped["overrideAssumptionReturnOnCapital"] = {
                "overrideCost": round(normalized, 2),
                "isOverride": True,
                "additionalInputValue": 0.0,
                "additionalRadioValue": None,
            }
        elif key == "tax_rate":
            mapped["overrideAssumptionTaxRate"] = {
                "overrideCost": round(normalize_percent(number), 2),
                "isOverride": True,
                "additionalInputValue": 0.0,
                "additionalRadioValue": None,
            }
    if autonomous_researched:
        mapped["researchedBaselineMode"] = True
        evidence_blocker = evidence_packet_required_for_requested_changes(requested, evidence_validation)
        if evidence_blocker is not None and "evidence_packet" not in unsupported:
            unsupported["evidence_packet"] = evidence_blocker
    if request_policy_mode is not None:
        mapped["requestPolicyMode"] = request_policy_mode
    return mapped, unsupported, metadata


def evidence_packet_metadata(validation: dict[str, Any]) -> dict[str, Any]:
    return {
        "ok": validation.get("ok"),
        "status": validation.get("status"),
        "sanitized_packet": validation.get("sanitized_packet", {}),
        "governed_evidence": validation.get("governed_evidence", []),
        "report_only_evidence": validation.get("report_only_evidence", []),
        "rejected_evidence": validation.get("rejected_evidence", []),
        "source_family_status": validation.get("source_family_status", []),
        "validation_warnings": validation.get("validation_warnings", []),
        "unsupported_blockers": validation.get("unsupported_blockers", []),
    }


def evidence_packet_unsupported(validation: dict[str, Any]) -> dict[str, Any]:
    return {
        "value": evidence_packet_metadata(validation),
        "status": validation.get("status") or "invalid_packet",
        "reason": "invalid_evidence_packet",
        "message": "EvidencePacket validation failed; unsupported evidence cannot govern recalculation.",
    }


def segment_economics_metadata(validation: dict[str, Any]) -> dict[str, Any]:
    return {
        "ok": validation.get("ok"),
        "status": validation.get("status"),
        "quality": validation.get("quality"),
        "accepted_mcp_inputs": validation.get("accepted_mcp_inputs", {}),
        "segment_decisions": validation.get("segment_decisions", []),
        "report_only_facts": validation.get("report_only_facts", []),
        "rejected_economics": validation.get("rejected_economics", []),
        "unsupported": validation.get("unsupported", []),
        "metadata": validation.get("metadata", {}),
        "validation_warnings": validation.get("validation_warnings", []),
        "limitations": validation.get("limitations", []),
    }


def segment_economics_unsupported(validation: dict[str, Any]) -> dict[str, Any]:
    details = "; ".join(
        str(item)
        for item in [
            *validation.get("limitations", []),
            *validation.get("validation_warnings", []),
        ]
        if str(item).strip()
    )
    return {
        "value": segment_economics_metadata(validation),
        "status": validation.get("status") or "invalid_segment_economics",
        "reason": "invalid_segment_economics",
        "message": details or "SegmentEconomics validation failed; rejected segment economics cannot govern recalculation.",
    }


def accounting_override_unsupported(validation: dict[str, Any], key: str, value: Any) -> dict[str, Any]:
    unsupported = validation.get("unsupported", [])
    status = validation.get("status") or "invalid_accounting_and_claims"
    reason = "invalid_accounting_and_claims"
    if unsupported and isinstance(unsupported[0], dict):
        status = str(unsupported[0].get("status") or status)
        reason = str(unsupported[0].get("reason") or reason)
    return {
        "value": sanitize_for_agent(value),
        "metadata": accounting_metadata(validation),
        "status": status,
        "reason": reason,
        "message": f"{key} is report-only unless accepted by a governed AccountingAndClaims scenario validator.",
    }


def evidence_packet_required_for_requested_changes(
    requested: dict[str, Any],
    validation: dict[str, Any] | None,
) -> dict[str, Any] | None:
    required_drivers = evidence_drivers_required_for_requested_changes(requested)
    if not required_drivers:
        return None
    if validation is None:
        return {
            "value": None,
            "status": "missing_evidence_packet_for_requested_changes",
            "reason": "missing_evidence_packet_for_requested_changes",
            "message": "Autonomous researched recalculation requested governed model changes, but no EvidencePacket was provided.",
            "missing_drivers": required_drivers,
        }
    if not validation.get("ok"):
        return None
    governed_drivers = {
        str(item.get("driver"))
        for item in validation.get("governed_evidence", [])
        if isinstance(item, dict)
    }
    missing_drivers = [driver for driver in required_drivers if driver not in governed_drivers]
    if not missing_drivers:
        return None
    if governed_drivers:
        return {
            "value": evidence_packet_metadata(validation),
            "status": "evidence_driver_mismatch",
            "reason": "evidence_driver_mismatch",
            "message": "Autonomous researched recalculation requested model changes that are not supported by matching governed EvidencePacket drivers.",
            "missing_drivers": missing_drivers,
        }
    return {
        "value": evidence_packet_metadata(validation),
        "status": "no_governed_evidence_for_requested_changes",
        "reason": "no_governed_evidence_for_requested_changes",
        "message": "Autonomous researched recalculation requested governed model changes, but the EvidencePacket accepted no governed evidence.",
        "missing_drivers": missing_drivers,
    }


def evidence_drivers_required_for_requested_changes(requested: dict[str, Any]) -> list[str]:
    required: list[str] = []
    field_drivers = {
        "revenue_growth": "revenue_growth",
        "operating_margin": "operating_margin",
        "sales_to_capital": "reinvestment_sales_to_capital",
    }
    for field, driver in field_drivers.items():
        if field in requested:
            required.append(driver)
    if "sector_overrides" in requested:
        for driver in sector_override_required_drivers(requested.get("sector_overrides")):
            required.append(driver)
    return dedupe(required)


def sector_override_required_drivers(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    drivers: list[str] = []
    parameter_drivers = {
        "revenue_growth": "revenue_growth",
        "operating_margin": "operating_margin",
        "target_operating_margin": "operating_margin",
        "target_pre_tax_operating_margin": "operating_margin",
        "sales_to_capital": "reinvestment_sales_to_capital",
        "reinvestment_sales_to_capital": "reinvestment_sales_to_capital",
    }
    for raw in value:
        if not isinstance(raw, dict):
            continue
        parameter = _string_or_none(
            _first_present(raw.get("parameter"), raw.get("parameter_type"), raw.get("parameterType"))
        )
        driver = parameter_drivers.get(parameter or "")
        if driver:
            drivers.append(driver)
    return drivers


def normalize_request_policy_mode(value: Any) -> tuple[str | None, dict[str, Any] | None]:
    if not isinstance(value, dict):
        return None, None
    mode = str(value.get("mode") or value.get("baseline_mode") or "").strip().lower()
    if not mode:
        return None, None
    if mode not in REQUEST_POLICY_MODES:
        return None, {
            "value": sanitize_for_agent(value),
            "status": "invalid_request_policy_mode",
            "reason": "invalid_request_policy_mode",
            "message": "request_policy.mode must be one of mechanical_baseline, autonomous_researched, user_refined_scenario, or explicit_scenario.",
        }
    if mode in {"researched_autonomous", "researched_baseline"}:
        return "autonomous_researched", None
    return mode, None


def map_segments(value: Any) -> tuple[dict[str, Any] | None, dict[str, str] | None]:
    raw_segments = value.get("segments") if isinstance(value, dict) else value
    if not isinstance(raw_segments, list) or not raw_segments:
        return None, {"reason": "invalid_segments", "message": "segments must be a non-empty list."}

    validation = sanitize_segment_package({"segments": raw_segments})
    if validation["baseline_quality"] != "segment_weighted_baseline":
        warnings = validation.get("validation_warnings") or []
        return None, {
            "reason": str(validation["baseline_quality"]),
            "message": "; ".join(str(warning) for warning in warnings) or "segment package did not pass validation.",
        }
    mapping_error = segment_service_mapping_error(raw_segments, validation["segments"])
    if mapping_error is not None:
        return None, {"reason": "segment_mapping_blocked", "message": mapping_error}

    segments: list[dict[str, Any]] = []
    for raw in raw_segments:
        if not isinstance(raw, dict):
            return None, {"reason": "invalid_segments", "message": "each segment must be a JSON object."}
        segment: dict[str, Any] = {}
        segment_name = _string_or_none(_first_present(raw.get("segment_name"), raw.get("segmentName"), raw.get("name")))
        sector = _string_or_none(
            _first_present(
                raw.get("sector_key"),
                raw.get("sectorKey"),
                raw.get("yahoo_industry_key"),
                raw.get("yahooIndustryKey"),
                raw.get("service_sector_key"),
                raw.get("serviceSectorKey"),
                raw.get("sector"),
                raw.get("mapped_industry"),
                raw.get("mappedIndustry"),
            )
        )
        industry = _string_or_none(_first_present(raw.get("mapped_industry"), raw.get("mappedIndustry"), raw.get("industry")))
        if sector:
            segment["sector"] = sector
        if industry:
            segment["industry"] = industry
        if segment_name:
            segment["segmentName"] = segment_name
        components = raw.get("components")
        if components is not None:
            if not isinstance(components, list):
                return None, {"reason": "invalid_segments", "message": "components must be a list when present."}
            segment["components"] = [str(item) for item in components]
        elif segment_name:
            segment["components"] = [segment_name]
        raw_mapping_score = _first_present(raw.get("mapping_score"), raw.get("mappingScore"))
        if raw_mapping_score is not None:
            mapping_score = _number_or_none(raw_mapping_score)
            if mapping_score is None:
                return None, {"reason": "invalid_segments", "message": "mappingScore must be numeric when present."}
            segment["mappingScore"] = mapping_score
        revenue_share = parse_revenue_weight(raw)
        if revenue_share is None:
            return None, {"reason": "segment_evidence_insufficient", "message": "segment weighting requires sourced revenue weights or revenue amounts."}
        segment["revenueShare"] = round(revenue_share, 6)
        for source_keys, target in [
            (("mapping_confidence", "mappingConfidence"), "mappingConfidence"),
            (("source_name", "sourceName"), "sourceName"),
            (("source_date", "sourceDate"), "sourceDate"),
            (("source_url", "sourceUrl", "source_reference", "sourceReference"), "sourceUrl"),
        ]:
            text = _string_or_none(_first_present(*(raw.get(source_key) for source_key in source_keys)))
            if text:
                segment[target] = text
        validation_warnings = raw.get("validation_warnings") or raw.get("validationWarnings")
        operating_margin = _first_present(raw.get("operating_margin"), raw.get("operatingMargin"))
        if operating_margin is not None:
            if isinstance(validation_warnings, list):
                validation_warnings = list(validation_warnings)
            else:
                validation_warnings = []
            validation_warnings.append(
                "Segment operating margin is report-only unless provided through validated SegmentEconomics."
            )
        if isinstance(validation_warnings, list):
            segment["validationWarnings"] = [str(item) for item in validation_warnings]
        if not segment:
            return None, {"reason": "invalid_segments", "message": "each segment must contain mapped fields."}
        segments.append(segment)
    return {"segments": segments}, None


def segment_service_mapping_error(raw_segments: list[Any], sanitized_segments: list[dict[str, Any]]) -> str | None:
    raw_by_name = {
        str(raw.get("segment_name") or raw.get("segmentName") or raw.get("name") or raw.get("segment") or "").strip(): raw
        for raw in raw_segments
        if isinstance(raw, dict)
    }
    errors: list[str] = []
    for segment in sanitized_segments:
        segment_name = str(segment.get("segment_name") or "").strip()
        raw = raw_by_name.get(segment_name) or {}
        disclosure_level = str(raw.get("disclosure_level") or raw.get("disclosureLevel") or "").strip().lower()
        if disclosure_level == "geography" and not has_operating_segment_basis(raw):
            errors.append(
                f"Geographic disclosure for {segment_name} cannot support segment baseline use without an explicit operating-segment basis and mapping rationale."
            )
        if not service_sector_key(raw):
            errors.append(
                f"{segment_name} requires sector_key or yahoo_industry_key for service baseline mapping; mapped_industry is display-only."
            )
    if not errors:
        return None
    return "; ".join(errors)


def has_operating_segment_basis(raw: dict[str, Any]) -> bool:
    basis = raw.get("operating_segment_basis")
    if basis is None:
        basis = raw.get("operatingSegmentBasis")
    rationale = _string_or_none(
        _first_present(
            raw.get("mapping_rationale"),
            raw.get("mappingRationale"),
            raw.get("operating_segment_rationale"),
            raw.get("operatingSegmentRationale"),
        )
    )
    return basis is True and rationale is not None


def service_sector_key(raw: dict[str, Any]) -> str:
    for key in SEGMENT_SERVICE_SECTOR_KEY_FIELDS:
        value = _string_or_none(raw.get(key))
        if value:
            return value
    sector = _string_or_none(_first_present(raw.get("sector"), raw.get("sectorName")))
    return sector if sector is not None and looks_like_service_sector_key(sector) else ""


def looks_like_service_sector_key(value: str) -> bool:
    if not value or value != value.lower():
        return False
    return all(character.isalnum() or character == "-" for character in value)


def map_sector_overrides(value: Any) -> list[dict[str, Any]] | None:
    if not isinstance(value, list) or not value:
        return None

    overrides: list[dict[str, Any]] = []
    for raw in value:
        if not isinstance(raw, dict):
            return None
        sector_name = _string_or_none(
            _first_present(
                raw.get("sector_key"),
                raw.get("sectorKey"),
                raw.get("yahoo_industry_key"),
                raw.get("yahooIndustryKey"),
                raw.get("service_sector_key"),
                raw.get("serviceSectorKey"),
                raw.get("sector"),
                raw.get("sector_name"),
                raw.get("sectorName"),
            )
        )
        parameter_type = _string_or_none(
            _first_present(raw.get("parameter"), raw.get("parameter_type"), raw.get("parameterType"))
        )
        parameter_aliases = {
            "target_operating_margin": "operating_margin",
            "target_pre_tax_operating_margin": "operating_margin",
            "reinvestment_sales_to_capital": "sales_to_capital",
        }
        if parameter_type in parameter_aliases:
            parameter_type = parameter_aliases[parameter_type]
        adjustment_type = _string_or_none(
            _first_present(raw.get("adjustment_type"), raw.get("adjustmentType"))
        )
        timeframe = _string_or_none(raw.get("timeframe")) or "both"
        number = _number_or_none(raw.get("value"))
        unit = (_string_or_none(raw.get("unit")) or "percent").lower()

        if (
            sector_name is None
            or parameter_type not in {"revenue_growth", "operating_margin", "sales_to_capital"}
            or adjustment_type not in {"absolute", "relative_multiplier", "relative_additive"}
            or timeframe not in {"years_1_to_5", "years_6_to_10", "both"}
            or number is None
            or unit not in {"percent", "x"}
        ):
            return None

        if unit == "percent":
            number = normalize_percent(number)
        elif parameter_type == "sales_to_capital":
            number = normalize_sales_to_capital(number)

        overrides.append(
            {
                "sectorName": sector_name,
                "parameterType": parameter_type,
                "value": round(number, 2),
                "adjustmentType": adjustment_type,
                "timeframe": timeframe,
            }
        )
    return overrides


def map_growth_pattern_override(value: Any) -> str | None:
    text = _string_or_none(value)
    if text is None:
        return None
    normalized = text.upper().replace("-", "_").replace(" ", "_")
    normalized = normalized.removesuffix("_GROWTH")
    aliases = {
        "STABLE": "STABLE",
        "TWO_STAGE": "TWO_STAGE",
        "THREE_STAGE": "THREE_STAGE",
        "N_STAGE": "N_STAGE",
        "NSTAGE": "N_STAGE",
    }
    return aliases.get(normalized)


def within_bounds(value: float, minimum: float, maximum: float) -> bool:
    return math.isfinite(value) and minimum <= value <= maximum


def bounded_numeric_unsupported(
    key: str,
    value: Any,
    minimum: float,
    maximum: float,
    unit: str,
) -> dict[str, Any]:
    return {
        "value": sanitize_for_agent(value),
        "status": "scenario_input_out_of_bounds",
        "reason": "scenario_input_out_of_bounds",
        "message": f"{key} must be between {minimum:g} and {maximum:g} {unit}.",
        "minimum": minimum,
        "maximum": maximum,
    }


def normalize_percent(value: float) -> float:
    if abs(value) <= 1.0:
        return value * 100.0
    return value


def normalize_sales_to_capital(value: float) -> float:
    if abs(value) > 50.0:
        return value / 100.0
    return value


def valuation_success_payload(
    tool: str,
    ticker: str,
    valuation: dict[str, Any],
    baseline_context: dict[str, Any] | None = None,
) -> dict[str, Any]:
    baseline = extract_baseline_contract(valuation, baseline_context)
    return {
        "ok": True,
        "tool": tool,
        "ticker": ticker,
        "valuation": sanitize_for_agent(valuation),
        "dcf": extract_dcf_summary(valuation),
        "baseline": baseline,
        "assumptions": extract_assumptions(valuation),
        "accountingAndClaims": extract_accounting_and_claims(valuation),
        "provenance": extract_source_provenance(valuation),
        "sourceQualityGate": extract_source_quality_gate(valuation),
        "growthAnchor": extract_growth_anchor(valuation),
        "referenceData": reference_data_status(valuation),
        "version": version_metadata(valuation),
        "policy": policy_metadata(baseline_entrypoint_for_tool(tool)),
        "warnings": extract_warnings(valuation),
    }


def prospectus_extraction_success_payload(
    tool: str, result: dict[str, Any], review_reference: str | None = None
) -> dict[str, Any]:
    packet = _dict(result.get("packet"))
    return {
        "ok": True,
        "tool": tool,
        "prospectus": {
            "status": _string_or_none(result.get("status")) or "requires_review",
            "reviewStatus": _string_or_none(packet.get("reviewStatus")) or _string_or_none(packet.get("review_status")),
            "reviewReference": review_reference,
            "company": sanitize_for_agent(_dict(packet.get("company"))),
            "filing": sanitize_for_agent(_dict(packet.get("filing"))),
            "sourceUrl": _string_or_none(packet.get("sourceUrl")) or _string_or_none(packet.get("source_url")),
            "segmentReview": prospectus_segment_review(packet),
            "packet": sanitize_for_agent(packet),
        },
        "provenance": extract_prospectus_source_provenance(_dict(packet.get("sourceProvenance") or packet.get("source_provenance"))),
        "sourceQualityGate": normalize_source_quality_gate(_dict(result.get("sourceQualityGate") or result.get("source_quality_gate"))),
        "driverAnchors": sanitize_for_agent(_dict(result.get("driverAnchors") or result.get("driver_anchors"))),
        "version": {"mcp": mcp_metadata()},
        "policy": policy_metadata("prospectus_extraction"),
    }


def prospectus_segment_review(packet: dict[str, Any]) -> dict[str, Any]:
    raw_segments = packet.get("segments")
    raw_candidate_tables = packet.get("segmentCandidateTables") or packet.get("segment_candidate_tables")
    segments = raw_segments if isinstance(raw_segments, list) else []
    candidate_tables = raw_candidate_tables if isinstance(raw_candidate_tables, list) else []
    rows = [prospectus_segment_review_row(segment) for segment in segments if isinstance(segment, dict)]
    proposed = [row for row in rows if row.get("sectorKey")]
    unmapped = [row for row in rows if not row.get("sectorKey")]
    coverage = round(sum(prospectus_segment_review_weight(row) for row in rows) * 100.0, 2)
    material_gap = any(
        (not row.get("sectorKey") and prospectus_segment_review_weight(row) > 0.10)
        or (
            str(row.get("mappingConfidence") or "").lower() in {"low", "unmapped", "unknown"}
            and prospectus_segment_review_weight(row) > 0.05
        )
        for row in rows
    )
    return {
        "status": "proposed_mapping_ready"
        if segments
        else "candidate_tables_only" if candidate_tables else "no_segment_candidates",
        "revenueCoveragePct": coverage,
        "materialGap": material_gap,
        "proposedMappings": proposed,
        "unmappedRows": [prospectus_segment_unmapped_review_row(row) for row in unmapped],
        "candidateTableCount": len(candidate_tables),
        "allowedActions": ["approve_mappings", "correct_mapping", "reject_mapping", "leave_unmapped"],
        "warnings": dedupe([warning for row in rows for warning in _string_list(row.get("warnings"))]),
    }


def segment_mapping_proposal_review(result: dict[str, Any]) -> dict[str, Any]:
    review = prospectus_segment_review({"segments": result.get("proposals") if isinstance(result, dict) else []})
    service_coverage = _number_or_none(_dict(result).get("revenueCoveragePct"))
    if service_coverage is not None:
        review["revenueCoveragePct"] = service_coverage
    if isinstance(_dict(result).get("materialGap"), bool):
        review["materialGap"] = bool(result["materialGap"])
    service_warnings = _string_list(_dict(result).get("warnings"))
    if service_warnings:
        review["warnings"] = dedupe(service_warnings + _string_list(review.get("warnings")))
    return review


def prospectus_segment_review_row(segment: dict[str, Any]) -> dict[str, Any]:
    components = segment.get("components")
    return {
        "name": _string_or_none(_first_present(segment.get("segmentName"), segment.get("segment_name"), segment.get("name"))),
        "revenueAmount": _number_or_none(_first_present(segment.get("revenueAmount"), segment.get("revenue_amount"))),
        "revenueWeight": _number_or_none(_first_present(segment.get("revenueWeight"), segment.get("revenue_weight"))),
        "sectorKey": _string_or_none(_first_present(segment.get("sectorKey"), segment.get("sector_key"))),
        "mappedIndustry": _string_or_none(_first_present(segment.get("mappedIndustry"), segment.get("mapped_industry"))),
        "mappingConfidence": _string_or_none(
            _first_present(segment.get("mappingConfidence"), segment.get("mapping_confidence"))
        ),
        "mappingScore": _number_or_none(_first_present(segment.get("mappingScore"), segment.get("mapping_score"))),
        "rationale": _string_or_none(segment.get("rationale")),
        "components": [str(item) for item in components] if isinstance(components, list) else [],
        "rowRole": _string_or_none(_first_present(segment.get("rowRole"), segment.get("row_role"))),
        "warnings": _string_list(segment.get("warnings")),
    }


def prospectus_segment_unmapped_review_row(row: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": row.get("name"),
        "revenueAmount": row.get("revenueAmount"),
        "revenueWeight": row.get("revenueWeight"),
        "rowRole": row.get("rowRole"),
        "mappingConfidence": row.get("mappingConfidence"),
        "mappingScore": row.get("mappingScore"),
        "rationale": row.get("rationale"),
        "warnings": row.get("warnings") or [],
    }


def prospectus_segment_review_weight(row: dict[str, Any]) -> float:
    weight = _number_or_none(row.get("revenueWeight"))
    if weight is None:
        return 0.0
    if weight > 1.5:
        weight = weight / 100.0
    return weight if weight > 0.0 else 0.0


def prospectus_review_token(packet: dict[str, Any]) -> str | None:
    if not packet:
        return None
    source_url = _string_or_none(packet.get("sourceUrl") or packet.get("source_url"))
    filing = _dict(packet.get("filing"))
    company = _dict(packet.get("company"))
    basis = {
        "sourceUrl": source_url,
        "accessionNumber": filing.get("accessionNumber") or filing.get("accession_number"),
        "filingDate": filing.get("filingDate") or filing.get("filing_date"),
        "legalName": company.get("legalName") or company.get("legal_name") or company.get("name"),
    }
    raw = json.dumps(basis, sort_keys=True, default=str)
    return "prospectus_" + hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]


def unwrap_prospectus_packet(value: Any) -> Any:
    if not isinstance(value, dict):
        return value
    prospectus = value.get("prospectus")
    if isinstance(prospectus, dict) and isinstance(prospectus.get("packet"), dict):
        return prospectus["packet"]
    nested_packet = value.get("packet")
    if isinstance(nested_packet, dict) and (
        nested_packet.get("schemaVersion") == "prospectus_financial_packet.v1"
        or nested_packet.get("schema_version") == "prospectus_financial_packet.v1"
    ):
        return nested_packet
    return value


def deep_merge_dict(base: dict[str, Any], overrides: dict[str, Any]) -> dict[str, Any]:
    merged = copy.deepcopy(base)
    for key, value in overrides.items():
        if isinstance(value, dict) and isinstance(merged.get(key), dict):
            merged[key] = deep_merge_dict(merged[key], value)
        else:
            merged[key] = copy.deepcopy(value)
    return merged


def prospectus_valuation_success_payload(tool: str, result: dict[str, Any]) -> dict[str, Any]:
    packet = _dict(result.get("packet"))
    scenario = _dict(result.get("scenario"))
    valuation = prospectus_valuation_for_agent(_dict(result.get("valuation")))
    provenance = _dict(result.get("sourceProvenance") or result.get("source_provenance"))
    if not provenance:
        provenance = _dict(packet.get("sourceProvenance") or packet.get("source_provenance"))
    valuation_basis_status = _string_or_none(result.get("valuationBasisStatus") or result.get("valuation_basis_status"))
    valuation_case_status = _string_or_none(result.get("valuationCaseStatus") or result.get("valuation_case_status"))
    proceeds_basis = _string_or_none(result.get("proceedsBasis") or result.get("proceeds_basis"))
    valuation_basis_warnings = _string_list(result.get("valuationBasisWarnings") or result.get("valuation_basis_warnings"))
    dcf = extract_dcf_summary(valuation)
    apply_prospectus_dcf_display_policy(dcf, valuation_case_status)
    return {
        "ok": True,
        "tool": tool,
        "priceBasis": _string_or_none(result.get("priceBasis")) or _string_or_none(result.get("price_basis")) or "offering_price",
        "valuationBasisStatus": valuation_basis_status,
        "valuationCaseStatus": valuation_case_status,
        "proceedsBasis": proceeds_basis,
        "valuationBasisWarnings": valuation_basis_warnings,
        "prospectus": {
            "status": _string_or_none(result.get("status")) or "valued",
            "reviewStatus": _string_or_none(packet.get("reviewStatus")) or _string_or_none(packet.get("review_status")),
            "company": sanitize_for_agent(_dict(packet.get("company"))),
            "filing": sanitize_for_agent(_dict(packet.get("filing"))),
            "sourceUrl": _string_or_none(packet.get("sourceUrl")) or _string_or_none(packet.get("source_url")),
            "packet": sanitize_for_agent(packet),
        },
        "scenario": sanitize_for_agent(scenario) if scenario else None,
        "valuation": valuation,
        "dcf": dcf,
        "baseline": extract_baseline_contract(valuation, {"requestPolicyMode": "prospectus_reviewed"}),
        "assumptions": extract_assumptions(valuation),
        "accountingAndClaims": extract_accounting_and_claims(valuation),
        "provenance": extract_prospectus_source_provenance(provenance),
        "sourceQualityGate": normalize_source_quality_gate(_dict(result.get("sourceQualityGate") or result.get("source_quality_gate"))),
        "driverAnchors": sanitize_for_agent(_dict(result.get("driverAnchors") or result.get("driver_anchors"))),
        "growthAnchor": extract_growth_anchor(valuation),
        "referenceData": prospectus_reference_data_status(valuation),
        "version": version_metadata(valuation),
        "policy": policy_metadata("prospectus_reviewed"),
        "warnings": dedupe(valuation_basis_warnings + extract_warnings(valuation)),
    }


def prospectus_valuation_for_agent(valuation: dict[str, Any]) -> dict[str, Any]:
    return _remove_yahoo_market_references(sanitize_for_agent(valuation))


def _remove_yahoo_market_references(value: Any) -> Any:
    if isinstance(value, dict):
        cleaned: dict[str, Any] = {}
        for key, item in value.items():
            filtered = _remove_yahoo_market_references(item)
            if filtered is None:
                continue
            if isinstance(filtered, (list, dict)) and not filtered:
                cleaned[key] = filtered
                continue
            cleaned[key] = filtered
        return cleaned
    if isinstance(value, list):
        return [
            filtered
            for item in value
            if (filtered := _remove_yahoo_market_references(item)) is not None
        ]
    if isinstance(value, str) and ("yahoo" in value.lower() or "yfinance" in value.lower()):
        return None
    return value


def prospectus_reference_data_status(valuation: dict[str, Any]) -> dict[str, Any]:
    anchor = extract_growth_anchor(valuation)
    return {
        "marketData": {
            "provider": "not_used_prospectus_primary_filing",
            "status": "not_queried_for_prospectus_path",
            "warnings": [],
        },
        "damodaranReferenceData": {
            "status": "available_when_growth_anchor_present" if anchor.get("mappedEntity") else "not_returned",
            "mappedEntity": anchor.get("mappedEntity"),
            "region": anchor.get("region"),
            "year": anchor.get("year"),
            "sourceDate": anchor.get("sourceDate"),
            "confidence": anchor.get("confidence"),
            "warnings": anchor.get("warnings", []),
        },
    }


def extract_prospectus_source_provenance(source: dict[str, Any]) -> dict[str, Any]:
    if not source:
        return {}
    return {
        "sourceClass": source.get("sourceClass") or source.get("source_class"),
        "provider": source.get("provider"),
        "sourceDate": source.get("sourceDate") or source.get("source_date"),
        "periodEnd": source.get("periodEnd") or source.get("period_end"),
        "retrievalStatus": source.get("retrievalStatus") or source.get("retrieval_status"),
        "crossCheckStatus": source.get("crossCheckStatus") or source.get("cross_check_status"),
        "sourcePolicyStatus": source.get("sourcePolicyStatus") or source.get("source_policy_status"),
        "warnings": _string_list(source.get("warnings")),
        "dataQualityWarnings": _data_quality_warning_list(source.get("dataQualityWarnings") or source.get("data_quality_warnings")),
    }


def extract_baseline_contract(
    valuation: dict[str, Any],
    baseline_context: dict[str, Any] | None = None,
) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    operating = _dict(transparency.get("operatingAssumptions"))
    context = baseline_context or {}

    baseline_quality = str(transparency.get("baselineQuality") or "single_industry_fallback")
    segment_aware = bool(transparency.get("segmentAware"))
    segment_count = _int_or_zero(transparency.get("segmentCount"))
    segment_coverage_pct = _number_or_none(transparency.get("segmentCoveragePct"))
    if segment_coverage_pct is None:
        segment_coverage_pct = 0.0
    mapped_industries = _string_list(transparency.get("mappedIndustries"))
    weighted_assumptions = _dict(transparency.get("weightedBaselineAssumptions"))
    researched_mode = bool(context.get("researchedBaselineMode"))

    baseline_use_status = _string_or_none(transparency.get("baselineUseStatus"))
    if baseline_use_status is None:
        baseline_use_status = derive_baseline_use_status(
            baseline_quality=baseline_quality,
            segment_aware=segment_aware,
            researched_mode=researched_mode,
        )

    target_margin = _first_present(
        operating.get("targetOperatingMargin"),
        extract_assumptions(valuation)["margin"]["targetOperatingMargin"],
    )
    target_status = _string_or_none(transparency.get("targetOperatingMarginStatus"))
    if target_status is None:
        target_status = "segment_weighted" if segment_aware else "single_industry_mechanical_fallback"
    target_source = _string_or_none(transparency.get("targetOperatingMarginSource"))
    if target_source is None:
        target_source = "Segment-weighted mechanical baseline" if segment_aware else "Single-industry mechanical fallback"

    warnings = _string_list(transparency.get("baselineWarnings"))
    unsupported_baseline_drivers = _issue_list(transparency.get("unsupportedBaselineDrivers"))
    if baseline_quality == "single_industry_fallback" and not segment_aware:
        warnings.append(
            "Single-industry mechanical fallback was used; target operating margin is not segment-weighted or researched evidence-supported."
        )
        if not any(item.get("field") == "target_operating_margin" for item in unsupported_baseline_drivers):
            unsupported_baseline_drivers.append(
                {
                    "field": "target_operating_margin",
                    "status": "mechanical_fallback",
                    "reason": "Target operating margin came from the company-level industry fallback, not validated segment weighting or governed evidence.",
                }
            )
    if researched_mode and not segment_aware:
        warnings.append(
            "researched baseline mode requires validated segment weighting or governed driver evidence; no valid segment package was used, so the baseline remains mechanical and challenged."
        )
        if not any(item.get("field") == "segments" for item in unsupported_baseline_drivers):
            unsupported_baseline_drivers.append(
                {
                    "field": "segments",
                    "status": "segment_evidence_insufficient",
                    "reason": "Researched baseline mode did not receive a validated segment package.",
                }
            )

    unsupported_adjustment_fields = _issue_list(transparency.get("unsupportedAdjustmentFields"))
    if not unsupported_adjustment_fields:
        unsupported_adjustment_fields = default_unsupported_adjustment_fields()

    return {
        "baselineQuality": baseline_quality,
        "baselineUseStatus": baseline_use_status,
        "valuationCaseStatus": _string_or_none(transparency.get("valuationCaseStatus")),
        "valuationBasisStatus": _string_or_none(transparency.get("valuationBasisStatus")),
        "proceedsBasis": _string_or_none(transparency.get("proceedsBasis")),
        "requestPolicyMode": _string_or_none(transparency.get("requestPolicyMode"))
        or _string_or_none(context.get("requestPolicyMode")),
        "segmentAware": segment_aware,
        "segmentCount": segment_count,
        "segmentCoveragePct": round(segment_coverage_pct, 2),
        "mappedIndustries": mapped_industries,
        "weightedBaselineAssumptions": weighted_assumptions,
        "baselineWarnings": dedupe(warnings),
        "unsupportedBaselineDrivers": unsupported_baseline_drivers,
        "unsupportedAdjustmentFields": unsupported_adjustment_fields,
        "targetOperatingMargin": target_margin,
        "targetOperatingMarginSource": target_source,
        "targetOperatingMarginStatus": target_status,
    }


def derive_baseline_use_status(*, baseline_quality: str, segment_aware: bool, researched_mode: bool) -> str:
    if baseline_quality == "segment_weighted_baseline" and segment_aware:
        return "validated_segment_weighted"
    if researched_mode and not segment_aware:
        return "segment_evidence_insufficient"
    if baseline_quality.startswith("segment_") and baseline_quality != "segment_weighted_baseline":
        return "challenged_baseline"
    return "mechanical_only"


def default_unsupported_adjustment_fields() -> list[dict[str, str]]:
    return [
        {
            "field": "operating_margin_next_year",
            "status": "scenario_only_in_autonomous_researched_mode",
            "reason": "Next-year operating margin can be used for explicit user scenarios, but autonomous researched baselines must not change it.",
        },
        {
            "field": "wacc",
            "status": "scenario_only_in_autonomous_researched_mode",
            "reason": "WACC can be used for explicit scenarios, but autonomous researched baselines must not change it without a governed tested path.",
        },
        {
            "field": "terminal_growth",
            "status": "scenario_only_in_autonomous_researched_mode",
            "reason": "Terminal growth can be used for explicit scenarios, but autonomous researched baselines must not change it without a governed tested path.",
        },
        {
            "field": "tax_rate",
            "status": "scenario_only_in_autonomous_researched_mode",
            "reason": "Tax-rate changes are report-only or explicit-scenario fields in autonomous researched mode.",
        },
        {"field": "rd_capitalization", "status": "source_required", "reason": "R&D capitalization is automatic in autonomous researched mode only when multi-year source-backed R&D history and an amortization policy pass validation."},
        {"field": "leases", "status": "blocked_report_only", "reason": "Lease adjustments are report-only in Phase 5; R&D capitalization is the only governed accounting model path."},
        {"field": "options", "status": "blocked_report_only", "reason": "Options and warrants are explain/flag only unless a governed service contract applies them."},
        {"field": "options_warrants", "status": "blocked_report_only", "reason": "Options and warrants are report-only unless service-calculated option inputs are available; direct claim overrides remain blocked."},
        {"field": "nols", "status": "blocked_report_only", "reason": "NOL adjustments are explain/flag only unless a governed service contract applies them."},
        {"field": "nol_tax", "status": "blocked_report_only", "reason": "NOL/tax adjustments are report-only or explicit scenario-only unless a tested governed path exists."},
        {"field": "sbc_dilution", "status": "blocked_report_only", "reason": "SBC/dilution diagnostics are report-only in Phase 5."},
        {"field": "cash", "status": "blocked_report_only", "reason": "Cash adjustments are report-only for autonomous researched baselines."},
        {"field": "debt", "status": "blocked_report_only", "reason": "Debt adjustments are report-only for autonomous researched baselines."},
        {"field": "share_count", "status": "blocked_report_only", "reason": "Share-count adjustments are report-only for autonomous researched baselines."},
        {"field": "accounting_adjustments", "status": "blocked_report_only", "reason": "Accounting cleanup fields are report-only unless an explicit governed service input is supported."},
    ]


def unsupported_override_field(key: str, value: Any) -> dict[str, Any]:
    if key in DIRECT_VALUATION_OUTPUT_FIELDS:
        return {
            "value": sanitize_for_agent(value),
            "status": "direct_valuation_output_rejected",
            "reason": "direct_valuation_output_rejected",
            "message": f"{key} is a valuation output or market-price calibration field, not a valid recalculate input.",
        }
    if key in REPORT_ONLY_OVERRIDE_FIELDS:
        return {
            "value": sanitize_for_agent(value),
            "status": "blocked_report_only",
            "reason": "blocked_report_only",
            "message": f"{key} is report-only in autonomous researched recalculation unless a governed service contract explicitly supports it.",
        }
    return {
        "value": sanitize_for_agent(value),
        "status": "unsupported_override_field",
        "reason": "unsupported_override_field",
        "message": f"{key} is not governed by the MCP recalculate contract.",
    }


def blocked_baseline_contract(unsupported: dict[str, Any]) -> dict[str, Any]:
    warnings = ["Recalculate was blocked before valuation-service execution because unsupported overrides were requested."]
    return {
        "baselineQuality": "not_calculated",
        "baselineUseStatus": "blocked",
        "segmentAware": False,
        "segmentCount": 0,
        "segmentCoveragePct": 0.0,
        "mappedIndustries": [],
        "weightedBaselineAssumptions": {},
        "baselineWarnings": warnings,
        "unsupportedBaselineDrivers": [],
        "unsupportedAdjustmentFields": [
            {
                "field": key,
                "status": _dict(value).get("status") or _dict(value).get("reason") or "unsupported_override_field",
                "reason": _dict(value).get("message") or _dict(value).get("reason") or "Unsupported override.",
            }
            for key, value in unsupported.items()
        ],
        "targetOperatingMargin": None,
        "targetOperatingMarginSource": None,
        "targetOperatingMarginStatus": "blocked",
    }


def extract_dcf_summary(valuation: dict[str, Any]) -> dict[str, Any]:
    company = _dict(valuation.get("companyDTO"))
    financial = _dict(valuation.get("financialDTO"))
    terminal = _dict(valuation.get("terminalValueDTO"))
    return {
        "companyName": valuation.get("companyName"),
        "currency": valuation.get("currency"),
        "stockCurrency": valuation.get("stockCurrency"),
        "primaryModel": valuation.get("primaryModel"),
        "growthPattern": valuation.get("growthPattern"),
        "projectionYears": valuation.get("projectionYears"),
        "estimatedValuePerShare": _first_present(
            company.get("estimatedValuePerShare"),
            financial.get("intrinsicValue"),
            valuation.get("recommendedIntrinsicValue"),
        ),
        "marketPrice": company.get("price"),
        "valueOfEquity": company.get("valueOfEquity"),
        "numberOfShares": company.get("numberOfShares"),
        "terminalGrowthRate": terminal.get("growthRate"),
        "terminalCostOfCapital": terminal.get("costOfCapital"),
    }


def apply_prospectus_dcf_display_policy(dcf: dict[str, Any], valuation_case_status: str | None) -> None:
    if valuation_case_status == "challenged_valuation_case":
        dcf["valueVisibility"] = "diagnostic_only"
        dcf["caseStatus"] = "challenged_diagnostic"
        dcf["displayPolicy"] = (
            "Do not present estimatedValuePerShare as a clean user-facing intrinsic value; "
            "show it only as a challenged diagnostic value after caveated continuation, valuation-detail request, or audit/debug request."
        )
        return
    dcf["valueVisibility"] = "clean_user_facing"
    dcf["caseStatus"] = valuation_case_status or "clean_valuation_case"


def extract_assumptions(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    operating = _dict(transparency.get("operatingAssumptions"))
    discount = _dict(transparency.get("discountRate"))
    terminal = _dict(valuation.get("terminalValueDTO"))
    financial = _dict(valuation.get("financialDTO"))
    tax_rate = _first_present(operating.get("taxRate"), _last_number(financial.get("taxRate")))
    return {
        "growth": {
            "revenueGrowthRateYears2To5": _first_present(
                operating.get("revenueGrowthRateYears2To5"),
                _last_number(financial.get("revenueGrowthRate")),
            ),
            "source": operating.get("revenueGrowthSource"),
            "rationale": operating.get("revenueGrowthRationale"),
        },
        "margin": {
            "operatingMarginNextYear": operating.get("operatingMarginNextYear"),
            "targetOperatingMargin": _first_present(
                operating.get("targetOperatingMargin"),
                _last_number(financial.get("ebitOperatingMargin")),
            ),
            "convergenceYearMargin": operating.get("convergenceYearMargin"),
            "source": operating.get("operatingMarginSource"),
            "rationale": operating.get("operatingMarginRationale"),
        },
        "salesToCapital": {
            "years1To5": _first_present(
                operating.get("salesToCapitalYears1To5"),
                _last_number(financial.get("salesToCapitalRatio")),
            ),
            "years6To10": operating.get("salesToCapitalYears6To10"),
            "source": operating.get("salesToCapitalSource"),
            "rationale": operating.get("salesToCapitalRationale"),
        },
        "costOfCapital": {
            "riskFreeRate": discount.get("riskFreeRate"),
            "initialCostOfCapital": _first_present(
                discount.get("initialCostOfCapital"),
                _first_number(financial.get("costOfCapital")),
            ),
            "terminalCostOfCapital": _first_present(
                discount.get("terminalCostOfCapital"),
                terminal.get("costOfCapital"),
            ),
            "source": {
                "riskFreeRate": discount.get("riskFreeRateSource"),
                "equityRiskPremium": discount.get("equityRiskPremiumSource"),
                "initialCostOfCapital": discount.get("initialCostOfCapitalSource"),
            },
        },
        "terminalGrowth": {
            "rate": terminal.get("growthRate"),
            "limitNote": "Compare terminal growth to inflation and mature economy growth before presenting scenarios.",
        },
        "taxRate": tax_rate,
        "accountingAdjustments": extract_accounting_and_claims(valuation),
        "source": "valuation-service",
        "rationale": {
            "templateSelection": valuation.get("templateSelectionReason")
            or transparency.get("templateSelectionReason"),
            "modelSelection": valuation.get("modelSelectionRationale"),
        },
    }


def effective_assumptions(valuation: dict[str, Any]) -> dict[str, Any]:
    assumptions = extract_assumptions(valuation)
    return {
        "revenue_growth": assumptions["growth"]["revenueGrowthRateYears2To5"],
        "operating_margin_next_year": assumptions["margin"]["operatingMarginNextYear"],
        "operating_margin": assumptions["margin"]["targetOperatingMargin"],
        "target_operating_margin": assumptions["margin"]["targetOperatingMargin"],
        "margin_convergence_year": assumptions["margin"]["convergenceYearMargin"],
        "sales_to_capital": assumptions["salesToCapital"]["years1To5"],
        "sales_to_capital_years_1_to_5": assumptions["salesToCapital"]["years1To5"],
        "sales_to_capital_years_6_to_10": assumptions["salesToCapital"]["years6To10"],
        "wacc": assumptions["costOfCapital"]["initialCostOfCapital"],
        "terminal_growth": assumptions["terminalGrowth"]["rate"],
        "tax_rate": assumptions["taxRate"],
    }


def extract_accounting_and_claims(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    accounting = _dict(transparency.get("accountingAndClaims"))
    if not accounting:
        return {}
    return sanitize_for_agent(accounting)


def stored_revealed_thesis(run: dict[str, Any] | None) -> dict[str, Any] | None:
    thesis = _dict(_dict(run).get("revealed_thesis"))
    if thesis.get("schema_version") != "revealed_thesis.v1":
        return None
    return copy.deepcopy(thesis)


def attach_valuation_audit_packet(
    payload: dict[str, Any],
    *,
    ticker: str,
    assumption_meta: dict[str, Any],
    recalculate_status: str,
) -> None:
    metadata = _dict(assumption_meta.get("metadata"))
    requested = _dict(assumption_meta.get("requested"))
    mapped = _dict(assumption_meta.get("mapped"))
    unsupported = _dict(assumption_meta.get("unsupported"))
    effective = _dict(assumption_meta.get("effective"))
    evidence_packet = _dict(metadata.get("evidence_packet")) or missing_evidence_packet_audit_result(unsupported)
    baseline = _dict(payload.get("baseline"))
    dcf = _dict(payload.get("dcf"))
    company = _string_or_none(dcf.get("companyName")) or ticker
    request_policy = _dict(metadata.get("request_policy")) or _dict(requested.get("request_policy"))
    run_mode = _string_or_none(request_policy.get("mode")) or "recalculate"
    final_case_type = derive_final_case_type(assumption_meta)
    audit_validation = build_valuation_audit_packet(
        ticker=ticker,
        company=company,
        run_mode=run_mode,
        evidence_packet=evidence_packet,
        segment_validation=segment_validation_from_baseline(baseline),
        baseline_plausibility=_dict(metadata.get("baseline_plausibility"))
        or default_baseline_plausibility(baseline),
        assumption_judgment=_dict(metadata.get("assumption_judgment")) or default_assumption_judgment(),
        recalculate_payloads=[
            {
                "kind": run_mode,
                "requested": requested,
                "mapped": mapped,
                "unsupported": unsupported,
                "metadata": metadata,
                "effective": effective,
                "status": recalculate_status,
            }
        ],
        assumption_buckets={
            "requested": requested,
            "mapped": mapped,
            "unsupported": unsupported,
            "metadata": metadata,
            "effective": effective,
        },
        guided_refinement=guided_refinement_status(requested, metadata, run_mode),
        final_case_type=final_case_type,
        final_report_inputs={
            "final_case_type": final_case_type,
            "educational_use_only": True,
            "not_financial_advice": True,
            "summary": final_case_summary(final_case_type),
            "source_quality_gate": _dict(payload.get("sourceQualityGate")),
        },
        data_quality_limitations=data_quality_limitations(payload),
        mcp_call_references=[
            {
                "tool": payload.get("tool") or "stockvaluation.recalculate",
                "ticker": ticker,
                "status": "ok" if payload.get("ok") else "error",
                "service_call_status": recalculate_status,
            }
        ],
        accounting_decisions=accounting_decisions_from_assumptions(assumption_meta, payload),
        internal_state=mechanical_baseline_internal_state(baseline),
    )
    revealed_thesis = _dict(payload.get("revealedThesis"))
    if revealed_thesis:
        audit_validation["packet"]["revealed_thesis"] = copy.deepcopy(revealed_thesis)
    payload["auditPacket"] = valuation_audit_packet_metadata(audit_validation)
    if not audit_validation.get("ok"):
        payload["auditPacket"]["validation_warnings"] = audit_validation.get("validation_warnings", [])


def attach_scenario_book(
    payload: dict[str, Any],
    *,
    ticker: str,
    assumption_meta: dict[str, Any],
    recalculate_status: str,
) -> None:
    audit_packet = _dict(_dict(payload.get("auditPacket")).get("packet"))
    dcf = _dict(payload.get("dcf"))
    company = _string_or_none(dcf.get("companyName")) or ticker
    guided_refinement = _dict(audit_packet.get("guided_refinement")) or guided_refinement_status(
        _dict(assumption_meta.get("requested")),
        _dict(assumption_meta.get("metadata")),
        _scenario_request_mode(assumption_meta) or "recalculate",
    )
    guided_refinement = dict(guided_refinement)
    if guided_refinement.get("status") == "completed":
        if recalculate_status == "executed" and not _string_or_none(guided_refinement.get("final_recalculate_reference")):
            guided_refinement["final_recalculate_reference"] = "recalculate_payload:0"
        elif recalculate_status != "executed":
            guided_refinement["status"] = "blocked"
            guided_refinement["block_reason"] = "recalculate blocked before final user-refined scenario execution"
    scenario = scenario_book_entry(payload, ticker, assumption_meta, audit_packet, recalculate_status)
    scenarios = [scenario] if scenario is not None else []
    main_scenario_id = scenario.get("scenario_id") if scenario else None
    book_status = scenario_book_status(
        payload=payload,
        guided_refinement=guided_refinement,
        scenarios=scenarios,
        recalculate_status=recalculate_status,
    )
    book = {
        "ticker": ticker,
        "company": company,
        "run_mode": _string_or_none(audit_packet.get("run_mode")) or _scenario_request_mode(assumption_meta) or "recalculate",
        "status": book_status,
        "main_scenario_id": main_scenario_id,
        "guided_refinement": guided_refinement,
        "scenarios": scenarios,
        "diagnostics": scenario_book_diagnostics(payload),
        "internal_references": scenario_book_internal_references(payload, audit_packet),
        "provenance_summary": scenario_book_provenance_summary(payload),
        "policy": {
            "educational_use_only": True,
            "not_financial_advice": True,
            "prohibited_recommendation_language": ["buy", "sell", "hold", "target price"],
        },
    }
    revealed_thesis = _dict(payload.get("revealedThesis"))
    if revealed_thesis:
        book["revealed_thesis"] = copy.deepcopy(revealed_thesis)
    validation = validate_scenario_book(book)
    payload["scenarioBook"] = scenario_book_metadata(validation)
    if not validation.get("ok"):
        payload["scenarioBook"]["validation_warnings"] = validation.get("validation_warnings", [])


def scenario_book_entry(
    payload: dict[str, Any],
    ticker: str,
    assumption_meta: dict[str, Any],
    audit_packet: dict[str, Any],
    recalculate_status: str,
) -> dict[str, Any] | None:
    if recalculate_status != "executed" or not payload.get("ok"):
        return None

    request_mode = _scenario_request_mode(assumption_meta)
    final_case_type = _string_or_none(_dict(_dict(payload.get("auditPacket")).get("summary")).get("final_case_type"))
    if request_mode == "user_refined_scenario":
        scenario_id = "user_refined"
        scenario_type = "user_refined_scenario"
        label = "User-refined scenario"
        source = "guided_user_judgment"
        explicit_intent = None
    elif request_mode == "explicit_scenario":
        scenario_id = "explicit_scenario"
        scenario_type = "explicit_scenario"
        label = "Explicit scenario"
        source = "explicit_user_request"
        explicit_intent = _string_or_none(_dict(_dict(assumption_meta.get("metadata")).get("request_policy")).get("explicit_user_intent"))
        if explicit_intent is None:
            explicit_intent = "Explicit user-requested supported scenario outside guided refinement."
    elif final_case_type == "insufficient_researched_evidence":
        return None
    else:
        scenario_id = "evidence_base"
        scenario_type = "evidence_constrained_base"
        label = "Evidence-constrained base"
        source = "evidence_constrained_workflow"
        explicit_intent = None

    assumptions = {
        "requested": _dict(assumption_meta.get("requested")),
        "mapped": _dict(assumption_meta.get("mapped")),
        "unsupported": _dict(assumption_meta.get("unsupported")),
        "metadata": _dict(assumption_meta.get("metadata")),
        "effective": _dict(assumption_meta.get("effective")),
    }
    scenario: dict[str, Any] = {
        "scenario_id": scenario_id,
        "label": label,
        "type": scenario_type,
        "status": "completed",
        "visibility": "user_facing",
        "source": source,
        "assumption_deltas": scenario_assumption_deltas(assumptions),
        "assumptions": assumptions,
        "payload_reference": "recalculate_payload:0",
        "service_response_reference": f"service_response:{ticker}:{recalculate_status}",
        "audit_packet_reference": _string_or_none(_dict(payload.get("auditPacket")).get("reference")),
        "evidence_packet_reference": evidence_packet_reference(audit_packet),
        "provenance_references": provenance_references(payload),
        "segment_economics_status": scenario_segment_economics_status(payload, assumption_meta),
        "accounting_claims_status": scenario_accounting_claims_status(payload, assumption_meta),
        "warnings": payload.get("warnings") or [],
        "limitations": data_quality_limitations(payload),
    }
    if explicit_intent is not None:
        scenario["explicit_user_intent"] = explicit_intent
    return sanitize_for_agent(scenario)


def scenario_book_status(
    *,
    payload: dict[str, Any],
    guided_refinement: dict[str, Any],
    scenarios: list[dict[str, Any]],
    recalculate_status: str,
) -> str:
    if recalculate_status != "executed" or not payload.get("ok"):
        final_case_type = _string_or_none(_dict(_dict(payload.get("auditPacket")).get("summary")).get("final_case_type"))
        return "insufficient_evidence" if final_case_type == "insufficient_researched_evidence" else "blocked"
    if guided_refinement.get("status") == "bypassed":
        return "completed_with_bypass"
    if not scenarios:
        return "partial"
    return "completed"


def scenario_book_diagnostics(payload: dict[str, Any]) -> list[dict[str, Any]]:
    valuation = _dict(payload.get("valuation"))
    transparency = _dict(valuation.get("assumptionTransparency"))
    diagnostics: list[dict[str, Any]] = []
    market_implied = transparency.get("marketImpliedExpectations")
    if market_implied is not None:
        diagnostics.append(
            {
                "diagnostic_id": "market_implied",
                "label": "Market-implied expectations",
                "type": "market_implied_diagnostic",
                "status": "available",
                "visibility": "diagnostic_only",
                "source": "market_implied_diagnostics",
                "model_action": "diagnostic_only",
                "evidence_status": "not_evidence",
                "payload_reference": "valuation.assumptionTransparency.marketImpliedExpectations",
                "data": sanitize_for_agent(market_implied),
                "warnings": [],
            }
        )
    priced_in = transparency.get("pricedInExpectations")
    if priced_in is not None:
        diagnostics.append(
            {
                "diagnostic_id": "priced_in",
                "label": "Priced-in expectations",
                "type": "priced_in_diagnostic",
                "status": "available",
                "visibility": "diagnostic_only",
                "source": "market_implied_diagnostics",
                "model_action": "diagnostic_only",
                "evidence_status": "not_evidence",
                "payload_reference": "valuation.assumptionTransparency.pricedInExpectations",
                "data": sanitize_for_agent(priced_in),
                "warnings": [],
            }
        )
    return diagnostics


def scenario_book_internal_references(payload: dict[str, Any], audit_packet: dict[str, Any]) -> dict[str, Any]:
    baseline = _dict(payload.get("baseline"))
    audit_reference = _string_or_none(_dict(payload.get("auditPacket")).get("reference"))
    return {
        "mechanical_baseline": {
            "visibility": "internal_only",
            "reference": f"mechanical_baseline:{_string_or_none(payload.get('ticker')) or 'unknown'}:{baseline.get('baselineUseStatus') or 'unknown'}",
            "baseline_quality": baseline.get("baselineQuality") or "not_calculated",
            "baseline_use_status": baseline.get("baselineUseStatus") or "unknown",
        },
        "valuation_audit_packet_reference": audit_reference,
        "evidence_packet_reference": evidence_packet_reference(audit_packet),
        "recalculate_payload_references": ["recalculate_payload:0"],
        "service_response_references": ["service_response:not_called"] if not payload.get("ok") else ["service_response:latest"],
    }


def scenario_book_provenance_summary(payload: dict[str, Any]) -> dict[str, Any]:
    provenance = _dict(payload.get("provenance"))
    source_class = _string_or_none(provenance.get("sourceClass"))
    source_date = _string_or_none(provenance.get("sourceDate"))
    return {
        "source_classes": [source_class] if source_class else [],
        "source_dates": [source_date] if source_date else [],
        "data_quality_warnings": provenance.get("dataQualityWarnings") or [],
        "missing_source_families": [],
        "source_policy_status": provenance.get("sourcePolicyStatus"),
        "source_quality_gate": _dict(payload.get("sourceQualityGate")),
    }


def scenario_assumption_deltas(assumptions: dict[str, Any]) -> list[dict[str, Any]]:
    requested = _dict(assumptions.get("requested"))
    effective = _dict(assumptions.get("effective"))
    deltas: list[dict[str, Any]] = []
    for key, requested_value in requested.items():
        if key in RECALCULATE_METADATA_FIELDS or key == "request_policy":
            continue
        delta = {
            "field": key,
            "requested": sanitize_for_agent(requested_value),
            "effective": sanitize_for_agent(effective.get(key)),
        }
        if key in _dict(assumptions.get("unsupported")):
            delta["status"] = "unsupported"
        else:
            delta["status"] = "mapped_or_metadata"
        deltas.append(delta)
    return deltas


def scenario_segment_economics_status(payload: dict[str, Any], assumption_meta: dict[str, Any]) -> dict[str, Any]:
    metadata = _dict(assumption_meta.get("metadata"))
    segment_economics = _dict(metadata.get("segment_economics"))
    if segment_economics:
        return segment_economics
    baseline = _dict(payload.get("baseline"))
    return {
        "status": baseline.get("baselineUseStatus") or "unknown",
        "baseline_quality": baseline.get("baselineQuality") or "not_calculated",
        "segment_aware": bool(baseline.get("segmentAware")),
        "segment_coverage_pct": baseline.get("segmentCoveragePct"),
        "mapped_industries": baseline.get("mappedIndustries") or [],
        "limitations": baseline.get("baselineWarnings") or [],
    }


def scenario_accounting_claims_status(payload: dict[str, Any], assumption_meta: dict[str, Any]) -> dict[str, Any]:
    metadata = _dict(_dict(assumption_meta.get("metadata")).get("accounting_and_claims"))
    accounting = dict(_dict(payload.get("accountingAndClaims")))
    governed = [
        item for item in metadata.get("governed_scenarios", [])
        if isinstance(item, dict)
    ]
    for item in governed:
        if item.get("topic") == "rd_capitalization":
            rd_status = dict(_dict(accounting.get("rdCapitalization")))
            rd_status["status"] = item.get("status") or "governed_scenario_supported"
            rd_status["modelTreatment"] = "governed_scenario"
            rd_status["scenario"] = item
            accounting["rdCapitalization"] = rd_status
    if accounting:
        return accounting
    return metadata


def evidence_packet_reference(audit_packet: dict[str, Any]) -> str | None:
    evidence_packet = _dict(audit_packet.get("evidence_packet"))
    status = _string_or_none(evidence_packet.get("status"))
    if status is None:
        return None
    return f"evidence_packet:{status}"


def provenance_references(payload: dict[str, Any]) -> list[str]:
    provenance = _dict(payload.get("provenance"))
    status = _string_or_none(provenance.get("sourcePolicyStatus"))
    return [f"source_provenance:{status}"] if status else []


def _scenario_request_mode(assumption_meta: dict[str, Any]) -> str | None:
    metadata = _dict(assumption_meta.get("metadata"))
    request_policy = _dict(metadata.get("request_policy")) or _dict(_dict(assumption_meta.get("requested")).get("request_policy"))
    return _string_or_none(request_policy.get("mode")) or _string_or_none(_dict(assumption_meta.get("mapped")).get("requestPolicyMode"))


def missing_evidence_packet_audit_result(unsupported: dict[str, Any]) -> dict[str, Any]:
    evidence_blocker = _dict(unsupported.get("evidence_packet"))
    status = _string_or_none(evidence_blocker.get("status")) or "missing_evidence_packet"
    blockers = [evidence_blocker] if evidence_blocker else []
    return {
        "ok": False,
        "status": status,
        "sanitized_packet": {},
        "governed_evidence": [],
        "report_only_evidence": [],
        "rejected_evidence": [],
        "source_family_status": [],
        "validation_warnings": ["EvidencePacket was not provided to the recalculate audit boundary."],
        "unsupported_blockers": blockers,
    }


def segment_validation_from_baseline(baseline: dict[str, Any]) -> dict[str, Any]:
    return {
        "baseline_quality": baseline.get("baselineQuality") or "not_calculated",
        "baseline_use_status": baseline.get("baselineUseStatus") or "unknown",
        "segment_aware": bool(baseline.get("segmentAware")),
        "segment_count": _int_or_zero(baseline.get("segmentCount")),
        "segment_coverage_pct": _number_or_none(baseline.get("segmentCoveragePct")) or 0.0,
        "mapped_industries": baseline.get("mappedIndustries") or [],
        "validation_warnings": baseline.get("baselineWarnings") or [],
    }


def default_baseline_plausibility(baseline: dict[str, Any]) -> dict[str, Any]:
    return {
        "status": "not_provided",
        "baseline_use_status": baseline.get("baselineUseStatus") or "unknown",
        "unsupported_blockers": baseline.get("unsupportedBaselineDrivers") or [],
    }


def default_assumption_judgment() -> dict[str, Any]:
    return {
        "status": "not_provided",
        "assumptions_left_unchanged": [],
    }


def guided_refinement_status(
    requested: dict[str, Any],
    metadata: dict[str, Any],
    run_mode: str,
) -> dict[str, Any]:
    explicit = _dict(metadata.get("guided_refinement"))
    if explicit:
        return explicit
    user_judgment = metadata.get("user_judgment")
    if run_mode == "user_refined_scenario" or user_judgment is not None:
        return {
            "status": "completed",
            "bypass_reason": None,
            "user_judgment": user_judgment,
        }
    request_policy = _dict(metadata.get("request_policy")) or _dict(requested.get("request_policy"))
    bypass_reason = _string_or_none(request_policy.get("guided_refinement_bypass_reason")) or _string_or_none(
        request_policy.get("bypass_reason")
    )
    bypass_status = _string_or_none(request_policy.get("guided_refinement"))
    if bypass_reason or bypass_status == "bypassed" or bool(request_policy.get("guided_refinement_bypassed")):
        return {
            "status": "bypassed",
            "bypass_reason": bypass_reason or "guided refinement bypassed by request",
            "user_judgment": None,
        }
    return {
        "status": "not_started",
        "bypass_reason": None,
        "user_judgment": None,
    }


def derive_final_case_type(assumption_meta: dict[str, Any]) -> str:
    metadata = _dict(assumption_meta.get("metadata"))
    mapped = _dict(assumption_meta.get("mapped"))
    unsupported = _dict(assumption_meta.get("unsupported"))
    request_policy = _dict(metadata.get("request_policy")) or _dict(_dict(assumption_meta.get("requested")).get("request_policy"))
    request_mode = _string_or_none(request_policy.get("mode")) or _string_or_none(mapped.get("requestPolicyMode"))
    if request_mode == "user_refined_scenario":
        return "user_refined_scenario"
    if unsupported:
        return "insufficient_researched_evidence"
    if request_mode == "explicit_scenario" and mapped.get("isExpensesCapitalize") is True:
        return "evidence_constrained_governed_recalculation"
    if request_mode == "autonomous_researched":
        if has_governed_recalculate_change(mapped):
            return "evidence_constrained_governed_recalculation"
        return "evidence_constrained_no_change"
    return "evidence_constrained_no_change"


def has_governed_recalculate_change(mapped: dict[str, Any]) -> bool:
    governed_fields = {
        "compoundAnnualGrowth2_5",
        "targetPreTaxOperatingMargin",
        "salesToCapitalYears1To5",
        "salesToCapitalYears6To10",
        "sectorOverrides",
        "isExpensesCapitalize",
    }
    return any(field in mapped for field in governed_fields)


def final_case_summary(final_case_type: str) -> str:
    return {
        "evidence_constrained_no_change": "Evidence-constrained no-change case; no governed recalculation inputs were accepted.",
        "evidence_constrained_governed_recalculation": "Evidence-constrained governed recalculation.",
        "user_refined_scenario": "User-refined scenario based on bounded user judgment, not external evidence.",
        "insufficient_researched_evidence": "Insufficient researched evidence for a user-facing valuation case.",
    }[final_case_type]


def data_quality_limitations(payload: dict[str, Any]) -> list[Any]:
    limitations: list[Any] = []
    limitations.extend(payload.get("warnings") or [])
    baseline = _dict(payload.get("baseline"))
    limitations.extend(baseline.get("baselineWarnings") or [])
    provenance = _dict(payload.get("provenance"))
    limitations.extend(provenance.get("warnings") or [])
    limitations.extend(provenance.get("dataQualityWarnings") or [])
    return limitations


def accounting_decisions_from_assumptions(
    assumption_meta: dict[str, Any],
    payload: dict[str, Any],
) -> dict[str, Any]:
    requested = _dict(assumption_meta.get("requested"))
    mapped = _dict(assumption_meta.get("mapped"))
    unsupported = _dict(assumption_meta.get("unsupported"))
    metadata = _dict(assumption_meta.get("metadata"))
    accounting_metadata = _dict(metadata.get("accounting_and_claims"))
    accounting_requested = {
        key: value
        for key, value in requested.items()
        if key in REPORT_ONLY_OVERRIDE_FIELDS
    }
    accounting_mapped = {
        key: value
        for key, value in mapped.items()
        if key in {
            "isExpensesCapitalize",
            "rdAmortizationMethod",
            "rdAmortizationPeriodYears",
        }
    }
    accounting_unsupported = {
        key: value
        for key, value in unsupported.items()
        if key in REPORT_ONLY_OVERRIDE_FIELDS
    }
    accounting_output = _dict(payload.get("accountingAndClaims"))
    return {
        "requested": accounting_requested,
        "mapped": accounting_mapped,
        "unsupported": accounting_unsupported,
        "report_only": accounting_metadata.get("report_only_diagnostics", []),
        "governed_scenarios": accounting_metadata.get("governed_scenarios", []),
        "rejected": accounting_metadata.get("rejected_claims", []),
        "metadata": accounting_metadata,
        "effective": accounting_output.get("effectiveAccountingDecisions", []),
    }


def mechanical_baseline_internal_state(baseline: dict[str, Any]) -> dict[str, Any]:
    return {
        "mechanical_baseline": {
            "visibility": "internal_only",
            "baseline_quality": baseline.get("baselineQuality") or "not_calculated",
            "baseline_use_status": baseline.get("baselineUseStatus") or "unknown",
        }
    }


def extract_growth_anchor(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    anchor = _dict(transparency.get("growthAnchor") or valuation.get("growthSkillContext"))
    warnings = []
    confidence = anchor.get("confidenceScore")
    if confidence is None:
        warnings.append("No growth-anchor confidence score was returned by valuation-service.")
    elif isinstance(confidence, (int, float)) and confidence < 0.5:
        warnings.append("Growth-anchor confidence is weak; treat industry comparison as directional.")
    return {
        "mappedEntity": anchor.get("entity"),
        "mappedEntityDisplay": anchor.get("entityDisplay"),
        "region": anchor.get("region"),
        "year": anchor.get("year"),
        "confidence": confidence,
        "percentileBand": {
            "p25": anchor.get("p25"),
            "p50": anchor.get("p50"),
            "p75": anchor.get("p75"),
        },
        "sourceDate": anchor.get("sourceDate") or anchor.get("year"),
        "source": anchor.get("source") or "valuation-service growth anchor",
        "warnings": warnings,
    }


def extract_source_provenance(valuation: dict[str, Any]) -> dict[str, Any]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    source = _dict(transparency.get("sourceProvenance"))
    if not source:
        return {}
    return {
        "sourceClass": source.get("sourceClass"),
        "provider": source.get("provider"),
        "sourceDate": source.get("sourceDate"),
        "periodEnd": source.get("periodEnd"),
        "retrievalStatus": source.get("retrievalStatus"),
        "crossCheckStatus": source.get("crossCheckStatus"),
        "sourcePolicyStatus": source.get("sourcePolicyStatus"),
        "warnings": _string_list(source.get("warnings")),
        "dataQualityWarnings": _data_quality_warning_list(source.get("dataQualityWarnings")),
    }


def extract_source_quality_gate(valuation: dict[str, Any]) -> dict[str, Any]:
    gate = _dict(valuation.get("sourceQualityGate"))
    if not gate:
        transparency = _dict(valuation.get("assumptionTransparency"))
        gate = _dict(transparency.get("sourceQualityGate"))
    if not gate:
        return {}
    return {
        "status": gate.get("status"),
        "reason": gate.get("reason"),
        "primarySourceExpected": bool(gate.get("primarySourceExpected")),
        "fallbackSourceAvailable": bool(gate.get("fallbackSourceAvailable")),
        "crossCheckRequired": bool(gate.get("crossCheckRequired")),
        "allowedActions": _string_list(gate.get("allowedActions")),
    }


def apply_request_policy_source_quality_gate(payload: dict[str, Any], assumption_meta: dict[str, Any]) -> None:
    if _dict(payload.get("sourceQualityGate")):
        return
    metadata = _dict(assumption_meta.get("metadata"))
    requested = _dict(assumption_meta.get("requested"))
    request_policy = _dict(metadata.get("request_policy")) or _dict(requested.get("request_policy"))
    gate = source_quality_gate_from_request_policy(request_policy)
    if gate:
        payload["sourceQualityGate"] = gate


def source_quality_gate_from_request_policy(request_policy: dict[str, Any]) -> dict[str, Any]:
    gate = normalize_source_quality_gate(
        _dict(request_policy.get("source_quality_gate")) or _dict(request_policy.get("sourceQualityGate"))
    )
    if gate:
        return gate
    status = _string_or_none(
        _first_present(
            request_policy.get("source_quality_gate_status"),
            request_policy.get("sourceQualityGateStatus"),
            request_policy.get("source_quality_gate_bypass"),
            request_policy.get("sourceQualityGateBypass"),
        )
    )
    if status not in SOURCE_QUALITY_GATE_BYPASS_STATUSES:
        return {}
    return {
        "status": status,
        "reason": _string_or_none(
            _first_present(request_policy.get("source_quality_gate_reason"), request_policy.get("sourceQualityGateReason"))
        ) or status,
        "primarySourceExpected": bool(request_policy.get("primarySourceExpected")),
        "fallbackSourceAvailable": bool(request_policy.get("fallbackSourceAvailable")),
        "crossCheckRequired": bool(request_policy.get("crossCheckRequired")),
        "allowedActions": _string_list(request_policy.get("allowedActions")),
    }


def normalize_source_quality_gate(gate: dict[str, Any]) -> dict[str, Any]:
    status = _string_or_none(gate.get("status"))
    if not status:
        return {}
    return {
        "status": status,
        "reason": _string_or_none(gate.get("reason")) or status,
        "primarySourceExpected": bool(gate.get("primarySourceExpected")),
        "fallbackSourceAvailable": bool(gate.get("fallbackSourceAvailable")),
        "crossCheckRequired": bool(gate.get("crossCheckRequired")),
        "allowedActions": _string_list(gate.get("allowedActions")),
    }


def reference_data_status(valuation: dict[str, Any]) -> dict[str, Any]:
    anchor = extract_growth_anchor(valuation)
    return {
        "valuationServiceUrl": DEFAULT_SERVICE_URL,
        "marketData": {
            "provider": "Yahoo Finance via local yfinance service",
            "status": "queried_by_valuation_service" if valuation else "unknown_until_ticker_request",
            "warnings": ["Yahoo Finance coverage can be missing, stale, or insufficient for some companies."],
        },
        "damodaranReferenceData": {
            "status": "available_when_growth_anchor_present" if anchor.get("mappedEntity") else "not_returned",
            "mappedEntity": anchor.get("mappedEntity"),
            "region": anchor.get("region"),
            "year": anchor.get("year"),
            "sourceDate": anchor.get("sourceDate"),
            "confidence": anchor.get("confidence"),
            "warnings": anchor.get("warnings", []),
        },
    }


def version_metadata(valuation: dict[str, Any]) -> dict[str, Any]:
    return {
        "mcp": mcp_metadata(),
        "valuationService": {
            "serviceVersion": valuation.get("serviceVersion"),
            "dataVersion": valuation.get("dataVersion"),
            "modelVersion": valuation.get("modelVersion"),
        },
    }


def mcp_metadata() -> dict[str, Any]:
    return {
        "name": "valuation-agent",
        "version": __version__,
        "protocolVersion": SUPPORTED_PROTOCOL_VERSIONS[0],
        "supportedProtocolVersions": list(SUPPORTED_PROTOCOL_VERSIONS),
    }


def skill_metadata(home: Any | None = None) -> dict[str, Any]:
    try:
        from .installer import AgentInstaller, skill_bundle_version

        installs = AgentInstaller(home=home).verify_skills(["all"])
        bundled_version = skill_bundle_version()
    except Exception:
        return {"installedVersion": "unknown", "syncStatus": "unknown", "installs": {}}
    resolvable = {
        client: report
        for client, report in installs.items()
        if report.get("status") != "not_installed"
    }
    if not resolvable:
        return {
            "installedVersion": "unknown",
            "syncStatus": "not_installed",
            "bundledVersion": bundled_version,
            "installs": installs,
        }
    versions = {report.get("version") for report in resolvable.values() if report.get("version")}
    installed_version = versions.pop() if len(versions) == 1 else "unknown"
    statuses = {report.get("status") for report in resolvable.values()}
    sync_status = "drifted" if "drifted" in statuses else "in_sync"
    return {
        "installedVersion": installed_version,
        "syncStatus": sync_status,
        "bundledVersion": bundled_version,
        "installs": installs,
    }


def policy_metadata(baseline_entrypoint: str | None = None) -> dict[str, Any]:
    policy = {
        "educationalUseOnly": True,
        "notFinancialAdvice": True,
        "reportWriter": "user-agent",
        "prohibitedRecommendationLanguage": ["buy", "sell", "hold", "target price", "should invest"],
    }
    if baseline_entrypoint:
        policy["baselineEntrypoint"] = baseline_entrypoint
    return policy


def baseline_entrypoint_for_tool(tool: str) -> str | None:
    if tool == "stockvaluation.researched_baseline":
        return "researched_baseline"
    if tool == "stockvaluation.value_ticker":
        return "mechanical_baseline"
    return None


def extract_warnings(valuation: dict[str, Any]) -> list[str]:
    transparency = _dict(valuation.get("assumptionTransparency"))
    notes = transparency.get("notes")
    if isinstance(notes, list):
        return [str(item) for item in notes]
    return []


def service_exception_payload(tool: str, exc: Exception, ticker: str | None = None) -> dict[str, Any]:
    if isinstance(exc, ServiceUnavailable):
        return error_payload(
            tool,
            "MISSING_LOCAL_SERVICE",
            "Local valuation service is not reachable.",
            "missing_local_service",
            extra={"ticker": ticker, "detail": sanitize_for_agent(str(exc))},
        )
    if isinstance(exc, NonJsonServiceResponse):
        return error_payload(
            tool,
            "NON_JSON_SERVICE_RESPONSE",
            "Local valuation service returned a non-JSON response.",
            "non_json_service_response",
            extra={"ticker": ticker, "detail": sanitize_for_agent(str(exc))},
        )
    if isinstance(exc, ServiceHTTPError):
        category = classify_failure(exc.message)
        if category == "unknown_failure" and exc.status >= 500:
            category = "upstream_service_error"
        return error_payload(
            tool,
            failure_code_for_category(category),
            exc.message,
            category,
            extra={"ticker": ticker, "status": exc.status, "upstream": sanitize_for_agent(exc.payload or {})},
        )
    if isinstance(exc, ValuationServiceError):
        category = classify_failure(str(exc))
        return error_payload(
            tool,
            failure_code_for_category(category),
            str(sanitize_for_agent(str(exc))),
            category,
            extra={"ticker": ticker},
        )
    return error_payload(tool, "VALUATION_SERVICE_ERROR", str(sanitize_for_agent(str(exc))), "unknown_failure")


def explain_failure(error: Any) -> dict[str, Any]:
    message = extract_failure_message(error)
    existing_category = extract_failure_category(error)
    category = existing_category if existing_category and existing_category != "unknown_failure" else classify_failure(message)
    if category == "unknown_failure" and existing_category:
        category = existing_category
    return {
        "ok": True,
        "tool": "stockvaluation.explain_failure",
        "failureCategory": category,
        "message": sanitize_for_agent(message),
        "recovery": recovery_for_category(category),
    }


def extract_failure_category(error: Any) -> str | None:
    if isinstance(error, str):
        stripped = error.strip()
        if not stripped:
            return None
        try:
            return extract_failure_category(json.loads(stripped))
        except json.JSONDecodeError:
            return None
    if isinstance(error, dict):
        for key in ("failureCategory", "failure_category", "code"):
            category = normalize_failure_category(error.get(key))
            if category:
                return category
        nested = error.get("error")
        if isinstance(nested, dict):
            for key in ("failureCategory", "failure_category", "code"):
                category = normalize_failure_category(nested.get(key))
                if category:
                    return category
    return None


def normalize_failure_category(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    normalized = value.strip().lower().replace("-", "_")
    if normalized in KNOWN_FAILURE_CATEGORIES:
        return normalized
    return None


def extract_failure_message(error: Any) -> str:
    if isinstance(error, str):
        stripped = error.strip()
        if stripped:
            try:
                return extract_failure_message(json.loads(stripped))
            except json.JSONDecodeError:
                return stripped
        return str(error)
    if isinstance(error, dict):
        direct = error.get("message")
        if isinstance(direct, str) and direct.strip():
            return direct
        nested = error.get("error")
        if isinstance(nested, dict):
            nested_message = nested.get("message") or nested.get("error")
            if isinstance(nested_message, str) and nested_message.strip():
                return nested_message
            nested_code = nested.get("code")
            if isinstance(nested_code, str) and nested_code.strip():
                return nested_code
        if isinstance(nested, str) and nested.strip():
            return nested
        code = error.get("code")
        if isinstance(code, str) and code.strip():
            return code
    return str(error)


def classify_failure(message: str) -> str:
    lowered = message.lower()
    if (
        "currency" in lowered
        and any(term in lowered for term in ("conversion failed", "differs", "convert", "exchange-rate"))
    ):
        return "currency_conversion_failed"
    if (
        ("frankfurter" in lowered or "currency provider" in lowered)
        and any(term in lowered for term in ("unavailable", "failed", "missing", "rate", "loading"))
    ):
        return "currency_conversion_failed"
    if any(
        term in lowered
        for term in (
            "financial company",
            "financial sector",
            "financial services sector",
            "bank",
            "insurance",
            "unsupported",
        )
    ):
        return "unsupported_company"
    if "insufficient" in lowered or "missing financial" in lowered or "not enough financial" in lowered:
        return "insufficient_financial_data"
    if any(term in lowered for term in ("configuration", "environment variable", "required")):
        return "missing_configuration"
    if "prospectus" in lowered and "review" in lowered:
        return "prospectus_review_required"
    if "stale" in lowered and "reference" in lowered:
        return "stale_reference_data"
    if "non-json" in lowered or "non json" in lowered or "html" in lowered:
        return "non_json_service_response"
    if "connection" in lowered or "refused" in lowered or "unreachable" in lowered or "timed out" in lowered:
        return "missing_local_service"
    if "upstream" in lowered or "dependency" in lowered or "service error" in lowered:
        return "upstream_service_error"
    return "unknown_failure"


def failure_code_for_category(category: str) -> str:
    return {
        "unsupported_company": "UNSUPPORTED_COMPANY",
        "insufficient_financial_data": "INSUFFICIENT_FINANCIAL_DATA",
        "missing_configuration": "MISSING_CONFIGURATION",
        "stale_reference_data": "STALE_REFERENCE_DATA",
        "non_json_service_response": "NON_JSON_SERVICE_RESPONSE",
        "missing_local_service": "MISSING_LOCAL_SERVICE",
        "currency_conversion_failed": "CURRENCY_CONVERSION_FAILED",
        "upstream_service_error": "UPSTREAM_SERVICE_ERROR",
        "invalid_prospectus_source": "INVALID_PROSPECTUS_URL",
        "prospectus_review_required": "PROSPECTUS_REVIEW_REQUIRED",
    }.get(category, "VALUATION_SERVICE_ERROR")


def recovery_for_category(category: str) -> dict[str, Any]:
    recovery = {
        "unsupported_company": "Explain that the company type is unsupported and do not invent a DCF.",
        "insufficient_financial_data": "Tell the user which data is missing and avoid filling gaps with invented values.",
        "missing_configuration": "Ask the user to run `sv check-env` and configure the missing local environment variable.",
        "stale_reference_data": "Show the stale-data warning and treat growth anchors as directional.",
        "non_json_service_response": "Ask the user to run `sv service status`; the service may be returning an error page.",
        "missing_local_service": "Ask the user to run `sv service start` and then retry the MCP call.",
        "currency_conversion_failed": "Explain that valuation-service could not safely complete currency conversion. Ask the user to verify Frankfurter currency provider availability, then retry; do not manually convert and invent the valuation.",
        "upstream_service_error": "Tell the user the local valuation service returned an upstream error. Ask them to run `sv service status`, retry once, and preserve the failure category if it repeats.",
        "invalid_ticker": "Ask for a valid public ticker symbol.",
        "unsupported_overrides": "Ask before retrying with only governed scenario override fields.",
        "invalid_prospectus_source": "Ask for a SEC EDGAR Archives HTML prospectus URL. Do not paste raw filing HTML into the MCP tool.",
        "prospectus_review_required": "Review the extracted ProspectusFinancialPacket with the user, correct any disputed fields, then retry only after setting reviewStatus to reviewed.",
        "gate_not_cleared": "Complete the named workflow gate with the user (or record an explicit user-stated bypass via gate_records) before retrying this call.",
        "unanchored_scenario_value": "Use one of the driver's recorded anchor values, or ask the user for a specific number and declare it in value_sources as user_input. Do not invent scenario numbers.",
        "unknown_run_id": "Start a new tracked run from stockvaluation.extract_prospectus or a baseline tool and use its run_id.",
        "invalid_gate_record": "Fix the gate record fields (gate, outcome, reason) and retry. Bypasses require an explicit reason.",
        "unknown_failure": "Summarize the failure and ask the user whether to run service status checks.",
    }
    return {
        "agentAction": recovery.get(category, recovery["unknown_failure"]),
        "canRetry": category in {"missing_local_service", "non_json_service_response", "missing_configuration"},
    }


def error_payload(
    tool: str,
    code: str,
    message: str,
    category: str,
    extra: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload: dict[str, Any] = {
        "ok": False,
        "tool": tool,
        "failureCategory": category,
        "error": {
            "code": code,
            "message": sanitize_for_agent(message),
        },
        "recovery": recovery_for_category(category),
        "policy": policy_metadata(),
    }
    if extra:
        payload.update({key: sanitize_for_agent(value) for key, value in extra.items() if value is not None})
    return payload


def tool_result(payload: dict[str, Any], is_error: bool) -> dict[str, Any]:
    safe_payload = sanitize_for_agent(payload)
    return {
        "content": [
            {
                "type": "text",
                "text": compact_text_content(safe_payload, is_error),
            }
        ],
        "structuredContent": safe_payload,
        "isError": is_error,
    }


def compact_text_content(payload: dict[str, Any], is_error: bool) -> str:
    tool = str(payload.get("tool") or "stockvaluation")
    ticker = _string_or_none(payload.get("ticker"))
    subject = f"{tool} {ticker}" if ticker else tool
    if is_error or not payload.get("ok"):
        error = _dict(payload.get("error"))
        recovery = _dict(payload.get("recovery"))
        code = _string_or_none(error.get("code")) or "ERROR"
        category = _string_or_none(payload.get("failureCategory")) or "unknown_failure"
        message = _string_or_none(error.get("message")) or "The tool call failed."
        action = _string_or_none(recovery.get("agentAction"))
        parts = [
            f"{subject}: error {code} ({category}).",
            message,
        ]
        if action:
            parts.append(f"Recovery: {action}")
        parts.append("Full details are in structuredContent.")
        return " ".join(parts)

    policy = _dict(payload.get("policy"))
    policy_text = ""
    if policy.get("educationalUseOnly") or policy.get("notFinancialAdvice"):
        policy_text = " Educational use only; not financial advice."

    dcf = _dict(payload.get("dcf"))
    if dcf:
        company_name = _string_or_none(dcf.get("companyName"))
        currency = _string_or_none(dcf.get("currency")) or _string_or_none(dcf.get("stockCurrency"))
        estimated_value = compact_number(dcf.get("estimatedValuePerShare"))
        market_price = compact_number(dcf.get("marketPrice"))
        baseline = _dict(payload.get("baseline"))
        baseline_status = _string_or_none(baseline.get("baselineUseStatus"))
        hide_visible_values = hide_visible_dcf_values(payload, tool, baseline_status)
        summary = f"{subject}: ok."
        if company_name:
            summary += f" {company_name}."
        if estimated_value is not None and not hide_visible_values:
            value_text = f" Estimated value/share {estimated_value}"
            if currency:
                value_text += f" {currency}"
            summary += value_text + "."
        if market_price is not None and not hide_visible_values:
            price_text = f" Market price {market_price}"
            if currency:
                price_text += f" {currency}"
            summary += price_text + "."
        if baseline_status:
            summary += f" Baseline use {baseline_status}."
        valuation_case_status = _string_or_none(payload.get("valuationCaseStatus")) or _string_or_none(baseline.get("valuationCaseStatus"))
        valuation_basis_status = _string_or_none(payload.get("valuationBasisStatus")) or _string_or_none(baseline.get("valuationBasisStatus"))
        if hide_visible_values and tool == "stockvaluation.value_prospectus":
            summary += " No clean prospectus valuation yet."
            if estimated_value is not None:
                summary += f" Mechanical diagnostic value is about {currency_amount(estimated_value, currency)}/share."
            summary += " A story scenario is required."
            if valuation_basis_status:
                summary += f" Basis issue {valuation_basis_status}."
        price_basis = _string_or_none(payload.get("priceBasis"))
        if price_basis:
            summary += f" Price basis {price_basis}."
        provenance = _dict(payload.get("provenance"))
        source_policy_status = _string_or_none(provenance.get("sourcePolicyStatus"))
        if source_policy_status:
            summary += f" Source policy {source_policy_status}."
        return f"{summary}{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.extract_prospectus":
        prospectus = _dict(payload.get("prospectus"))
        company = _dict(prospectus.get("company"))
        filing = _dict(prospectus.get("filing"))
        gate = _dict(payload.get("sourceQualityGate"))
        company_name = _string_or_none(company.get("legalName")) or _string_or_none(company.get("name"))
        form_type = _string_or_none(filing.get("formType")) or _string_or_none(filing.get("form_type"))
        review_status = _string_or_none(prospectus.get("reviewStatus")) or "review_required"
        gate_reason = _string_or_none(gate.get("reason"))
        summary = f"{subject}: ok. Prospectus extraction requires review."
        if company_name:
            summary += f" {company_name}."
        if form_type:
            summary += f" Filing {form_type}."
        summary += f" Review status {review_status}."
        if gate_reason:
            summary += f" Source gate {gate_reason}."
        return f"{summary}{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.health":
        service = _dict(payload.get("service"))
        status = _string_or_none(service.get("status")) or "unknown"
        return f"{subject}: ok. Service status {status}.{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.get_assumptions":
        return f"{subject}: ok. Assumption transparency returned.{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.get_growth_anchor":
        anchor = _dict(payload.get("growthAnchor"))
        entity = _string_or_none(anchor.get("mappedEntityDisplay")) or _string_or_none(anchor.get("mappedEntity"))
        suffix = f" Growth anchor {entity}." if entity else " Growth anchor returned."
        return f"{subject}: ok.{suffix}{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.get_reference_data_status":
        return f"{subject}: ok. Reference-data status returned.{policy_text} Full JSON is in structuredContent."

    if tool == "stockvaluation.explain_failure":
        category = _string_or_none(payload.get("failureCategory")) or "unknown_failure"
        message = _string_or_none(payload.get("message")) or "Failure classified."
        return f"{subject}: ok. {category}: {message} Full JSON is in structuredContent."

    return f"{subject}: ok.{policy_text} Full JSON is in structuredContent."


def hide_visible_dcf_values(payload: dict[str, Any], tool: str, baseline_status: str | None) -> bool:
    audit_summary = _dict(_dict(payload.get("auditPacket")).get("summary"))
    final_case_type = _string_or_none(audit_summary.get("final_case_type"))
    if final_case_type == "insufficient_researched_evidence":
        return True
    baseline = _dict(payload.get("baseline"))
    valuation_case_status = _string_or_none(payload.get("valuationCaseStatus")) or _string_or_none(baseline.get("valuationCaseStatus"))
    if tool == "stockvaluation.value_prospectus":
        return valuation_case_status == "challenged_valuation_case" or baseline_status == "challenged_baseline"
    internal_baseline_statuses = {"mechanical_only", "segment_evidence_insufficient", "challenged_baseline"}
    return tool == "stockvaluation.value_ticker" and baseline_status in internal_baseline_statuses


def compact_number(value: Any) -> str | None:
    number = _number_or_none(value)
    if number is None:
        return None
    if abs(number) >= 1000:
        return f"{number:,.2f}"
    return f"{number:.2f}"


def currency_amount(amount: str, currency: str | None) -> str:
    if currency == "USD":
        return f"${amount}"
    return f"{amount} {currency}" if currency else amount


def dedupe(items: list[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for item in items:
        if item and item not in seen:
            seen.add(item)
            ordered.append(item)
    return ordered


def _int_or_zero(value: Any) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return 0


def _string_list(value: Any) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value if item is not None]


def _issue_list(value: Any) -> list[dict[str, str]]:
    if not isinstance(value, list):
        return []
    issues: list[dict[str, str]] = []
    for item in value:
        if not isinstance(item, dict):
            continue
        field = _string_or_none(item.get("field"))
        if field is None:
            continue
        issues.append(
            {
                "field": field,
                "status": _string_or_none(item.get("status")) or "",
                "reason": _string_or_none(item.get("reason")) or _string_or_none(item.get("message")) or "",
            }
        )
    return issues


def _data_quality_warning_list(value: Any) -> list[dict[str, Any]]:
    if not isinstance(value, list):
        return []
    warnings: list[dict[str, Any]] = []
    for item in value:
        if not isinstance(item, dict):
            continue
        warning = {
            "field": _string_or_none(item.get("field")),
            "status": _string_or_none(item.get("status")),
            "normalizedValue": _number_or_none(item.get("normalizedValue")),
            "filingValue": _number_or_none(item.get("filingValue")),
            "differencePct": _number_or_none(item.get("differencePct")),
            "thresholdPct": _number_or_none(item.get("thresholdPct")),
            "sourceClass": _string_or_none(item.get("sourceClass")),
            "sourceDate": _string_or_none(item.get("sourceDate")),
            "normalizedSourceClass": _string_or_none(item.get("normalizedSourceClass")),
            "normalizedSourceDate": _string_or_none(item.get("normalizedSourceDate")),
            "normalizedPeriodEnd": _string_or_none(item.get("normalizedPeriodEnd")),
            "filingSourceClass": _string_or_none(item.get("filingSourceClass")),
            "filingSourceDate": _string_or_none(item.get("filingSourceDate")),
            "filingPeriodEnd": _string_or_none(item.get("filingPeriodEnd")),
        }
        warnings.append({key: value for key, value in warning.items() if value is not None})
    return warnings


def _dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _number_or_none(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _string_or_none(value: Any) -> str | None:
    if not isinstance(value, str):
        return None
    stripped = value.strip()
    return stripped or None


def _first_number(values: Any) -> float | None:
    if not isinstance(values, list):
        return None
    for value in values:
        if isinstance(value, (int, float)):
            return float(value)
    return None


def _last_number(values: Any) -> float | None:
    if not isinstance(values, list):
        return None
    for value in reversed(values):
        if isinstance(value, (int, float)):
            return float(value)
    return None


def _first_present(*values: Any) -> Any:
    for value in values:
        if value is not None:
            return value
    return None
