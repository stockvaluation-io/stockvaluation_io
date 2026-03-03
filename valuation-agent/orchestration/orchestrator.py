"""
Enhanced agent orchestration layer with modular prompt loading and model routing.
"""
import json
import logging
import re
from typing import Dict, Any, Optional, List
from urllib.parse import urlparse

from llm.routing import get_default_provider, resolve_agent_llm
from config.prompt_registry import get_prompt
from llm.client_manager import LLMClientManager
from prompts.utils import get_prompt_dumper
from storage.cache.local_cache import (
    fetch_news_summary_from_cache, save_news_summary_to_cache,
    fetch_earnings_summary_from_cache, save_earnings_summary_to_cache,
    fetch_macro_news_from_cache, save_macro_news_to_cache,
    get_cached_segments, set_cached_segments,
)
from domain.knowledge.tool_definitions import industry_mapping
from domain.processing.helpers import infer_country_for_macro, safe_json_loads

logger = logging.getLogger(__name__)

class AgentOrchestrator:
    """Enhanced orchestrator with modular prompt loading and model routing."""
    
    def __init__(self, default_provider: Optional[str] = None):
        self.default_provider = default_provider or get_default_provider()
        self.llm_manager = LLMClientManager()

    def run_agent(self, agent_name: str, inputs: Dict[str, Any], provider: str = None) -> Dict[str, Any]:
        """
        Run a specific agent with dynamic prompt loading and model routing.
        
        Args:
            agent_name: Name of the agent to run
            inputs: Input data for the agent
            provider: Provider to use (optional, uses default if not specified)
            
        Returns:
            Agent execution result
        """
        try:
            if agent_name == "news":
                logger.debug("Running %s via non-LLM orchestrated path", agent_name)
                return self._run_news_agent(inputs)

            resolved = resolve_agent_llm(
                agent_name=agent_name,
                provider=provider,
                default_provider=self.default_provider,
            )
            if not resolved:
                return {"error": f"No LLM routing configured for agent {agent_name}"}

            client = self.llm_manager.get_client(
                provider=resolved["provider"],
                model=resolved["model"],
                use_case=resolved["use_case"],
                max_tokens=resolved["max_tokens"],
            )
            if not client:
                return {
                    "error": (
                        "No client available for provider "
                        f"{resolved['provider']} model {resolved['model']}"
                    )
                }

            prompt = get_prompt(agent_name, inputs)
            if not prompt:
                logger.error("Failed to load prompt for agent %s", agent_name)
                return {"error": f"Failed to load prompt for agent {agent_name}"}

            prompt_timestamp = None
            # segments agent injects dynamic segment context into prompt later, so we dump
            # its final rendered prompt inside _run_segments_agent for accuracy.
            if agent_name != "segments":
                dumper = get_prompt_dumper()
                prompt_timestamp = dumper.dump_prompt(
                    agent_name=agent_name,
                    prompt=prompt,
                    inputs=inputs,
                    metadata={
                        "provider": resolved["provider"],
                        "model": resolved["model"],
                        "use_case": resolved["use_case"],
                        "temperature": resolved["temperature"],
                    },
                )

            inputs["_prompt_timestamp"] = prompt_timestamp
            inputs["_llm_context"] = {
                "provider": resolved["provider"],
                "model": resolved["model"],
                "use_case": resolved["use_case"],
                "temperature": resolved["temperature"],
            }
            logger.debug(
                "Running %s with %s/%s (use_case=%s)",
                agent_name,
                resolved["provider"],
                resolved["model"],
                resolved["use_case"],
            )

            data = None
            if agent_name == "analyst":
                data = self._run_analyst_agent(inputs, client, prompt)
            elif agent_name == "analyzer":
                data = self._run_analyzer_agent(inputs, client, prompt)
            elif agent_name == "narrative":
                data = self._run_narrative_agent(inputs, client, prompt)
            elif agent_name == "segments":
                data = self._run_segments_agent(inputs, client, prompt)
            elif agent_name == "segments_judge":
                data = self._run_segments_judge_agent(inputs, client, prompt)
            elif agent_name == "news_judge":
                data = self._run_news_judge_agent(inputs, client, prompt)
            # Query generator agents for dynamic Tavily queries
            elif agent_name == "earnings_query_generator":
                data = self._run_query_generator_agent(inputs, client, prompt, "earnings")
            elif agent_name == "news_query_generator":
                data = self._run_query_generator_agent(inputs, client, prompt, "news")
            elif agent_name == "macro_query_generator":
                data = self._run_query_generator_agent(inputs, client, prompt, "macro")
            elif agent_name == "segments_query_generator":
                data = self._run_query_generator_agent(inputs, client, prompt, "segments")
            else:
                logger.error(f"Unknown agent: {agent_name}")
                return {"error": f"Unknown agent: {agent_name}"}
            logger.debug(f"{agent_name} agent result: {json.dumps(data, indent=2)}")

            return data

        except Exception as e:
            logger.error(f"Error running agent {agent_name}: {str(e)}")
            return {"error": f"Agent execution failed: {str(e)}"}

    @staticmethod
    def _coerce_serializable(value: Any) -> Any:
        try:
            json.dumps(value)
            return value
        except TypeError:
            return str(value)

    def _extract_llm_response(self, response: Any) -> tuple[str, Dict[str, Any]]:
        content = str(getattr(response, "content", response or ""))
        metadata: Dict[str, Any] = {}
        for field in ("response_metadata", "usage_metadata", "additional_kwargs"):
            value = getattr(response, field, None)
            if value:
                metadata[field] = self._coerce_serializable(value)
        return content, metadata

    def _dump_llm_response(
        self,
        agent_name: str,
        response: Any,
        inputs: Dict[str, Any],
        metadata: Optional[Dict[str, Any]] = None,
    ) -> str:
        content, response_metadata = self._extract_llm_response(response)
        final_metadata = dict(metadata or {})
        context = inputs.get("_llm_context")
        if isinstance(context, dict) and context:
            final_metadata.setdefault("llm_context", context)
        if response_metadata:
            final_metadata["llm_response"] = response_metadata
        final_metadata.setdefault("response_length", len(content))
        dumper = get_prompt_dumper()
        dumper.dump_response(
            agent_name=agent_name,
            response=content,
            timestamp=inputs.get("_prompt_timestamp"),
            metadata=final_metadata,
        )
        return content

    def _dump_llm_error(self, agent_name: str, inputs: Dict[str, Any], error_text: str) -> None:
        dumper = get_prompt_dumper()
        dumper.dump_response(
            agent_name=agent_name,
            response={"error": error_text},
            timestamp=inputs.get("_prompt_timestamp"),
            metadata={"status": "error"},
        )
    
    def _run_narrative_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run narrative agent."""
        try:
            response = client.invoke([
                {"role": "system", "content": "You are a financial hypothesis generator that outputs strict JSON only."},
                {"role": "user", "content": prompt}
            ])

            response_content = self._dump_llm_response(
                agent_name="narrative",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            result = safe_json_loads(response_content)
            if not isinstance(result, dict):
                raise ValueError("Narrative output must be a JSON object")
            
            # Safety guard: enforce max_sentences cap on summary
            max_sentences = inputs.get("max_sentences", 10)
            sentences = [s.strip() for s in re.split(r'(?<=[.!?])\s+', result.get("summary_hypothesis", "")) if s.strip()]
            if len(sentences) > max_sentences:
                result["summary_hypothesis"] = " ".join(sentences[:max_sentences]).strip()
            
            return result
            
        except Exception as e:
            logger.error(f"Narrative agent failed: {str(e)}")
            self._dump_llm_error("narrative", inputs, str(e))
            return {"error": str(e)}

    def _run_analyst_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run analyst agent and return strict JSON object."""
        try:
            if "System:" in prompt and "User:" in prompt:
                system_part, user_part = prompt.split("User:", 1)
                system_content = system_part.replace("System:", "").strip()
                user_content = user_part.strip()
            else:
                system_content = "You are a valuation assistant. Output ONLY valid JSON."
                user_content = prompt

            response = client.invoke([
                {"role": "system", "content": system_content},
                {"role": "user", "content": user_content},
            ])

            response_content = self._dump_llm_response(
                agent_name="analyst",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            parsed = safe_json_loads(response_content)
            if not parsed:
                return {"error": "Failed to parse analyst JSON"}
            if not isinstance(parsed, dict):
                return {"error": "Analyst output must be JSON object"}
            return parsed

        except Exception as e:
            logger.error(f"Analyst agent failed: {str(e)}")
            self._dump_llm_error("analyst", inputs, str(e))
            return {"error": str(e)}

    def _run_analyzer_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run analyzer agent."""
        try:
            response = client.invoke([
                {"role": "system", "content": "You are a strict valuation analysis agent. Output ONLY valid JSON."},
                {"role": "user", "content": prompt},
            ])

            response_content = self._dump_llm_response(
                agent_name="analyzer",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            parsed = safe_json_loads(response_content)
            if not parsed:
                return {"error": "Failed to parse analyzer JSON"}
            if not isinstance(parsed, dict):
                return {"error": "Analyzer output must be JSON object"}
            return parsed

        except Exception as e:
            logger.error(f"Analyzer agent failed: {str(e)}")
            self._dump_llm_error("analyzer", inputs, str(e))
            return {"error": str(e)}

    def _run_segments_judge_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run segments judge agent."""
        try:
            response = client.invoke([
                {"role": "system", "content": "You are a strict segment-quality judge. Output ONLY valid JSON."},
                {"role": "user", "content": prompt},
            ])

            response_content = self._dump_llm_response(
                agent_name="segments_judge",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            parsed = safe_json_loads(response_content)
            if not parsed:
                return {"error": "Failed to parse segments_judge JSON"}
            if not isinstance(parsed, dict):
                return {"error": "segments_judge output must be JSON object"}
            return parsed
        except Exception as e:
            logger.error(f"Segments judge agent failed: {str(e)}")
            self._dump_llm_error("segments_judge", inputs, str(e))
            return {"error": str(e)}

    def _run_news_judge_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run news judge agent."""
        try:
            response = client.invoke([
                {"role": "system", "content": "You are a strict news-quality judge. Output ONLY valid JSON."},
                {"role": "user", "content": prompt},
            ])

            response_content = self._dump_llm_response(
                agent_name="news_judge",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            parsed = safe_json_loads(response_content)
            if not parsed:
                return {"error": "Failed to parse news_judge JSON"}
            if not isinstance(parsed, dict):
                return {"error": "news_judge output must be JSON object"}
            return parsed
        except Exception as e:
            logger.error(f"News judge agent failed: {str(e)}")
            self._dump_llm_error("news_judge", inputs, str(e))
            return {"error": str(e)}
    
    def _run_news_agent(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """Run news agent with caching."""
        try:
            ticker = inputs.get("ticker", "")
            name = inputs.get("name", "").replace(".", "")
            dcf = inputs.get("dcf", {})
            financials = inputs.get("financials", {})
            industry = inputs.get("industry", "")

            profile = financials.get("profile", {}) if isinstance(financials, dict) else {}

            # Extract company website URL for IR domain construction
            company_url = profile.get("website", "")

            from services.news_service import get_latest_earning_report, get_latest_company_news, get_latest_macro_news

            # Check cache first
            summary = fetch_news_summary_from_cache(ticker)
            if isinstance(summary, dict) and "sources" not in summary:
                summary = None
            if summary is None:
                logger.debug("Cache miss for company: %s", ticker)
                
                # Fetch both earnings and general news
                earnings_summary = fetch_earnings_summary_from_cache(ticker)
                if earnings_summary is None:
                    logger.debug("Earnings cache miss for ticker: %s", ticker)
                    earnings_news = get_latest_earning_report(name, ticker=ticker, company_url=company_url)
                    # Cache earnings for longer duration using ticker
                    save_earnings_summary_to_cache(ticker, {"earnings": earnings_news})
                else:
                    earnings_news = earnings_summary.get("earnings", "")
                
                general_news = get_latest_company_news(name, ticker=ticker, company_url=company_url)

                # Fetch macro news with caching
                country = infer_country_for_macro(
                    country=profile.get("country"),
                    currency=financials.get("currency") if isinstance(financials, dict) else None,
                    ticker=ticker,
                )
                macro_news = fetch_macro_news_from_cache(country)
                if macro_news is None:
                    logger.debug("Macro news cache miss for country: %s", country)
                    macro_news = get_latest_macro_news(country)
                    # Cache macro news for medium duration
                    save_macro_news_to_cache(country, macro_news)
                else:
                    logger.debug("Macro news cache hit for country: %s", country)

                judge_result = self.run_agent(
                    "news_judge",
                    {
                        "company": name,
                        "ticker": ticker,
                        "industry": industry,
                        "earnings_news": earnings_news,
                        "general_news": general_news,
                        "macro_news": macro_news,
                    },
                )
                earnings_news, general_news, macro_news = self._apply_news_judge_result(
                    earnings_news=earnings_news,
                    general_news=general_news,
                    macro_news=macro_news,
                    judge_result=judge_result,
                )
                
                # Combine earnings, general news, and macro news
                combined_news = f"EARNINGS REPORTS:\n{earnings_news}\n\nGENERAL NEWS:\n{general_news}\n\nMACROECONOMIC NEWS:\n{macro_news}"
                
                # Use narrative agent to process combined news
                narrative_inputs = {
                    "company": name,
                    "news_content": combined_news,
                    "macro_news": macro_news,
                    "industry": industry,
                    "max_sentences": 10
                }
                summary = self.run_agent("narrative", narrative_inputs)
                if isinstance(summary, dict):
                    summary["sources"] = self._build_news_sources(
                        earnings_news=earnings_news,
                        general_news=general_news,
                        macro_news=macro_news,
                    )
                save_news_summary_to_cache(ticker, summary)
            elif isinstance(summary, dict):
                summary["sources"] = summary.get("sources") or []
            
            # Return only the narrative summary to avoid redundant duplication of context
            return summary
            
        except Exception as e:
            logger.error(f"News agent failed: {str(e)}")
            return {"error": str(e)}

    @staticmethod
    def _apply_news_judge_result(
        earnings_news: Any,
        general_news: Any,
        macro_news: Any,
        judge_result: Dict[str, Any],
    ) -> tuple[str, str, str]:
        """
        Apply news-judge output with conservative fallback to raw snippets.
        """
        raw_earnings = earnings_news if isinstance(earnings_news, str) else str(earnings_news or "")
        raw_general = general_news if isinstance(general_news, str) else str(general_news or "")
        raw_macro = macro_news if isinstance(macro_news, str) else str(macro_news or "")

        if not isinstance(judge_result, dict) or judge_result.get("error"):
            return raw_earnings, raw_general, raw_macro

        cleaned = judge_result.get("cleaned_news")
        if not isinstance(cleaned, dict):
            return raw_earnings, raw_general, raw_macro

        judged_earnings = cleaned.get("earnings")
        judged_general = cleaned.get("company_news")
        judged_macro = cleaned.get("macro")

        final_earnings = judged_earnings.strip() if isinstance(judged_earnings, str) and judged_earnings.strip() else raw_earnings
        final_general = judged_general.strip() if isinstance(judged_general, str) and judged_general.strip() else raw_general
        final_macro = judged_macro.strip() if isinstance(judged_macro, str) and judged_macro.strip() else raw_macro

        return final_earnings, final_general, final_macro

    def _build_news_sources(self, earnings_news: Any, general_news: Any, macro_news: Any) -> List[Dict[str, str]]:
        sources: List[Dict[str, str]] = []
        seen_urls = set()

        for category, text in (
            ("earnings", earnings_news),
            ("company_news", general_news),
            ("macro", macro_news),
        ):
            for item in self._extract_sources_from_news_text(text, category):
                url = item.get("url")
                if not url or url in seen_urls:
                    continue
                seen_urls.add(url)
                sources.append(item)
        return sources

    def _extract_sources_from_news_text(self, text: Any, category: str) -> List[Dict[str, str]]:
        if not isinstance(text, str) or not text.strip():
            return []

        results: List[Dict[str, str]] = []
        markdown_links = re.findall(r"-\s*([^\n]+?)\n(?:.*?\n)?\[Read more\]\((https?://[^)\s]+)\)", text, flags=re.IGNORECASE)
        for raw_title, raw_url in markdown_links:
            title = raw_title.strip()
            url = raw_url.strip()
            domain = urlparse(url).netloc
            results.append({
                "title": title,
                "url": url,
                "category": category,
                "source": domain,
            })

        if results:
            return results

        plain_urls = re.findall(r"(https?://[^\s)\]]+)", text)
        for raw_url in plain_urls:
            url = raw_url.strip()
            if not url:
                continue
            domain = urlparse(url).netloc
            results.append({
                "title": domain or "Source",
                "url": url,
                "category": category,
                "source": domain,
            })

        return results
    

    
    def _run_segments_agent(self, inputs: Dict[str, Any], client: Any, prompt: str) -> Dict[str, Any]:
        """Run segments agent with caching."""
        try:
            from services.news_service import get_company_segments
            from domain.processing.helpers import postprocess_segments

            company = inputs.get("name", "")
            current_industry = inputs.get("industry", "")
            description = inputs.get("description", "")
            
            # Check cache first
            cached = get_cached_segments(company, current_industry)
            if cached:
                logger.debug(f"Cache hit for {company} ({current_industry})")
                return cached
            
            # Get segment data
            segment_data = get_company_segments(company)
            if isinstance(segment_data, str):
                logger.warning(f"No segment data found for {company}: {segment_data}")
                return {"segments": []}
            
            # Use the segment data in prompt (use non-JSON placeholder to avoid conflicts)
            if isinstance(segment_data, dict):
                segment_text = json.dumps(segment_data, indent=2)
            else:
                segment_text = str(segment_data)
            prompt_with_data = prompt.replace("<<SEGMENT_DATA>>", segment_text)
            prompt_with_data = prompt_with_data.replace("<<DESCRIPTION>>", description)

            if not inputs.get("_prompt_timestamp"):
                dumper = get_prompt_dumper()
                inputs["_prompt_timestamp"] = dumper.dump_prompt(
                    agent_name="segments",
                    prompt=prompt_with_data,
                    inputs=inputs,
                    metadata={"rendered_prompt": True},
                )

            response = client.invoke([
                {"role": "system", "content": "You are a strict financial JSON extraction agent."},
                {"role": "user", "content": prompt_with_data}
            ])

            response_content = self._dump_llm_response(
                agent_name="segments",
                response=response,
                inputs=inputs,
                metadata={"status": "success"},
            )

            data = safe_json_loads(response_content)
            if not isinstance(data, dict):
                logger.warning("LLM output not valid JSON.")
                return {"segments": []}
            
            # Validate and cache
            validated = postprocess_segments(data, current_industry, industry_mapping)

            # Optional LLM judge pass to handle noisy segment extraction edge cases.
            if self._should_run_segments_judge(validated):
                judge_result = self.run_agent(
                    "segments_judge",
                    {
                        "name": company,
                        "industry": current_industry,
                        "description": description,
                        "segments": validated.get("segments", []),
                        "raw_segment_data": segment_data if isinstance(segment_data, dict) else {},
                    },
                )
                validated = self._apply_segments_judge_result(
                    validated=validated,
                    judge_result=judge_result,
                    current_industry=current_industry,
                )
            
            set_cached_segments(company, current_industry, validated)
            
            return validated
            
        except Exception as e:
            logger.error(f"Segments agent failed: {str(e)}")
            self._dump_llm_error("segments", inputs, str(e))
            return {"segments": []}

    @staticmethod
    def _should_run_segments_judge(validated: Dict[str, Any]) -> bool:
        segments = validated.get("segments") if isinstance(validated, dict) else None
        if not isinstance(segments, list) or not segments:
            return True

        if len(segments) > 6:
            return True

        seen_sectors = set()
        total_share = 0.0
        low_conf_count = 0

        for seg in segments:
            if not isinstance(seg, dict):
                return True
            sector = str(seg.get("sector", "")).strip()
            if not sector:
                return True
            if sector in seen_sectors:
                return True
            seen_sectors.add(sector)

            try:
                total_share += float(seg.get("revenue_share") or 0.0)
            except (TypeError, ValueError):
                return True

            try:
                if float(seg.get("mapping_score") or 0.0) < 0.55:
                    low_conf_count += 1
            except (TypeError, ValueError):
                low_conf_count += 1

        if abs(total_share - 1.0) > 0.05:
            return True
        return low_conf_count > 0

    def _apply_segments_judge_result(
        self,
        validated: Dict[str, Any],
        judge_result: Dict[str, Any],
        current_industry: str,
    ) -> Dict[str, Any]:
        if not isinstance(judge_result, dict) or judge_result.get("error"):
            return validated

        corrected = judge_result.get("corrected_segments")
        if not isinstance(corrected, list) or not corrected:
            return validated

        from domain.processing.helpers import postprocess_segments

        corrected_payload = postprocess_segments(
            {"segments": corrected},
            current_industry,
            industry_mapping,
        )
        if isinstance(corrected_payload, dict) and corrected_payload.get("segments"):
            return corrected_payload
        return validated
    
    def _run_query_generator_agent(
        self, 
        inputs: Dict[str, Any], 
        client: Any, 
        prompt: str,
        query_type: str
    ) -> Dict[str, Any]:
        """
        Run query generator agent to create optimized Tavily search queries.
        
        This is a unified method for all 4 query generator agents:
        - earnings_query_generator
        - news_query_generator
        - macro_query_generator
        - segments_query_generator
        
        Args:
            inputs: Input data including company context
            client: LLM client
            prompt: Generated prompt
            query_type: Type of query (earnings, news, macro, segments)
            
        Returns:
            Dict with primary_query or error
        """
        try:
            response = client.invoke([
                {"role": "system", "content": "You are a search query optimization expert. Output ONLY valid JSON."},
                {"role": "user", "content": prompt}
            ])

            response_content = self._dump_llm_response(
                agent_name=f"{query_type}_query_generator",
                response=response,
                inputs=inputs,
                metadata={
                    "status": "success",
                    "query_type": query_type,
                },
            )

            # Parse JSON response
            parsed_json = safe_json_loads(response_content)
            if not parsed_json:
                logger.warning(f"{query_type}_query_generator: Failed to parse JSON, returning error")
                return {"error": "Failed to parse query generator JSON"}
            
            # Validate that primary_query exists
            if "primary_query" not in parsed_json:
                logger.warning(f"{query_type}_query_generator: Missing primary_query in response")
                return {"error": "Missing primary_query in response"}
            
            # Validate query length (Tavily limit is 400 chars)
            query = parsed_json.get("primary_query", "")
            if len(query) > 400:
                logger.warning(f"{query_type}_query_generator: Query too long ({len(query)} chars), truncating")
                parsed_json["primary_query"] = query[:397] + "..."
            
            logger.debug(f"{query_type}_query_generator: Generated query: {parsed_json.get('primary_query', '')[:100]}...")
            
            return parsed_json
            
        except Exception as e:
            logger.error(f"{query_type}_query_generator agent failed: {str(e)}")
            self._dump_llm_error(f"{query_type}_query_generator", inputs, str(e))
            return {"error": str(e)}
    
# Global orchestrator instance
orchestrator = AgentOrchestrator()

# Convenience function
def run_agent(agent_name: str, inputs: Dict[str, Any], provider: str = None) -> Dict[str, Any]:
    """Run an agent with the global orchestrator."""
    return orchestrator.run_agent(agent_name, inputs, provider)
