"""
Refactored Flask application for stockvaluation.io
"""
import json
import logging
import os
import re
import hashlib
import secrets
import time
import threading
from collections import deque
from difflib import SequenceMatcher
from datetime import datetime
from typing import Any, Dict, List, Optional
from uuid import uuid4
from flask import Flask, Response, jsonify, request
from flask_cors import CORS

# Import refactored modules
from config.app_config import APIConfig
from domain.knowledge.tool_definitions import industry_mapping
from domain.knowledge.skill_context import build_skill_bundle
from domain.models.valuation import GraphState, ValuationRequest
from domain.processing.helpers import preprocess_dcf_json, preprocess_financials_json
from orchestration.orchestrator import AgentOrchestrator
from orchestration.graph_builder import GraphBuilder
from storage.persistence.valuation_persistence import valuation_service
from services.valuation_service_client import (
    ValuationServiceClient,
    ValuationServiceClientError,
)

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

SECTOR_TO_INDUSTRY = {
    str(entry.get("sector", "")).strip().lower(): str(entry.get("industry", "")).strip().lower()
    for entry in industry_mapping
    if entry.get("sector") and entry.get("industry")
}
CANONICAL_INDUSTRIES = set(SECTOR_TO_INDUSTRY.values())
INDUSTRY_ALIASES = {
    "information-technology": "technology",
    "information-technology-services": "technology",
    "communications": "communication-services",
    "communication": "communication-services",
    "consumer-discretionary": "consumer-cyclical",
    "consumer-staples": "consumer-defensive",
    "financials": "financial-services",
    "real-estate-services": "real-estate",
    "materials": "basic-materials",
}

DEFAULT_CORS_ORIGINS = [
    "http://localhost:4200",
    "http://127.0.0.1:4200",
    "http://localhost:3000",
    "http://127.0.0.1:3000",
]


def _parse_cors_origins(raw: str) -> List[str]:
    cleaned = (raw or "").strip()
    if not cleaned:
        return list(DEFAULT_CORS_ORIGINS)
    if cleaned == "*":
        allow_all = os.getenv("CORS_ALLOW_ALL", "false").lower() == "true"
        if allow_all:
            return ["*"]
        logger.warning("CORS_ORIGINS='*' ignored unless CORS_ALLOW_ALL=true; using localhost defaults")
        return list(DEFAULT_CORS_ORIGINS)
    origins = [origin.strip() for origin in cleaned.split(",") if origin.strip()]
    return origins or list(DEFAULT_CORS_ORIGINS)


def _resolve_secret_key() -> str:
    """
    Resolve Flask SECRET_KEY from env.
    Fail fast when not configured to avoid insecure implicit runtime keys.
    """
    configured = os.getenv("SECRET_KEY", "").strip()
    if not configured:
        raise RuntimeError("SECRET_KEY environment variable is required for valuation-agent")
    return configured

