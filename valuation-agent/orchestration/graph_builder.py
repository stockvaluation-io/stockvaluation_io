"""
Graph builder for creating and compiling the LangGraph workflow.
"""
import logging
from statistics import mean
from typing import TYPE_CHECKING, Dict, Any, Optional, List

if TYPE_CHECKING:
    from orchestration.orchestrator import AgentOrchestrator

from domain.knowledge.skill_context import build_skill_bundle
from domain.models.valuation import GraphState


logger = logging.getLogger(__name__)

class GraphBuilder:
    """Builds and configures the LangGraph workflow."""
    
    def __init__(self, orchestrator: 'AgentOrchestrator'):
        self.orchestrator = orchestrator
    
    def build_graph(self, checkpointer=None):
        """Build and compile the graph workflow.
        
        Args:
            checkpointer: Optional LangGraph checkpointer (e.g., InMemorySaver)
        """
        try:
            from langgraph.graph import StateGraph, START, END
            from domain.models.valuation import GraphState
            
            # Create StateGraph
            graph = StateGraph(GraphState)

            # Analyzer-only graph:
            # news is gathered before graph execution in api/app.py,
            # recalc happens after analyzer, and analyst runs on fresh DCF afterward.
            graph.add_node("analyzer_agent", self._analyzer_agent_wrapper)
            graph.add_edge(START, "analyzer_agent")
            graph.add_edge("analyzer_agent", END)

            # Compile the graph (optionally with checkpointer for memory)
            if checkpointer is not None:
                compiled_graph = graph.compile(checkpointer=checkpointer)
            else:
                compiled_graph = graph.compile()
            logger.debug("Graph compiled successfully")
            return compiled_graph
            
        except ImportError as e:
            logger.error(f"Failed to import LangGraph dependencies: {e}")
            return None
        except Exception as e:
            logger.error(f"Failed to build graph: {e}")
            return None
    
    def _news_agent_wrapper(self, state: GraphState, config: Dict[str, Any] = None) -> Dict[str, Any]:
        state_dict = state.model_dump() if hasattr(state, "model_dump") else state
        """Wrapper for news agent."""
        logger.debug(f"NEWS AGENT WRAPPER CALLED for {state_dict.get('name', 'Unknown')}")
        try:
            # Extract run_id from config for evaluation tracking
            run_id = None
            if config:
                run_id = config.get("run_id") or config.get("configurable", {}).get("run_id")
            
            # Extract necessary parameters from state
            inputs = {
                "ticker": state_dict.get("ticker", ""),
                "name": state_dict.get("name", ""),
                "dcf": state_dict.get("dcf", {}),
                "financials": state_dict.get("financials", {}),
                "industry": state_dict.get("financials", {}).get("profile", {}).get("industry", "")
            }
            
            # Pass run_id for evaluation tracking
            if run_id:
                inputs["_langgraph_run_id"] = run_id
            
            result = self.orchestrator.run_agent("news", inputs)
            logger.debug(f"News agent completed for {state_dict.get('name', 'Unknown')}")
            return {"news": result}
        except Exception as e:
            logger.error(f"News agent failed: {e}")
            return {"news": {"error": str(e)}}

    def _analyzer_agent_wrapper(self, state: GraphState, config: Dict[str, Any] = None) -> Dict[str, Any]:
        state_dict = state.model_dump() if hasattr(state, "model_dump") else state
        """
        Analyzer V1:
        - Uses an LLM analyzer agent over normalized DCF + financials + news summary/tone
        - Emits only 3 DCF variables: revenue_cagr, operating_margin, sales_to_capital
        - Keeps DCF math deterministic in Java
        """
        logger.debug(f"ANALYZER AGENT WRAPPER CALLED for {state_dict.get('name', 'Unknown')}")
        try:
            del config  # no config inputs currently required for analyzer
            analyzer_inputs = {
                "ticker": state_dict.get("ticker", ""),
                "name": state_dict.get("name", ""),
                "industry": state_dict.get("industry", ""),
                "dcf": state_dict.get("dcf", {}) or {},
                "financials": state_dict.get("financials", {}) or {},
                "segments": state_dict.get("segments", {}) or {},
                "news": state_dict.get("news", {}) or {},
                "skills": build_skill_bundle(
                    industry=str(state_dict.get("industry", "")).strip(),
                    segments_payload=state_dict.get("segments", {}) or {},
                ),
            }
            llm_result = self.orchestrator.run_agent("analyzer", analyzer_inputs)

            if not isinstance(llm_result, dict) or llm_result.get("error"):
                error_text = (
                    (llm_result or {}).get("error")
                    if isinstance(llm_result, dict)
                    else "invalid_result"
                )
                logger.warning(
                    "Analyzer LLM failed for %s. Error=%s",
                    state_dict.get("ticker", ""),
                    error_text,
                )
                analyzer_result = self._build_analyzer_failure_payload(state_dict, error_text)
            else:
                analyzer_result = self._normalize_analyzer_result(llm_result, state_dict)

            existing_merged = state_dict.get("merged_result", {}) or {}
            merged_update = {
                **existing_merged,
                "dcf_analysis": analyzer_result.get("dcf_analysis", {}) or {},
                "recommendations": analyzer_result.get("recommendations", {}) or {},
                "analyzer_metadata": analyzer_result.get("analyzer_metadata", {}) or {},
            }

            logger.debug(
                "Analyzer V1 completed for %s. Instructions=%s",
                state_dict.get("ticker", ""),
                len(((analyzer_result.get("dcf_analysis") or {}).get("dcf_adjustment_instructions") or [])),
            )
            return {"merged_result": merged_update}
        except Exception as e:
            logger.error(f"Analyzer agent failed: {e}", exc_info=True)
            return {
                "merged_result": {
                    "dcf_analysis": {"dcf_adjustment_instructions": []},
                    "recommendations": {"confidence_level": "low"},
                    "analyzer_metadata": {"version": "analyzer_v1", "error": True},
                },
            }

    def _analyst_agent_wrapper(self, state: GraphState, config: Dict[str, Any] = None) -> Dict[str, Any]:
        state_dict = state.model_dump() if hasattr(state, "model_dump") else state
        """Analyst V1 wrapper for narrative sections consumed by frontend."""
        try:
            del config  # no config inputs currently required for analyst
            analyst_inputs = {
                "ticker": state_dict.get("ticker", ""),
                "name": state_dict.get("name", ""),
                "industry": state_dict.get("industry", ""),
                "dcf": state_dict.get("dcf", {}) or {},
                "financials": state_dict.get("financials", {}) or {},
                "news_content": state_dict.get("news", {}) or {},
                "skills": build_skill_bundle(
                    industry=str(state_dict.get("industry", "")).strip(),
                    segments_payload=state_dict.get("segments", {}) or {},
                ),
            }
            llm_result = self.orchestrator.run_agent("analyst", analyst_inputs)

            normalized_result = self._normalize_analyst_result(llm_result, state_dict)
            existing_merged = state_dict.get("merged_result", {}) or {}
            merged_update = {
                **existing_merged,
                **normalized_result,
            }
            return {"merged_result": merged_update}
        except Exception as e:
            logger.error("Analyst agent failed: %s", e, exc_info=True)
            return {"merged_result": state_dict.get("merged_result", {}) or {}}

    def _normalize_analyzer_result(self, llm_result: Dict[str, Any], state_dict: Dict[str, Any]) -> Dict[str, Any]:
        allowed_params = {
            "revenue_cagr": "percent",
            "operating_margin": "percent",
            "sales_to_capital": "x",
        }

        raw_dcf_analysis = llm_result.get("dcf_analysis") if isinstance(llm_result.get("dcf_analysis"), dict) else {}
        raw_recommendations = llm_result.get("recommendations") if isinstance(llm_result.get("recommendations"), dict) else {}
        raw_metadata = llm_result.get("analyzer_metadata") if isinstance(llm_result.get("analyzer_metadata"), dict) else {}

        baseline = self._derive_baseline_assumptions(state_dict.get("dcf", {}) or {})
        raw_baseline = raw_dcf_analysis.get("baseline_assumptions")
        if isinstance(raw_baseline, dict):
            for key in baseline:
                candidate = self._safe_float(raw_baseline.get(key))
                if candidate is not None:
                    baseline[key] = candidate

        normalized_instructions: List[Dict[str, Any]] = []
        raw_instructions = raw_dcf_analysis.get("dcf_adjustment_instructions")
        if isinstance(raw_instructions, list):
            for item in raw_instructions:
                if not isinstance(item, dict):
                    continue
                parameter = str(item.get("parameter", "")).strip()
                if parameter not in allowed_params:
                    continue
                new_value = self._safe_float(item.get("new_value"))
                if new_value is None:
                    continue
                unit = str(item.get("unit", "")).strip().lower() or allowed_params[parameter]
                if unit != allowed_params[parameter]:
                    unit = allowed_params[parameter]
                normalized_instructions.append(
                    {
                        "parameter": parameter,
                        "new_value": round(new_value, 3),
                        "unit": unit,
                        "rationale": str(item.get("rationale", "")).strip() or "LLM-generated adjustment.",
                    }
                )

        allowed_sector_params = {
            "revenue_growth": "percent",
            "operating_margin": "percent",
            "sales_to_capital": "x",
        }
        allowed_adjustment_types = {"absolute", "relative_multiplier", "relative_additive"}
        allowed_timeframes = {"years_1_to_5", "years_6_to_10", "both"}
        valid_sectors_lookup = {
            str(segment.get("sector", "")).strip().lower(): str(segment.get("sector", "")).strip()
            for segment in self._extract_segments(state_dict.get("segments", {}) or {})
            if str(segment.get("sector", "")).strip()
        }
        normalized_sector_instructions: List[Dict[str, Any]] = []
        raw_sector_instructions = raw_dcf_analysis.get("sector_adjustment_instructions")
        if isinstance(raw_sector_instructions, list):
            for item in raw_sector_instructions:
                if not isinstance(item, dict):
                    continue
                parameter = str(item.get("parameter", "")).strip().lower()
                if parameter not in allowed_sector_params:
                    continue
                sector_raw = str(item.get("sector", "")).strip()
                sector_name = valid_sectors_lookup.get(sector_raw.lower())
                if not sector_name:
                    continue
                value = self._safe_float(item.get("value"))
                if value is None:
                    continue
                adjustment_type = str(item.get("adjustment_type", "absolute")).strip().lower()
                if adjustment_type not in allowed_adjustment_types:
                    continue
                timeframe = str(item.get("timeframe", "both")).strip().lower()
                if timeframe not in allowed_timeframes:
                    timeframe = "both"
                unit = str(item.get("unit", "")).strip().lower() or allowed_sector_params[parameter]
                if unit != allowed_sector_params[parameter]:
                    unit = allowed_sector_params[parameter]
                normalized_sector_instructions.append(
                    {
                        "sector": sector_name,
                        "parameter": parameter,
                        "value": round(value, 3),
                        "unit": unit,
                        "adjustment_type": adjustment_type,
                        "timeframe": timeframe,
                        "rationale": str(item.get("rationale", "")).strip() or "LLM-generated sector adjustment.",
                    }
                )

        proposed = {}
        raw_proposed = raw_dcf_analysis.get("proposed_assumptions")
        if isinstance(raw_proposed, dict):
            for param in allowed_params:
                value = self._safe_float(raw_proposed.get(param))
                if value is not None:
                    proposed[param] = round(value, 3)
        for item in normalized_instructions:
            proposed[item["parameter"]] = item["new_value"]

        segments = self._extract_segments(state_dict.get("segments", {}) or {})
        dominant_segment = self._get_dominant_segment(segments)
        dominant_sector = str(dominant_segment.get("sector", "")).strip()
        dominant_industry = str(dominant_segment.get("industry", "")).strip()
        fallback_industry = (
            state_dict.get("industry")
            or (
                (state_dict.get("financials", {}) or {}).get("profile", {})
                if isinstance(state_dict.get("financials", {}), dict)
                else {}
            ).get("industry")
            or ""
        )
        industry = dominant_industry or str(fallback_industry).strip()
        tone = (
            str((state_dict.get("news", {}) or {}).get("tone", "neutral")).lower()
            if isinstance(state_dict.get("news", {}), dict)
            else "neutral"
        )
        if tone not in {"optimistic", "cautious", "neutral"}:
            tone = "neutral"

        confidence = str(raw_recommendations.get("confidence_level", "low")).lower()
        if confidence not in {"low", "medium", "high"}:
            confidence = "low"

        baseline_available = [key for key, value in baseline.items() if value is not None]

        dcf_analysis = {
            "assumption_policy": raw_dcf_analysis.get("assumption_policy"),
            "baseline_assumptions": baseline,
            "proposed_assumptions": proposed,
            "dcf_adjustment_instructions": normalized_instructions,
            "sector_adjustment_instructions": normalized_sector_instructions,
            "adjusted_valuation": raw_dcf_analysis.get("adjusted_valuation"),
        }

        recommendations = {
            "confidence_level": confidence,
            "focus_variables": ["revenue_cagr", "operating_margin", "sales_to_capital"],
            "summary": raw_recommendations.get("summary")
            or "Narrow assumption adjuster only. DCF math, data retrieval, and final valuation remain in Java.",
        }

        raw_baseline_available = raw_metadata.get("baseline_metrics_available")
        if not isinstance(raw_baseline_available, list):
            raw_baseline_available = baseline_available

        raw_segments_count = raw_metadata.get("segments_count", len(segments))
        try:
            segments_count = int(raw_segments_count)
        except (TypeError, ValueError):
            segments_count = len(segments)

        analyzer_metadata = {
            "version": str(raw_metadata.get("version", "analyzer_v1")),
            "industry": str(raw_metadata.get("industry", industry)),
            "dominant_sector": str(raw_metadata.get("dominant_sector", dominant_sector)),
            "dominant_industry": str(raw_metadata.get("dominant_industry", dominant_industry)),
            "segments_count": segments_count,
            "tone": str(raw_metadata.get("tone", tone)),
            "generated_instruction_count": len(normalized_instructions) + len(normalized_sector_instructions),
            "baseline_metrics_available": raw_baseline_available,
        }

        return {
            "dcf_analysis": dcf_analysis,
            "recommendations": recommendations,
            "analyzer_metadata": analyzer_metadata,
        }

    def _derive_baseline_assumptions(self, dcf: Dict[str, Any]) -> Dict[str, Optional[float]]:
        derived = dcf.get("derived", {}) if isinstance(dcf, dict) else {}
        revenue_cagr = self._safe_float(derived.get("revenue_cagr_pct"))

        margin = self._safe_float(derived.get("margin_end_pct"))
        if margin is None:
            margin = self._penultimate_value(dcf.get("margins"))

        sales_to_capital = self._mean_non_terminal_ratio(dcf.get("sales_to_capital_ratio"))

        return {
            "revenue_cagr": revenue_cagr,
            "operating_margin": margin,
            "sales_to_capital": sales_to_capital,
        }

    def _build_analyzer_failure_payload(self, state_dict: Dict[str, Any], error_text: str) -> Dict[str, Any]:
        baseline = self._derive_baseline_assumptions(state_dict.get("dcf", {}) or {})
        segments = self._extract_segments(state_dict.get("segments", {}) or {})
        dominant_segment = self._get_dominant_segment(segments)
        dominant_sector = str(dominant_segment.get("sector", "")).strip()
        dominant_industry = str(dominant_segment.get("industry", "")).strip()
        fallback_industry = (
            state_dict.get("industry")
            or (
                (state_dict.get("financials", {}) or {}).get("profile", {})
                if isinstance(state_dict.get("financials", {}), dict)
                else {}
            ).get("industry")
            or ""
        )
        industry = dominant_industry or str(fallback_industry).strip()
        tone = (
            str((state_dict.get("news", {}) or {}).get("tone", "neutral")).lower()
            if isinstance(state_dict.get("news", {}), dict)
            else "neutral"
        )
        if tone not in {"optimistic", "cautious", "neutral"}:
            tone = "neutral"

        return {
            "dcf_analysis": {
                "assumption_policy": (
                    "Analyzer adjusts only revenue_cagr, operating_margin, and sales_to_capital. "
                    "Java DCF remains source of truth for math."
                ),
                "baseline_assumptions": baseline,
                "proposed_assumptions": {},
                "dcf_adjustment_instructions": [],
                "sector_adjustment_instructions": [],
                "adjusted_valuation": "Analyzer output unavailable; no assumption changes applied.",
            },
            "recommendations": {
                "confidence_level": "low",
                "focus_variables": ["revenue_cagr", "operating_margin", "sales_to_capital"],
                "summary": "Analyzer output unavailable. Keeping baseline assumptions.",
            },
            "analyzer_metadata": {
                "version": "analyzer_v1",
                "industry": industry,
                "dominant_sector": dominant_sector,
                "dominant_industry": dominant_industry,
                "segments_count": len(segments),
                "tone": tone,
                "generated_instruction_count": 0,
                "baseline_metrics_available": [k for k, v in baseline.items() if v is not None],
                "error": error_text,
            },
        }

    @staticmethod
    def _safe_float(value: Any) -> Optional[float]:
        try:
            if value is None:
                return None
            return float(value)
        except (TypeError, ValueError):
            return None

    def _normalize_analyst_result(self, llm_result: Dict[str, Any], state_dict: Dict[str, Any]) -> Dict[str, Any]:
        news_data = state_dict.get("news", {}) if isinstance(state_dict.get("news"), dict) else {}
        drivers = news_data.get("valuation_drivers", {}) if isinstance(news_data.get("valuation_drivers"), dict) else {}
        is_valid_llm_result = isinstance(llm_result, dict) and not llm_result.get("error")

        section_defaults = {
            "growth": ("Growth", "Business as usual growth assumptions apply.", drivers.get("growth")),
            "margins": ("Margins", "Business as usual margin assumptions apply.", drivers.get("operating_margins")),
            "investment_efficiency": (
                "Investment Efficiency",
                "Business as usual investment efficiency assumptions apply.",
                drivers.get("capital_efficiency"),
            ),
            "risks": ("Risks", "Standard market and operational risks apply.", drivers.get("risk")),
            "key_takeaways": ("Key Takeaways", "No specific hypothesis generated.", news_data.get("summary_hypothesis")),
        }

        normalized: Dict[str, Any] = {}
        for key, (default_title, default_text, fallback_text) in section_defaults.items():
            source_block = llm_result.get(key) if is_valid_llm_result else None
            narrative = ""
            title = default_title
            if isinstance(source_block, dict):
                title = str(source_block.get("title", default_title)).strip() or default_title
                narrative = str(source_block.get("narrative", "")).strip()
            if not narrative:
                narrative = str(fallback_text).strip() if isinstance(fallback_text, str) else ""
            if not narrative:
                narrative = default_text
            normalized[key] = {"title": title, "narrative": narrative}

        if is_valid_llm_result:
            title = llm_result.get("title")
            if isinstance(title, str) and title.strip():
                normalized["title"] = title.strip()
            normalized["analyst_output"] = llm_result

        return normalized

    def _extract_segments(self, segments_data: Any) -> List[Dict[str, Any]]:
        if not isinstance(segments_data, dict):
            return []
        segments = segments_data.get("segments")
        if not isinstance(segments, list):
            return []
        clean: List[Dict[str, Any]] = []
        for item in segments:
            if not isinstance(item, dict):
                continue
            sector = str(item.get("sector", "")).strip()
            if not sector or sector.upper() == "UNKNOWN":
                continue
            clean.append(item)
        return clean

    def _get_dominant_segment(self, segments: List[Dict[str, Any]]) -> Dict[str, Any]:
        if not segments:
            return {}

        def _share(seg: Dict[str, Any]) -> float:
            return self._safe_float(seg.get("revenue_share")) or 0.0

        return max(segments, key=_share)

    def _mean_non_terminal_ratio(self, values: Any) -> Optional[float]:
        if not isinstance(values, list):
            return None
        nums = [self._safe_float(v) for v in values]
        nums = [v for v in nums if v is not None and v > 0]
        if not nums:
            return None
        core = nums[:-1] if len(nums) > 1 else nums
        return round(mean(core), 3)

    def _penultimate_value(self, values: Any) -> Optional[float]:
        if not isinstance(values, list):
            return None
        nums = [self._safe_float(v) for v in values]
        nums = [v for v in nums if v is not None]
        if len(nums) >= 2:
            return round(nums[-2], 3)
        if nums:
            return round(nums[-1], 3)
        return None

    def _merger_agent_wrapper(self, state: GraphState, config: Dict[str, Any] = None) -> Dict[str, Any]:
        """Wrapper for merger agent using orchestrator."""
        del config  # No config inputs currently required for merger.
        state_dict = state.model_dump() if hasattr(state, "model_dump") else state
        logger.debug("Merger wrapper called for %s", state_dict.get("name", "Unknown"))
        
        try:
            existing_merged = state_dict.get("merged_result", {}) or {}
            # Extract narrative data from news agent
            news_data = state_dict.get("news", {})
            drivers = news_data.get("valuation_drivers", {})
            
            merged_result = {
                "growth": existing_merged.get("growth") or {
                    "title": "Growth",
                    "narrative": drivers.get("growth", "Business as usual growth assumptions apply."),
                },
                "margins": existing_merged.get("margins") or {
                    "title": "Margins",
                    "narrative": drivers.get("operating_margins", "Business as usual margin assumptions apply."),
                },
                "investment_efficiency": existing_merged.get("investment_efficiency") or {
                    "title": "Investment Efficiency",
                    "narrative": drivers.get("capital_efficiency", "Business as usual investment efficiency assumptions apply."),
                },
                "risks": existing_merged.get("risks") or {
                    "title": "Risks",
                    "narrative": drivers.get("risk", "Standard market and operational risks apply."),
                },
                "key_takeaways": existing_merged.get("key_takeaways") or {
                    "title": "Key Takeaways",
                    "narrative": news_data.get("summary_hypothesis", "No specific hypothesis generated."),
                },
                "tone": news_data.get("tone", "neutral"),
            }

            if "title" in existing_merged:
                merged_result["title"] = existing_merged["title"]
            if "analyst_output" in existing_merged:
                merged_result["analyst_output"] = existing_merged["analyst_output"]

            # Preserve analyzer outputs for /api-s/valuate (DCF override mapping + audit).
            for key in ("dcf_analysis", "recommendations", "analyzer_metadata"):
                if key in existing_merged:
                    merged_result[key] = existing_merged[key]

            logger.debug(
                "Merger completed for %s. Drivers present=%s",
                state_dict.get("name", ""),
                bool(drivers),
            )
            return {"merged_result": merged_result}
            
        except Exception as e:
            logger.error("Merger agent wrapper failed: %s", e, exc_info=True)
            return {}
    
