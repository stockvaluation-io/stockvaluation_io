"""
Prompt template for analyzer agent.
"""
import json
from typing import Any, Dict


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for analyzer agent.

    Inputs expected:
    - ticker
    - name
    - industry
    - dcf
    - segments
    - news
    """
    ticker = inputs.get("ticker", "")
    name = inputs.get("name", "")
    industry = inputs.get("industry", "")
    dcf = inputs.get("dcf", {}) or {}
    financials = inputs.get("financials", {}) or {}
    segments = inputs.get("segments", {}) or {}
    news = inputs.get("news", {}) or {}
    skills = inputs.get("skills", {}) or {}
    growth_skill = skills.get("growth_skill", {}) if isinstance(skills, dict) else {}

    return f"""
You are a valuation analyzer that proposes only bounded business-assumption changes for a downstream Java DCF engine.

Company:
- ticker: {ticker}
- name: {name}
- industry: {industry}

Rules:
1. You may propose adjustments only for these parameters:
   - revenue_cagr (unit: percent)
   - operating_margin (unit: percent)
   - sales_to_capital (unit: x)
2. Keep recommendations conservative and explain rationale with evidence from news + sector context.
3. If evidence is weak, return zero instructions.
4. Sector-level adjustments are allowed only when SKILLS_JSON.has_segment_skills=true and sector names exactly match listed segment_skills sectors.
5. When GROWTH_SKILL_JSON is provided (has_growth_skill=true), cross-check your proposed revenue_cagr against the historical growth bands (p25/p50/p75). If your proposal exceeds the p75, provide explicit rationale. If it falls below p25, explain the bearish thesis.
6. Output strict JSON only.
7. ENFORCEMENT: When growth_skill is available, your proposed revenue_cagr MUST stay within [p25 - 5pp, p75 + 5pp] unless exceptional evidence justifies deviation. Include the growth_anchor_reference in your response.

Input data:
DCF_PREPROCESSED_JSON:
{json.dumps(dcf, ensure_ascii=True)}

FINANCIALS_PREPROCESSED_JSON:
{json.dumps(financials, ensure_ascii=True)}

SEGMENTS_JSON:
{json.dumps(segments, ensure_ascii=True)}

NEWS_JSON:
{json.dumps(news, ensure_ascii=True)}

SKILLS_JSON:
{json.dumps(skills, ensure_ascii=True)}

GROWTH_SKILL_JSON:
{json.dumps(growth_skill, ensure_ascii=True)}

Return JSON in this schema:
{{
  "dcf_analysis": {{
    "assumption_policy": "Analyzer adjusts only revenue_cagr, operating_margin, and sales_to_capital. Java DCF remains source of truth for math.",
    "baseline_assumptions": {{
      "revenue_cagr": number|null,
      "operating_margin": number|null,
      "sales_to_capital": number|null
    }},
    "proposed_assumptions": {{
      "revenue_cagr": number (optional),
      "operating_margin": number (optional),
      "sales_to_capital": number (optional)
    }},
    "dcf_adjustment_instructions": [
      {{
        "parameter": "revenue_cagr|operating_margin|sales_to_capital",
        "new_value": number,
        "unit": "percent|x",
        "rationale": "string"
      }}
    ],
    "sector_adjustment_instructions": [
      {{
        "sector": "exact sector from SKILLS_JSON.segment_skills",
        "parameter": "revenue_growth|operating_margin|sales_to_capital",
        "value": number,
        "unit": "percent|x",
        "adjustment_type": "absolute|relative_multiplier|relative_additive",
        "timeframe": "years_1_to_5|years_6_to_10|both",
        "rationale": "string"
      }}
    ],
    "adjusted_valuation": "string"
  }},
  "recommendations": {{
    "confidence_level": "low|medium|high",
    "focus_variables": ["revenue_cagr", "operating_margin", "sales_to_capital"],
    "summary": "string"
  }},
  "analyzer_metadata": {{
    "version": "analyzer_v1",
    "industry": "string",
    "dominant_sector": "string",
    "dominant_industry": "string",
    "segments_count": number,
    "tone": "optimistic|cautious|neutral",
    "generated_instruction_count": number,
    "baseline_metrics_available": ["revenue_cagr", "operating_margin", "sales_to_capital"]
  }},
  "growth_anchor_reference": {{
    "entity": "string (from GROWTH_SKILL_JSON.entity)",
    "region": "string",
    "fundamental_growth": number|null,
    "historical_p25": number|null,
    "historical_p50": number|null,
    "historical_p75": number|null,
    "regime_tag": "string",
    "confidence_score": number|null,
    "deviation_rationale": "string (explain if proposed growth deviates from p25-p75 band)"
  }}
}}
"""