class StockValuationApp:
    """Main application class."""
    
    def __init__(self):
        self.app = Flask(__name__)
        self.setup_config()
        self.internal_api_key = os.getenv("INTERNAL_API_KEY", "").strip()
        self.rate_limit_enabled = os.getenv("RATE_LIMIT_ENABLED", "true").strip().lower() in {
            "1",
            "true",
            "yes",
            "on",
        }
        self.rate_limit_requests = max(1, int(os.getenv("RATE_LIMIT_REQUESTS_PER_SECOND", "2")))
        self.rate_limit_window_seconds = max(1, int(os.getenv("RATE_LIMIT_DURATION_SECONDS", "1")))
        self._rate_limit_lock = threading.Lock()
        self._rate_limit_buckets: Dict[str, deque[float]] = {}

        # Setup CORS with explicit origin allowlist by default.
        cors_origins = _parse_cors_origins(os.getenv("CORS_ORIGINS", ""))
        CORS(
            self.app,
            resources={r"/*": {"origins": cors_origins}},
            supports_credentials=False,
            allow_headers=[
                "Content-Type",
                "Authorization",
                "X-Requested-With",
                "X-User-ID",
                "x-user-id",
            ],
            methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
        )
        
        # Pre-load LLM Guard models at startup (only if enabled via ENABLE_LLM_GUARD=true)
        if os.getenv('ENABLE_LLM_GUARD', 'false').lower() == 'true':
            self.preload_llm_guard_models()
        else:
            logger.info("LLM Guard disabled - skipping model preloading for faster startup")
        

        self.orchestrator = AgentOrchestrator()
        self.graph_builder = GraphBuilder(self.orchestrator)
        self.valuation_service_client = ValuationServiceClient()
        
        self.setup_routes()
    
    def setup_config(self):
        """Configure Flask application."""
        self.app.config.update({
            'SECRET_KEY': _resolve_secret_key(),
            'MAX_CONTENT_LENGTH': 50 * 1024 * 1024  # 50MB max - handle large POST bodies with eventlet
        })
    
    def preload_llm_guard_models(self):
        """
        Pre-load LLM Guard models at startup to avoid 50+ second delay on first message.
        
        This initializes:
        - PromptInjection scanner (deberta-v3-base model)
        - Toxicity scanner (unbiased-toxic-roberta model)
        - Sensitive scanner (deberta-v3-base for PII detection)
        - Presidio analyzer models
        """
        try:
            logger.info("Pre-loading LLM Guard models...")
            start_time = datetime.now()
            
            # Import and initialize PromptGuard which loads all the models
            from security.prompt_guard import PromptGuard
            
            # Create a dummy instance to trigger model loading
            guard = PromptGuard(
                max_messages_per_minute=10,
                max_message_length=5000,
                enable_anonymization=False
            )
            
            # Run a test validation to ensure models are fully loaded
            guard.validate_input("test message", session_id="startup_test")
            
            elapsed = (datetime.now() - start_time).total_seconds()
            logger.info(f"LLM Guard models pre-loaded successfully in {elapsed:.2f}s")
            
        except Exception as e:
            logger.warning(f"Failed to pre-load LLM Guard models: {e}")
            logger.warning("Models will be loaded on first use (may cause 50+ second delay)")
    
    def setup_routes(self):
        """Setup Flask routes."""

        
        @self.app.route('/health')
        def health_check():
            """Health check endpoint for Docker."""
            return jsonify({
                "status": "healthy",
                "service": "stockvaluation.io",
                "version": "2.0.0"
            }), 200
        


        @self.app.post('/api-s/valuate')
        def valuate():
            if not self._is_internal_request_authorized():
                return jsonify({"error": "unauthorized"}), 401
            if self._is_rate_limited():
                return jsonify({"error": "rate_limited"}), 429
            return self.valuate_endpoint()
        
        @self.app.get('/api-s/valuation/<valuation_id>')
        def get_valuation(valuation_id: str):
            # Used by bullbeargpt to load valuation context for chat sessions.
            if not self._is_internal_request_authorized():
                return jsonify({"error": "unauthorized"}), 401
            valuation = valuation_service.get_valuation_by_id(valuation_id)
            if not valuation:
                return jsonify({'error': 'Valuation not found'}), 404
            
            # Extract key fields for chat context
            valuation_data = valuation.get('valuation_data', {})
            
            return jsonify({
                'id': valuation.get('id'),
                'ticker': valuation.get('ticker'),
                'company_name': valuation.get('company_name'),
                'valuation_date': valuation.get('valuation_date'),
                'fair_value': valuation.get('fair_value'),
                'current_price': valuation.get('current_price'),
                'upside_percentage': valuation.get('upside_percentage'),
                'valuation_data': valuation_data,
            })

    def _is_internal_request_authorized(self) -> bool:
        """
        Optional internal API guard.
        If INTERNAL_API_KEY is configured, requests must provide matching key via:
        - X-Internal-API-Key
        - Authorization: Bearer <key>
        """
        if not self.internal_api_key:
            return True

        header_key = (request.headers.get("X-Internal-API-Key") or "").strip()
        if header_key and secrets.compare_digest(header_key, self.internal_api_key):
            return True

        auth_header = (request.headers.get("Authorization") or "").strip()
        if auth_header.startswith("Bearer "):
            bearer_token = auth_header[7:].strip()
            if bearer_token and secrets.compare_digest(bearer_token, self.internal_api_key):
                return True

        return False

    def _extract_client_id(self) -> str:
        forwarded_for = (request.headers.get("X-Forwarded-For") or "").split(",")[0].strip()
        if forwarded_for:
            return forwarded_for
        if request.remote_addr:
            return request.remote_addr
        return "unknown"

    def _is_rate_limited(self) -> bool:
        if not self.rate_limit_enabled:
            return False

        now = time.monotonic()
        cutoff = now - self.rate_limit_window_seconds
        client_id = self._extract_client_id()

        with self._rate_limit_lock:
            bucket = self._rate_limit_buckets.get(client_id)
            if bucket is None:
                bucket = deque()
                self._rate_limit_buckets[client_id] = bucket

            while bucket and bucket[0] < cutoff:
                bucket.popleft()

            if len(bucket) >= self.rate_limit_requests:
                return True

            bucket.append(now)
            return False

    def valuate_endpoint(self) -> Response:
        """
        Ticker-first orchestration endpoint.

        Flow:
        1. Fetch baseline valuation without segment payload (for profile/industry context)
        2. Segment mapping
        3. News gathering + cleaning
        4. Baseline valuation with segment payload
        5. Analyzer judgement (override instructions only)
        6. Recalculate Java DCF with overrides + segments
        7. Analyst narrative on final recalculated DCF
        8. Persist and return merged response
        """
        audit_run_id = str(uuid4())
        auth_header = request.headers.get("Authorization")

        # 1. Process Request
        val_request, error_response = self._process_valuation_request()
        if error_response:
            return error_response
            
        ticker = val_request.ticker.strip().upper()
        requested_name = val_request.name.strip() if val_request.name else None

        try:
            company_name = requested_name or ticker
            
            # Step 1: Baseline DCF context (no segments) for robust profile/industry extraction
            prebaseline_result = self._fetch_baseline_valuation(
                ticker=ticker,
                requested_name=company_name,
                auth_header=auth_header,
                mapped_segments=None,
            )
            company_name = prebaseline_result["company_name"]

            # Step 2: Segment mapping with financial/profile context
            segments_result = self._run_segment_mapping(
                ticker, 
                company_name, 
                prebaseline_result["preprocessed_financials"],
            )
            
            if not segments_result["mapped_segments"]:
                return self._handle_missing_segments(ticker, company_name)

            mapped_segments = segments_result["mapped_segments"]

            # Step 3: News and evidence gathering
            news_result = self._gather_news(
                ticker, 
                company_name, 
                prebaseline_result["preprocessed_dcf"],
                prebaseline_result["preprocessed_financials"],
            )

            # Step 4: Baseline DCF from Java with segment payload attached
            baseline_result = self._fetch_baseline_valuation(
                ticker=ticker,
                requested_name=company_name,
                auth_header=auth_header,
                mapped_segments=mapped_segments,
            )
            company_name = baseline_result["company_name"]
            
            # Step 5: Generate DCF override judgement from baseline+news
            graph_result = self._run_dcf_graph(
                ticker,
                company_name,
                baseline_result["preprocessed_dcf"],
                baseline_result["preprocessed_financials"],
                mapped_segments,
                news_result["news"],
                audit_run_id
            )
            
            # Step 6: Recalculate Java DCF with overrides + segments
            recalc_result = self._recalculate_java_dcf(
                ticker,
                graph_result["merged_result"],
                mapped_segments,
                auth_header
            )

            # Step 7: Run analyst on fresh DCF after recalculation
            analyst_result = self._run_analyst_on_final_dcf(
                ticker=ticker,
                company_name=company_name,
                dcf=recalc_result["dcf"],
                news_result=news_result["news"],
                mapped_segments=mapped_segments,
            )
            if isinstance(analyst_result, dict):
                merged = dict(graph_result.get("merged_result") or {})
                for key in (
                    "title",
                    "growth",
                    "margins",
                    "investment_efficiency",
                    "risks",
                    "key_takeaways",
                    "analyst_output",
                ):
                    if key in analyst_result:
                        merged[key] = analyst_result[key]
                if isinstance(news_result.get("news"), dict):
                    merged["tone"] = str(news_result["news"].get("tone", "neutral"))
                    merged["news_narrative"] = news_result["news"]
                graph_result["merged_result"] = merged
                graph_result["agent_analysis_payload"] = self._build_agent_analysis_payload(
                    merged_result=merged,
                    mapped_segments=mapped_segments,
                )
            
            # Step 8: Build narrative, persist valuation, return response
            return self._finalize_and_persist(
                ticker=ticker,
                company_name=company_name,
                audit_run_id=audit_run_id,
                baseline_result=baseline_result,
                news_result=news_result,
                graph_result=graph_result,
                recalc_result=recalc_result,
                mapped_segments=mapped_segments
            )

        except ValuationServiceClientError as e:
            logger.error(f"valuation-service error in /api-s/valuate for {ticker}: {e}")
            return jsonify({"error": str(e), "ticker": ticker}), 502
        except Exception as e:
            logger.error(f"Error in valuate_endpoint: {e}", exc_info=True)
            return jsonify({"error": f"Valuation failed: {e}", "ticker": ticker}), 500

    def _process_valuation_request(self) -> tuple[Optional[ValuationRequest], Any]:
        try:
            data = request.get_json(force=True, silent=True) or {}
            val_request = ValuationRequest.model_validate(data)
        except Exception as e:
            logger.error(f"Invalid request payload: {e}")
            return None, (jsonify({"error": "Invalid payload format"}), 400)

        if not val_request.ticker.strip():
            return None, (jsonify({"error": "ticker is required"}), 400)

        return val_request, None

    def _fetch_baseline_valuation(
        self,
        ticker: str,
        requested_name: Optional[str],
        auth_header: Optional[str],
        mapped_segments: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        baseline_overrides = self._build_java_recalculate_payload({}, mapped_segments or [])
        baseline_dcf = self.valuation_service_client.get_baseline_valuation(
            ticker=ticker,
            auth_header=auth_header,
            overrides=baseline_overrides,
        )

        raw_dcf = self._deep_snake_case(baseline_dcf)
        company_name = requested_name or raw_dcf.get("company_name") or ticker
        raw_financials = self._build_raw_financials_from_java_output(
            ticker=ticker,
            company_name=company_name,
            raw_dcf=raw_dcf,
        )

        preprocessed_dcf = preprocess_dcf_json(raw_dcf)
        preprocessed_financials = preprocess_financials_json(raw_financials)
        
        return {
            "dcf": baseline_dcf,
            "company_name": company_name,
            "preprocessed_dcf": preprocessed_dcf,
            "preprocessed_financials": preprocessed_financials,
            "financial_data_input_payload": baseline_overrides,
        }

    def _run_segment_mapping(self, ticker: str, company_name: str, preprocessed_financials: Dict[str, Any]) -> Dict[str, Any]:
        profile = preprocessed_financials.get("profile", {}) or {}
        raw_industry = (
            profile.get("industry")
            or profile.get("industry_global")
            or profile.get("industry_us")
            or ""
        )
        current_industry = self._normalize_industry(
            raw_industry
        )
        segments_inputs = {
            "ticker": ticker,
            "name": company_name,
            "industry": current_industry,
            "description": profile.get("description", ""),
        }
        segments_result = self.orchestrator.run_agent("segments", segments_inputs)
        mapped_segments = self._extract_mapped_segments(segments_result, current_industry)
        if not mapped_segments:
            mapped_segments = self._build_fallback_segments(
                company_name=company_name,
                preprocessed_financials=preprocessed_financials,
                expected_industry=current_industry,
            )
            if mapped_segments:
                logger.warning(
                    "Using deterministic fallback segment mapping for %s (industry=%s)",
                    ticker,
                    current_industry or "unknown",
                )

        return {
            "mapped_segments": mapped_segments
        }

    def _handle_missing_segments(self, ticker: str, company_name: str) -> Any:
        return jsonify({
            "error": "segments_required",
            "message": "Unable to map company segments to supported sectors for this ticker.",
            "ticker": ticker,
            "company_name": company_name
        }), 422

    def _gather_news(self, ticker: str, company_name: str, preprocessed_dcf: Dict[str, Any], preprocessed_financials: Dict[str, Any]) -> Dict[str, Any]:
        news_inputs = {
            "ticker": ticker,
            "name": company_name,
            "dcf": preprocessed_dcf,
            "financials": preprocessed_financials,
            "industry": preprocessed_financials.get("profile", {}).get("industry", ""),
        }
        news_result = self.orchestrator.run_agent("news", news_inputs)
        
        try:
            news_hash_source = json.dumps(news_result, sort_keys=True, default=str)
            current_news_hash = hashlib.sha256(news_hash_source.encode()).hexdigest()[:16]
        except Exception:
            current_news_hash = hashlib.sha256(str(datetime.utcnow()).encode()).hexdigest()[:16]

        return {
            "news": news_result,
            "news_hash": current_news_hash
        }

    def _run_dcf_graph(self, ticker: str, company_name: str, preprocessed_dcf: Dict[str, Any], preprocessed_financials: Dict[str, Any], mapped_segments: List[Dict[str, Any]], news_result: Dict[str, Any], audit_run_id: str) -> Dict[str, Any]:
        cache_key = f"valuate-{ticker}-{audit_run_id}"

        initial_state = GraphState(
            dcf=preprocessed_dcf,
            financials=preprocessed_financials,
            segments={"segments": mapped_segments},
            ticker=ticker,
            name=company_name,
            industry=preprocessed_financials.get("profile", {}).get("industry", ""),
            news=news_result,
            merged_result={},
        )

        from langgraph.checkpoint.memory import MemorySaver

        memory = MemorySaver()
        compiled_graph = self.graph_builder.build_graph(checkpointer=memory)
        graph_config = {
            "configurable": {
                "thread_id": cache_key,
                "audit_run_id": audit_run_id,
            }
        }
        graph_state = compiled_graph.invoke(initial_state, graph_config)

        if not isinstance(graph_state, dict):
            raise RuntimeError("DCF graph returned non-dict state")

        merged_result = graph_state.get("merged_result") or {}
        
        if not merged_result:
            logger.warning("DCF graph produced no merged_result, using empty default.")
            merged_result = {
                "dcf_analysis": {
                    "dcf_adjustment_instructions": []
                }
            }

        agent_analysis_payload = self._build_agent_analysis_payload(
            merged_result=merged_result,
            mapped_segments=mapped_segments,
        )

        return {
            "graph_state": graph_state,
            "merged_result": merged_result,
            "agent_analysis_payload": agent_analysis_payload,
        }

    def _build_agent_analysis_payload(
        self,
        merged_result: Dict[str, Any],
        mapped_segments: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        analyzer_metadata = {}
        if isinstance(merged_result, dict):
            raw_metadata = merged_result.get("analyzer_metadata")
            if isinstance(raw_metadata, dict):
                analyzer_metadata = dict(raw_metadata)
        analyzer_metadata["segments_count"] = len(mapped_segments)

        payload = dict(merged_result) if isinstance(merged_result, dict) else {}
        payload["dcf_analysis"] = payload.get("dcf_analysis") or {}
        payload["recommendations"] = payload.get("recommendations") or {}
        payload["analyzer_metadata"] = analyzer_metadata
        return payload

    def _recalculate_java_dcf(self, ticker: str, merged_result: Dict[str, Any], mapped_segments: List[Dict[str, Any]], auth_header: Optional[str]) -> Dict[str, Any]:
        dcf_analysis = (merged_result.get("dcf_analysis") or {}) if isinstance(merged_result, dict) else {}
        adjustments = dcf_analysis.get("dcf_adjustment_instructions", [])
        sector_adjustments = dcf_analysis.get("sector_adjustment_instructions", [])
        java_overrides, mapping_meta = self._map_adjustments_to_java_overrides(
            adjustments=adjustments,
            sector_adjustments=sector_adjustments,
            mapped_segments=mapped_segments,
        )
        java_recalculate_payload = self._build_java_recalculate_payload(java_overrides, mapped_segments)

        dcf = self.valuation_service_client.recalculate_valuation(
            ticker=ticker,
            overrides=java_recalculate_payload,
            auth_header=auth_header,
        )

        return {
            "dcf": dcf,
            "java_overrides": java_overrides,
            "mapping_meta": mapping_meta,
            "java_recalculate_payload": java_recalculate_payload,
        }

    def _run_analyst_on_final_dcf(
        self,
        ticker: str,
        company_name: str,
        dcf: Dict[str, Any],
        news_result: Dict[str, Any],
        mapped_segments: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        final_raw_dcf = self._deep_snake_case(dcf)
        raw_financials = self._build_raw_financials_from_java_output(
            ticker=ticker,
            company_name=company_name,
            raw_dcf=final_raw_dcf,
        )
        preprocessed_dcf = preprocess_dcf_json(final_raw_dcf)
        preprocessed_financials = preprocess_financials_json(raw_financials)

        analyst_inputs = {
            "ticker": ticker,
            "name": company_name,
            "industry": preprocessed_financials.get("profile", {}).get("industry", ""),
            "dcf": preprocessed_dcf,
            "financials": preprocessed_financials,
            "news_content": news_result if isinstance(news_result, dict) else {},
            "skills": build_skill_bundle(
                industry=str(preprocessed_financials.get("profile", {}).get("industry", "")).strip(),
                segments_payload={"segments": mapped_segments or []},
            ),
        }
        llm_result = self.orchestrator.run_agent("analyst", analyst_inputs)

        # Reuse graph normalization fallback logic for section completeness.
        return self.graph_builder._normalize_analyst_result(
            llm_result=llm_result if isinstance(llm_result, dict) else {},
            state_dict={
                "news": news_result if isinstance(news_result, dict) else {},
            },
        )

    def _finalize_and_persist(
        self,
        ticker: str,
        company_name: str,
        audit_run_id: str,
        baseline_result: Dict[str, Any],
        news_result: Dict[str, Any],
        graph_result: Dict[str, Any],
        recalc_result: Dict[str, Any],
        mapped_segments: List[Dict[str, Any]],
    ) -> Response:
        
        dcf = recalc_result["dcf"]
        merged_result = graph_result["merged_result"]
        
        final_raw_dcf = self._deep_snake_case(dcf)
        final_raw_financials = self._build_raw_financials_from_java_output(
            ticker=ticker,
            company_name=company_name,
            raw_dcf=final_raw_dcf,
        )
        final_preprocessed_financials = preprocess_financials_json(final_raw_financials)

        dcf["narrativeDTO"] = self._build_narrative_dto(merged_result, dcf)
        dcf["story"] = None
        dcf["assumptionTransparency"] = self._build_assumption_transparency(
            dcf=dcf,
            adjustments=((merged_result.get("dcf_analysis") or {}).get("dcf_adjustment_instructions", [])
                         if isinstance(merged_result, dict) else []),
            java_overrides=recalc_result.get("java_overrides") or {},
            mapped_segments=mapped_segments,
        )

        company_dto = dcf.get("companyDTO") or {}
        fair_value = company_dto.get("estimatedValuePerShare")
        current_price = company_dto.get("price")

        persisted_payload: Dict[str, Any] = {
            "ticker": ticker,
            "company_name": company_name,
            "merged_result": merged_result,
            "news": news_result["news"],
            "dcf_analysis": {
                **((merged_result.get("dcf_analysis") or {}) if isinstance(merged_result, dict) else {}),
                "fair_value": fair_value,
                "intrinsic_value": fair_value,
            },
            "financials": {
                **final_preprocessed_financials,
                "current_price": current_price,
            },
            "java_valuation_output": dcf,
            "financialDTO": dcf.get("financialDTO"),
            "companyDTO": dcf.get("companyDTO"),
            "terminalValueDTO": dcf.get("terminalValueDTO"),
            "baseYearComparison": dcf.get("baseYearComparison"),
            "valuation_metadata": {
                "audit_run_id": audit_run_id,
                "news_hash": news_result["news_hash"],
                "baseline_financial_data_input": baseline_result.get("financial_data_input_payload") or {},
                "java_overrides": recalc_result["java_overrides"],
                "recalculate_financial_data_input": recalc_result.get("java_recalculate_payload") or {},
                "mapping_meta": recalc_result["mapping_meta"],
                "orchestration_version": "ticker-first-v1",
            },
        }

        valuation_id = valuation_service.save_valuation(
            ticker=ticker,
            company_name=company_name,
            valuation_data=persisted_payload,
        )

        response_payload = {
            "ticker": ticker,
            "company_name": company_name,
            "valuation_id": valuation_id,
            "user_valuation_id": None,
            "audit_run_id": audit_run_id,
            "dcf": dcf,
            "agent_analysis": graph_result["agent_analysis_payload"],
            "news_sources": self._extract_news_sources(news_result["news"]),
            "segments": mapped_segments,
            "applied_overrides": recalc_result["java_overrides"],
            "financial_data_input": {
                "baseline": baseline_result.get("financial_data_input_payload") or {},
                "recalculate": recalc_result.get("java_recalculate_payload") or {},
            },
        }

        return jsonify(response_payload), 200

    def _map_adjustments_to_java_overrides(
        self,
        adjustments: Any,
        sector_adjustments: Any = None,
        mapped_segments: Optional[List[Dict[str, Any]]] = None,
    ) -> tuple[Dict[str, Any], Dict[str, Any]]:
        """Map agent dcf_adjustment_instructions to Java FinancialDataInput overrides."""
        if not isinstance(adjustments, list):
            adjustments = []
        if not isinstance(sector_adjustments, list):
            sector_adjustments = []

        overrides: Dict[str, Any] = {}
        mapped = []
        unmapped = []

        for item in adjustments:
            if not isinstance(item, dict):
                continue

            param = str(item.get("parameter", "")).strip().lower()
            unit = str(item.get("unit", "")).strip().lower()
            new_value = item.get("new_value")
            try:
                new_value = float(new_value)
            except (TypeError, ValueError):
                unmapped.append({"parameter": param, "reason": "invalid_new_value", "value": item.get("new_value")})
                continue
            rounded_value = round(new_value, 2)

            if param == "revenue_cagr":
                overrides["compoundAnnualGrowth2_5"] = rounded_value
            elif param == "operating_margin":
                overrides["targetPreTaxOperatingMargin"] = rounded_value
            elif param == "wacc":
                overrides["initialCostCapital"] = round(self._normalize_percent_like_value(new_value), 2)
            elif param in {"terminal_growth", "terminal_growth_rate"}:
                overrides["terminalGrowthRate"] = round(self._normalize_percent_like_value(new_value), 2)
            elif param == "tax_rate":
                overrides["overrideAssumptionTaxRate"] = {
                    "overrideCost": round(self._normalize_percent_like_value(new_value), 2),
                    "isOverride": True,
                    "additionalInputValue": 0.0,
                    "additionalRadioValue": None,
                }
            elif param in {"sales_to_capital", "sales_to_capital_ratio", "reinvestment", "reinvestment_rate"}:
                stc_value = round(self._normalize_sales_to_capital_value(new_value), 2)
                overrides["salesToCapitalYears1To5"] = stc_value
                overrides.setdefault("salesToCapitalYears6To10", stc_value)
            else:
                unmapped.append({"parameter": param, "reason": "unsupported_parameter", "value": new_value, "unit": unit})
                continue

            mapped.append({
                "parameter": param,
                "new_value": rounded_value,
                "unit": unit,
                "rationale": str(item.get("rationale", "")).strip(),
            })

        allowed_adjustment_types = {"absolute", "relative_multiplier", "relative_additive"}
        allowed_timeframes = {"years_1_to_5", "years_6_to_10", "both"}
        allowed_sector_params = {"revenue_growth", "operating_margin", "sales_to_capital"}
        valid_sector_lookup = {
            str(item.get("sector", "")).strip().lower(): str(item.get("sector", "")).strip()
            for item in (mapped_segments or [])
            if isinstance(item, dict) and str(item.get("sector", "")).strip()
        }
        sector_mapped = []
        sector_unmapped = []
        java_sector_overrides: List[Dict[str, Any]] = []

        for item in sector_adjustments:
            if not isinstance(item, dict):
                continue

            raw_sector = str(item.get("sector", "")).strip()
            sector_name = valid_sector_lookup.get(raw_sector.lower())
            if not sector_name:
                sector_unmapped.append({"sector": raw_sector, "reason": "unknown_sector"})
                continue

            parameter = str(item.get("parameter", "")).strip().lower()
            if parameter not in allowed_sector_params:
                sector_unmapped.append(
                    {"sector": sector_name, "parameter": parameter, "reason": "unsupported_parameter"}
                )
                continue

            value = item.get("value", item.get("new_value"))
            try:
                value = float(value)
            except (TypeError, ValueError):
                sector_unmapped.append(
                    {"sector": sector_name, "parameter": parameter, "reason": "invalid_value", "value": value}
                )
                continue

            unit = str(item.get("unit", "")).strip().lower()
            if parameter == "sales_to_capital":
                normalized_value = round(self._normalize_sales_to_capital_value(value), 2)
            elif unit in {"percent", "%"}:
                normalized_value = round(self._normalize_percent_like_value(value), 2)
            else:
                normalized_value = round(value, 2)

            adjustment_type = str(item.get("adjustment_type", "absolute")).strip().lower()
            if adjustment_type not in allowed_adjustment_types:
                sector_unmapped.append(
                    {
                        "sector": sector_name,
                        "parameter": parameter,
                        "reason": "invalid_adjustment_type",
                        "adjustment_type": adjustment_type,
                    }
                )
                continue

            timeframe = str(item.get("timeframe", "both")).strip().lower()
            if timeframe not in allowed_timeframes:
                timeframe = "both"

            java_item = {
                "sectorName": sector_name,
                "parameterType": parameter,
                "value": normalized_value,
                "adjustmentType": adjustment_type,
                "timeframe": timeframe,
            }
            java_sector_overrides.append(java_item)
            sector_mapped.append(
                {
                    "sector": sector_name,
                    "parameter": parameter,
                    "value": normalized_value,
                    "adjustmentType": adjustment_type,
                    "timeframe": timeframe,
                }
            )

        if java_sector_overrides:
            overrides["sectorOverrides"] = java_sector_overrides

        return overrides, {
            "count": len(adjustments),
            "mapped": mapped,
            "unmapped": unmapped,
            "sector_count": len(sector_adjustments),
            "sector_mapped": sector_mapped,
            "sector_unmapped": sector_unmapped,
        }

    @staticmethod
    def _normalize_percent_like_value(value: float) -> float:
        """
        Normalize rate values that may arrive as decimal (0.105) or percent (10.5).
        Java FinancialDataInput override fields expect percent-form numbers.
        """
        if abs(value) <= 1.0:
            return value * 100.0
        return value

    @staticmethod
    def _normalize_sales_to_capital_value(value: float) -> float:
        """
        Java stores sales-to-capital in percentage-style units (e.g., 200 = 2.0x).
        """
        if abs(value) <= 10.0:
            return value * 100.0
        return value

    def _extract_mapped_segments(self, segments_result: Any, expected_industry: str = "") -> List[Dict[str, Any]]:
        if not isinstance(segments_result, dict):
            return []

        normalized_expected_industry = self._normalize_industry(expected_industry)
        raw_segments = segments_result.get("segments")
        if not isinstance(raw_segments, list):
            return []

        candidates: List[Dict[str, Any]] = []
        for item in raw_segments:
            if not isinstance(item, dict):
                continue
            sector = str(item.get("sector", "")).strip()
            if not sector or sector.upper() == "UNKNOWN":
                continue

            mapping_score = self._safe_number(item.get("mapping_score"))
            if mapping_score is None:
                mapping_score = 0.0

            item_industry = self._normalize_industry(item.get("industry", ""))
            if not item_industry:
                item_industry = SECTOR_TO_INDUSTRY.get(sector.lower(), "")

            normalized_item = dict(item)
            normalized_item["industry"] = item_industry
            normalized_item["_mapping_score"] = mapping_score
            normalized_item["_industry_match"] = (
                not normalized_expected_industry
                or not item_industry
                or item_industry == normalized_expected_industry
            )
            candidates.append(normalized_item)

        if not candidates:
            return []

        strict = [
            segment for segment in candidates
            if segment["_mapping_score"] >= 0.55 and segment["_industry_match"]
        ]

        if len(strict) >= 2:
            selected = strict
        else:
            relaxed = [
                segment for segment in candidates
                if segment["_mapping_score"] >= 0.45
                and (segment["_industry_match"] or not normalized_expected_industry)
            ]
            if len(relaxed) >= 2:
                selected = relaxed
            else:
                selected = sorted(
                    candidates,
                    key=lambda segment: segment["_mapping_score"],
                    reverse=True,
                )[:3]

        deduped: Dict[str, Dict[str, Any]] = {}
        for segment in selected:
            sector_key = str(segment.get("sector", "")).strip().lower()
            if not sector_key:
                continue
            existing = deduped.get(sector_key)
            if not existing or segment["_mapping_score"] > existing["_mapping_score"]:
                deduped[sector_key] = segment

        final_segments = list(deduped.values())
        final_segments.sort(
            key=lambda segment: (
                self._safe_number(segment.get("revenue_share")) or 0.0,
                segment["_mapping_score"],
            ),
            reverse=True,
        )

        cleaned: List[Dict[str, Any]] = []
        for segment in final_segments:
            normalized_segment = dict(segment)
            normalized_segment.pop("_mapping_score", None)
            normalized_segment.pop("_industry_match", None)
            cleaned.append(normalized_segment)

        return cleaned

    def _build_java_recalculate_payload(
        self,
        overrides: Dict[str, Any],
        mapped_segments: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        payload = dict(overrides or {})
        java_segments = self._to_java_segments(mapped_segments)
        if java_segments:
            payload["segments"] = {"segments": java_segments}
        return payload

    def _to_java_segments(self, mapped_segments: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
        java_segments: List[Dict[str, Any]] = []
        for segment in mapped_segments or []:
            if not isinstance(segment, dict):
                continue

            sector = str(segment.get("sector", "")).strip()
            if not sector:
                continue

            revenue_share = self._safe_number(segment.get("revenue_share"))
            operating_margin = self._safe_number(segment.get("operating_margin"))
            mapping_score = self._safe_number(segment.get("mapping_score"))

            components = []
            raw_components = segment.get("components")
            if isinstance(raw_components, list):
                components = [str(item) for item in raw_components if item is not None]

            java_segments.append({
                "sector": sector,
                "industry": str(segment.get("industry", "")).strip() or None,
                "components": components,
                "mappingScore": mapping_score,
                "revenueShare": revenue_share,
                "operatingMargin": operating_margin,
            })

        return java_segments

    def _extract_news_sources(self, news_result: Any) -> List[Dict[str, Any]]:
        if not isinstance(news_result, dict):
            return []
        raw_sources = news_result.get("sources")
        if not isinstance(raw_sources, list):
            return []

        normalized: List[Dict[str, Any]] = []
        seen_urls = set()
        for item in raw_sources:
            if not isinstance(item, dict):
                continue
            url = str(item.get("url", "")).strip()
            if not url or url in seen_urls:
                continue
            seen_urls.add(url)
            normalized.append({
                "title": str(item.get("title", "")).strip() or "Source",
                "url": url,
                "source": str(item.get("source", "")).strip(),
                "category": str(item.get("category", "")).strip(),
            })
        return normalized

    def _build_narrative_dto(self, merged_result: Dict[str, Any], dcf: Dict[str, Any]) -> Dict[str, Any]:
        merged = merged_result if isinstance(merged_result, dict) else {}
        company_dto = dcf.get("companyDTO") or {}
        terminal_dto = dcf.get("terminalValueDTO") or {}
        financial_dto = dcf.get("financialDTO") or {}

        growth = merged.get("growth") or {}
        margins = merged.get("margins") or {}
        investment_efficiency = merged.get("investment_efficiency") or {}
        risks = merged.get("risks") or {}
        key_takeaways = merged.get("key_takeaways") or {}

        growth_rates = financial_dto.get("revenueGrowthRate") if isinstance(financial_dto, dict) else []
        margins_series = financial_dto.get("ebitOperatingMargin") if isinstance(financial_dto, dict) else []
        cost_of_capital = financial_dto.get("costOfCapital") if isinstance(financial_dto, dict) else []

        initial_growth = self._first_numeric(growth_rates)
        terminal_growth = self._safe_number(terminal_dto.get("growthRate"))
        if terminal_growth is None:
            terminal_growth = self._last_numeric(growth_rates)

        initial_wacc = self._first_numeric(cost_of_capital)
        terminal_wacc = self._safe_number(terminal_dto.get("costOfCapital"))
        if terminal_wacc is None:
            terminal_wacc = self._last_numeric(cost_of_capital)

        margin_values = [v for v in (self._safe_number(x) for x in (margins_series or [])) if v is not None]
        avg_margin = (sum(margin_values) / len(margin_values)) if margin_values else None

        intrinsic_value = self._safe_number(company_dto.get("estimatedValuePerShare"))
        market_price = self._safe_number(company_dto.get("price"))

        premium_to_intrinsic = None
        if intrinsic_value not in (None, 0.0) and market_price is not None:
            premium_to_intrinsic = ((market_price - intrinsic_value) / intrinsic_value) * 100.0

        explicit_pv = self._safe_number(company_dto.get("pvCFOverNext10Years"))
        terminal_pv = self._safe_number(company_dto.get("pvTerminalValue"))
        total_pv = self._safe_number(company_dto.get("sumOfPV"))
        terminal_contribution = None
        if terminal_pv is not None and total_pv not in (None, 0.0):
            terminal_contribution = (terminal_pv / total_pv) * 100.0

        key_assumptions_narrative = (
            f"Near-term growth starts at {self._format_number(initial_growth)}% and converges to "
            f"{self._format_number(terminal_growth)}%. Cost of capital converges toward "
            f"{self._format_number(terminal_wacc)}%."
        )
        valuation_summary_narrative = (
            f"Intrinsic value is ${self._format_number(intrinsic_value)} per share versus market price "
            f"${self._format_number(market_price)}."
        )

        narrative_dto: Dict[str, Any] = {
            "growth": {
                "narrative": growth.get("narrative") or "Revenue growth assumptions follow current operating trends.",
                "title": growth.get("title") or "Growth",
            },
            "margins": {
                "narrative": margins.get("narrative") or "Margin assumptions reflect operating efficiency and competitive intensity.",
                "title": margins.get("title") or "Margins",
            },
            "investmentEfficiency": {
                "narrative": investment_efficiency.get("narrative") or "Investment efficiency is evaluated through reinvestment productivity.",
                "title": investment_efficiency.get("title") or "Investment Efficiency",
            },
            "risks": {
                "narrative": risks.get("narrative") or "Key risks include execution, macro conditions, and competitive pressure.",
                "title": risks.get("title") or "Risks",
            },
            "keyTakeaways": {
                "narrative": key_takeaways.get("narrative") or "Valuation reflects the interaction of growth, margins, capital efficiency, and risk.",
                "title": key_takeaways.get("title") or "Key Takeaways",
            },
            "keyAssumptions": {
                "narrative": key_assumptions_narrative,
                "growthRate": {
                    "initial": self._format_number(initial_growth),
                    "terminal": self._format_number(terminal_growth),
                },
                "costOfCapital": {
                    "initial": self._format_number(initial_wacc),
                    "terminal": self._format_number(terminal_wacc),
                },
                "operatingMargin": {
                    "average": self._format_number(avg_margin),
                },
                "terminalGrowthRate": terminal_growth,
            },
            "valueDrivers": {
                "narrative": "Terminal value contribution and explicit period cash flows remain core valuation drivers.",
                "terminalValueContribution": {
                    "pdata": self._format_number(terminal_contribution),
                },
                "explicitPeriodPv": {
                    "pdata": self._format_number(explicit_pv / 1_000_000_000 if explicit_pv is not None else None),
                },
                "terminalValuePv": {
                    "pdata": self._format_number(terminal_pv / 1_000_000_000 if terminal_pv is not None else None),
                },
            },
            "valuationSummary": {
                "narrative": valuation_summary_narrative,
                "intrinsicValuePerShare": {
                    "pdata": self._format_number(intrinsic_value),
                },
                "currentMarketPrice": {
                    "pdata": self._format_number(market_price),
                },
                "premiumToIntrinsic": {
                    "percentage": self._format_number(premium_to_intrinsic),
                },
            },
            "sensitivityAndUncertainties": {
                "narrative": "Valuation sensitivity is concentrated in discount rate and terminal growth assumptions.",
                "sensitivityExamples": {
                    "terminalGrowthRate": {},
                    "wacc": {},
                },
            },
        }

        scenario_analysis = merged.get("scenarioAnalysis") or merged.get("scenario_analysis")
        if scenario_analysis:
            narrative_dto["scenarioAnalysis"] = scenario_analysis

        bull_bear_debate = merged.get("bullBearDebate") or merged.get("bull_bear_debate")
        if bull_bear_debate:
            narrative_dto["bullBearDebate"] = bull_bear_debate

        return narrative_dto

    def _build_assumption_transparency(
        self,
        dcf: Dict[str, Any],
        adjustments: Any,
        java_overrides: Dict[str, Any],
        mapped_segments: List[Dict[str, Any]],
    ) -> Dict[str, Any]:
        financial_dto = dcf.get("financialDTO") or {}
        terminal_dto = dcf.get("terminalValueDTO") or {}

        cost_of_capital_values = financial_dto.get("costOfCapital") if isinstance(financial_dto, dict) else []
        growth_values = financial_dto.get("revenueGrowthRate") if isinstance(financial_dto, dict) else []
        margin_values = financial_dto.get("ebitOperatingMargin") if isinstance(financial_dto, dict) else []
        sales_to_capital_values = financial_dto.get("salesToCapitalRatio") if isinstance(financial_dto, dict) else []

        initial_cost_of_capital = self._normalize_percent_output(self._first_numeric(cost_of_capital_values))
        terminal_cost_of_capital = self._normalize_percent_output(
            self._safe_number(terminal_dto.get("costOfCapital")) or self._last_numeric(cost_of_capital_values)
        )
        risk_free_rate = self._normalize_percent_output(self._safe_number(terminal_dto.get("growthRate")))
        equity_risk_premium = None
        if risk_free_rate is not None and terminal_cost_of_capital is not None:
            equity_risk_premium = round(max(0.0, terminal_cost_of_capital - risk_free_rate), 2)

        revenue_growth = self._normalize_percent_output(
            self._safe_number(java_overrides.get("compoundAnnualGrowth2_5"))
        )
        if revenue_growth is None:
            revenue_growth = self._average_percent_window(growth_values, 2, 5)

        operating_margin = self._normalize_percent_output(
            self._safe_number(java_overrides.get("targetPreTaxOperatingMargin"))
        )
        if operating_margin is None:
            operating_margin = self._average_percent_window(margin_values, 2, 5)

        sales_to_capital_1_5 = self._normalize_sales_to_capital_output(
            self._safe_number(java_overrides.get("salesToCapitalYears1To5"))
        )
        if sales_to_capital_1_5 is None:
            sales_to_capital_1_5 = self._normalize_sales_to_capital_output(self._projection_value(sales_to_capital_values, 1))

        sales_to_capital_6_10 = self._normalize_sales_to_capital_output(
            self._safe_number(java_overrides.get("salesToCapitalYears6To10"))
        )
        if sales_to_capital_6_10 is None:
            sales_to_capital_6_10 = self._normalize_sales_to_capital_output(self._last_projection_value(sales_to_capital_values))

        has_growth_override = java_overrides.get("compoundAnnualGrowth2_5") is not None
        has_margin_override = java_overrides.get("targetPreTaxOperatingMargin") is not None
        has_stc_override = (
            java_overrides.get("salesToCapitalYears1To5") is not None
            or java_overrides.get("salesToCapitalYears6To10") is not None
        )
        has_wacc_override = java_overrides.get("initialCostCapital") is not None

        return {
            "valuationModel": dcf.get("primaryModel") or "FCFF",
            "industryUs": dcf.get("industryUs"),
            "industryGlobal": dcf.get("industryGlobal"),
            "currency": dcf.get("currency") or dcf.get("stockCurrency"),
            "segmentCount": len(mapped_segments or []),
            "segmentAware": len(mapped_segments or []) > 1,
            "discountRate": {
                "riskFreeRate": risk_free_rate,
                "equityRiskPremium": equity_risk_premium,
                "initialCostOfCapital": initial_cost_of_capital,
                "terminalCostOfCapital": terminal_cost_of_capital,
                "costOfCapitalFormula": "Terminal WACC = risk-free rate + mature market premium; path values from FCFF model output.",
                "riskFreeRateSource": "Final FCFF terminal growth anchor (bounded by risk-free assumption).",
                "equityRiskPremiumSource": "Derived from terminal WACC minus risk-free anchor.",
                "initialCostOfCapitalSource": "Analyzer-adjusted override" if has_wacc_override else "Final FCFF output",
            },
            "operatingAssumptions": {
                "revenueGrowthRateYears2To5": revenue_growth,
                "targetOperatingMargin": operating_margin,
                "salesToCapitalYears1To5": sales_to_capital_1_5,
                "salesToCapitalYears6To10": sales_to_capital_6_10,
                "revenueGrowthSource": "Analyzer-adjusted override" if has_growth_override else "Final FCFF output",
                "operatingMarginSource": "Analyzer-adjusted override" if has_margin_override else "Final FCFF output",
                "salesToCapitalSource": "Analyzer-adjusted override" if has_stc_override else "Final FCFF output",
                "revenueGrowthRationale": "Derived from analyzer instruction rationale" if has_growth_override else "No analyzer override applied.",
                "operatingMarginRationale": "Derived from analyzer instruction rationale" if has_margin_override else "No analyzer override applied.",
                "salesToCapitalRationale": "Derived from analyzer instruction rationale" if has_stc_override else "No analyzer override applied.",
            },
            "adjustmentRationales": self._extract_adjustment_rationales(adjustments),
            "notes": [
                "Rates are shown in percent.",
                "Sales-to-capital is shown as x multiple.",
                "Values are rounded and reflect the final valuation run.",
            ],
        }

    def _extract_adjustment_rationales(self, adjustments: Any) -> Dict[str, str]:
        if not isinstance(adjustments, list):
            return {}

        rationales: Dict[str, str] = {}
        for item in adjustments:
            if not isinstance(item, dict):
                continue
            parameter = str(item.get("parameter", "")).strip().lower()
            rationale = str(item.get("rationale", "")).strip()
            if not rationale:
                continue
            value = self._safe_number(item.get("new_value"))
            unit = str(item.get("unit", "")).strip().lower()
            with_value = rationale
            if value is not None:
                with_value = f"{rationale} (Adjusted to {self._format_adjustment_value(value, unit)})"
            if parameter == "revenue_cagr":
                rationales["revenueGrowth"] = with_value
            elif parameter == "operating_margin":
                rationales["operatingMargin"] = with_value
            elif parameter in {"sales_to_capital", "sales_to_capital_ratio"}:
                rationales["salesToCapital"] = with_value
            elif parameter in {"wacc", "cost_of_capital"}:
                rationales["costOfCapital"] = with_value
        return rationales

    @staticmethod
    def _format_adjustment_value(value: float, unit: str) -> str:
        rounded = round(value, 2)
        if unit in {"percent", "%"}:
            return f"{rounded:.2f}%"
        if unit in {"x", "times", "multiple"}:
            return f"{rounded:.2f}x"
        return f"{rounded:.2f}"

    @staticmethod
    def _normalize_percent_output(value: Optional[float]) -> Optional[float]:
        if value is None:
            return None
        normalized = float(value)
        if abs(normalized) <= 1.0:
            normalized *= 100.0
        elif abs(normalized) > 100.0:
            normalized /= 100.0
        return round(normalized, 2)

    @staticmethod
    def _normalize_sales_to_capital_output(value: Optional[float]) -> Optional[float]:
        if value is None:
            return None
        normalized = float(value)
        if abs(normalized) > 10.0:
            normalized /= 100.0
        return round(normalized, 2)

    def _average_percent_window(self, values: Any, start_index: int, end_index: int) -> Optional[float]:
        if not isinstance(values, list):
            return None
        collected: List[float] = []
        for index in range(start_index, end_index + 1):
            if index < 0 or index >= len(values):
                continue
            parsed = self._safe_number(values[index])
            normalized = self._normalize_percent_output(parsed)
            if normalized is not None:
                collected.append(normalized)
        if not collected:
            return None
        return round(sum(collected) / len(collected), 2)

    def _projection_value(self, values: Any, index: int) -> Optional[float]:
        if not isinstance(values, list):
            return None
        if index < 0 or index >= len(values):
            return None
        return self._safe_number(values[index])

    def _last_projection_value(self, values: Any) -> Optional[float]:
        if not isinstance(values, list):
            return None
        for value in reversed(values[:-1] if len(values) > 1 else values):
            parsed = self._safe_number(value)
            if parsed is not None:
                return parsed
        return None

    @staticmethod
    def _safe_number(value: Any) -> Optional[float]:
        try:
            if value is None:
                return None
            return float(value)
        except (TypeError, ValueError):
            return None

    def _first_numeric(self, values: Any) -> Optional[float]:
        if not isinstance(values, list):
            return None
        for value in values:
            parsed = self._safe_number(value)
            if parsed is not None:
                return parsed
        return None

    def _last_numeric(self, values: Any) -> Optional[float]:
        if not isinstance(values, list):
            return None
        for value in reversed(values):
            parsed = self._safe_number(value)
            if parsed is not None:
                return parsed
        return None

    @staticmethod
    def _format_number(value: Optional[float]) -> str:
        if value is None:
            return "0.00"
        return f"{value:.2f}"

    def _build_raw_financials_from_java_output(
        self,
        ticker: str,
        company_name: str,
        raw_dcf: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Build minimal FinancialDataInput-shaped payload for preprocessing/agents."""
        def _first_present(*values: Any) -> Optional[str]:
            for value in values:
                if value is None:
                    continue
                if isinstance(value, str):
                    cleaned = value.strip()
                    if cleaned and cleaned.lower() not in {"none", "null"}:
                        return cleaned
                    continue
                return str(value)
            return None

        financial_dto = dict(raw_dcf.get("financial_dto") or {})
        company_dto = dict(raw_dcf.get("company_dto") or {})
        base_year_comparison = dict(raw_dcf.get("base_year_comparison") or {})
        terminal_value_dto = dict(raw_dcf.get("terminal_value_dto") or {})
        assumption_transparency = dict(raw_dcf.get("assumption_transparency") or {})
        discount_rate = assumption_transparency.get("discount_rate") or {}

        company_data_dto = raw_dcf.get("company_data_dto") or {}
        basic_info_dto = company_data_dto.get("basic_info_data_dto") or {}
        info_dto = raw_dcf.get("info_dto") or {}

        stock_price = self._safe_number(
            company_dto.get("price")
            or financial_dto.get("stock_price")
        )
        shares_outstanding = self._safe_number(
            company_dto.get("number_of_shares")
            or financial_dto.get("no_of_share_outstanding")
        )
        market_cap = None
        if stock_price is not None and shares_outstanding is not None:
            market_cap = stock_price * shares_outstanding

        revenues = financial_dto.get("revenues")
        revenue_ttm = self._safe_number(base_year_comparison.get("revenue")) or self._first_numeric(revenues)
        revenue_growth_pct = self._safe_number(base_year_comparison.get("revenue_growth_company"))
        revenue_ltm = None
        if revenue_ttm is not None and revenue_growth_pct is not None and revenue_growth_pct > -99.0:
            revenue_ltm = revenue_ttm / (1.0 + (revenue_growth_pct / 100.0))

        ebit_operating_income = financial_dto.get("ebit_operating_income")
        operating_income_ttm = self._safe_number(base_year_comparison.get("operating_income")) or self._first_numeric(ebit_operating_income)
        operating_income_ltm = None
        if operating_income_ttm is not None and revenue_growth_pct is not None and revenue_growth_pct > -99.0:
            operating_income_ltm = operating_income_ttm / (1.0 + (revenue_growth_pct / 100.0))

        book_value_debt = self._safe_number(company_dto.get("debt"))
        cash_and_marketable = self._safe_number(company_dto.get("cash"))
        minority_interest = self._safe_number(company_dto.get("minority_interests"))
        invested_capital_start = self._first_numeric(financial_dto.get("invested_capital"))
        book_value_equity = None
        if invested_capital_start is not None and book_value_debt is not None and cash_and_marketable is not None:
            # Approximate book equity from invested capital identity when direct book equity is unavailable.
            approx_equity = invested_capital_start + cash_and_marketable - book_value_debt
            if approx_equity > 0:
                book_value_equity = approx_equity

        tax_rate_series = financial_dto.get("tax_rate")
        effective_tax_rate = self._first_numeric(tax_rate_series)
        marginal_tax_rate = self._last_numeric(tax_rate_series)
        risk_free_rate = self._safe_number(discount_rate.get("risk_free_rate"))
        initial_cost_of_capital = (
            self._safe_number(discount_rate.get("initial_cost_of_capital"))
            or self._first_numeric(financial_dto.get("cost_of_capital"))
            or self._safe_number(terminal_value_dto.get("cost_of_capital"))
        )

        debt_to_equity_pct = None
        if book_value_debt is not None and book_value_equity is not None and book_value_equity > 0:
            debt_to_equity_pct = (book_value_debt / book_value_equity) * 100.0

        financial_dto.update({
            "stock_price": stock_price,
            "no_of_share_outstanding": shares_outstanding,
            "revenue_ttm": revenue_ttm,
            "revenue_ltm": revenue_ltm,
            "operating_income_ttm": operating_income_ttm,
            "operating_income_ltm": operating_income_ltm,
            "book_value_equality_ttm": book_value_equity,
            "book_value_debt_ttm": book_value_debt,
            "cash_and_markabl_ttm": cash_and_marketable,
            "minority_interest_ttm": minority_interest,
            "effective_tax_rate": effective_tax_rate,
            "marginal_tax_rate": marginal_tax_rate,
            "risk_free_rate": risk_free_rate,
            "initial_cost_capital": initial_cost_of_capital,
            "industry": (
                financial_dto.get("industry")
                or raw_dcf.get("industry_global")
                or raw_dcf.get("industry_us")
            ),
        })

        industry_us = (
            raw_dcf.get("industry_us")
            or financial_dto.get("industry_us")
            or ""
        )
        industry_global = (
            raw_dcf.get("industry_global")
            or financial_dto.get("industry_global")
            or industry_us
        )
        financial_dto.setdefault("industry", industry_global or industry_us)
        country_of_incorporation = _first_present(
            raw_dcf.get("country_of_incorporation"),
            raw_dcf.get("country"),
            basic_info_dto.get("country_of_incorporation"),
            basic_info_dto.get("country"),
            info_dto.get("country_of_incorporation"),
            info_dto.get("country"),
            financial_dto.get("country_of_incorporation"),
            financial_dto.get("country"),
        )
        website = _first_present(
            raw_dcf.get("website"),
            basic_info_dto.get("website"),
            info_dto.get("website"),
            financial_dto.get("website"),
        )

        return {
            "basic_info_data_dto": {
                "ticker": ticker,
                "company_name": company_name,
                "currency": raw_dcf.get("currency") or raw_dcf.get("stock_currency"),
                "stock_currency": raw_dcf.get("stock_currency"),
                "country_of_incorporation": country_of_incorporation,
                "website": website,
                "industry_us": industry_us,
                "industry_global": industry_global,
                "market_cap": market_cap,
                "debtToEquity": debt_to_equity_pct,
                "beta": self._safe_number(
                    basic_info_dto.get("beta")
                    or info_dto.get("beta")
                    or raw_dcf.get("beta")
                ),
            },
            "financial_data_dto": financial_dto,
        }

    def _normalize_industry(self, value: Any) -> str:
        normalized = str(value or "").strip().lower()
        if not normalized:
            return ""
        normalized = normalized.replace("_", "-").replace(" ", "-")
        if normalized in SECTOR_TO_INDUSTRY:
            return SECTOR_TO_INDUSTRY[normalized]
        if normalized in CANONICAL_INDUSTRIES:
            return normalized
        if normalized in INDUSTRY_ALIASES:
            return INDUSTRY_ALIASES[normalized]
        return normalized

    def _build_fallback_segments(
        self,
        company_name: str,
        preprocessed_financials: Dict[str, Any],
        expected_industry: str,
    ) -> List[Dict[str, Any]]:
        profile = preprocessed_financials.get("profile", {}) or {}
        industry_candidates = [
            str(profile.get("industry", "")).strip().lower(),
            str(profile.get("industry_us", "")).strip().lower(),
            str(profile.get("industry_global", "")).strip().lower(),
        ]
        fallback_sector = self._select_fallback_sector(industry_candidates, expected_industry)
        if not fallback_sector:
            return []

        profitability = preprocessed_financials.get("profitability", {}) or {}
        margin_pct = self._safe_number(profitability.get("operating_margin_ltm_pct"))
        if margin_pct is None:
            margin_pct = self._safe_number(profitability.get("operating_margin_ttm_pct"))
        operating_margin = 0.15
        if margin_pct is not None:
            operating_margin = max(0.0, min(1.0, margin_pct / 100.0))

        return [{
            "components": ["Core Business"],
            "industry": expected_industry or SECTOR_TO_INDUSTRY.get(fallback_sector, ""),
            "sector": fallback_sector,
            "mapping_score": 0.70,
            "operating_margin": operating_margin,
            "revenue_share": 1.0,
            "fallback_reason": f"deterministic_sector_inference:{company_name}",
        }]

    def _select_fallback_sector(self, candidates: List[str], expected_industry: str) -> str:
        normalized_candidates = []
        for candidate in candidates:
            cleaned = str(candidate or "").strip().lower().replace("_", "-").replace(" ", "-")
            if cleaned:
                normalized_candidates.append(cleaned)

        for candidate in normalized_candidates:
            if candidate in SECTOR_TO_INDUSTRY:
                return candidate

        relevant_mapping = [
            entry for entry in industry_mapping
            if not expected_industry or self._normalize_industry(entry.get("industry", "")) == expected_industry
        ]
        if not relevant_mapping:
            relevant_mapping = list(industry_mapping)
        if not relevant_mapping:
            return ""

        query_text = " ".join(normalized_candidates) or expected_industry or ""
        if not query_text:
            return str(relevant_mapping[0].get("sector", ""))

        best_score = -1.0
        best_sector = ""
        for entry in relevant_mapping:
            sector = str(entry.get("sector", "")).strip().lower()
            name = str(entry.get("name", "")).strip().lower()
            if not sector:
                continue
            score = max(
                SequenceMatcher(None, query_text, sector).ratio(),
                SequenceMatcher(None, query_text, name).ratio(),
            )
            if score > best_score:
                best_score = score
                best_sector = sector
        return best_sector

    def _deep_snake_case(self, obj: Any) -> Any:
        if isinstance(obj, dict):
            return {self._camel_to_snake(str(k)): self._deep_snake_case(v) for k, v in obj.items()}
        if isinstance(obj, list):
            return [self._deep_snake_case(v) for v in obj]
        return obj

    @staticmethod
    def _camel_to_snake(value: str) -> str:
        value = value.replace("-", "_")
        value = re.sub(r"([a-z0-9])([A-Z])", r"\1_\2", value)
        return value.lower()
    
    def run(self):
        """Run the Flask application."""
        self.app.run(
            host=APIConfig.HOST, 
            port=APIConfig.PORT, 
            debug=APIConfig.DEBUG
        )

if __name__ == '__main__':
    app = StockValuationApp()
    app.run()
