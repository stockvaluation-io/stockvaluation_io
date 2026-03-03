"""
Prompt template for segments judge agent.
"""
import json
from typing import Any, Dict


def get_prompt(inputs: Dict[str, Any]) -> str:
    company = inputs.get("name", "")
    industry = inputs.get("industry", "")
    description = inputs.get("description", "")
    segments = inputs.get("segments", []) or []
    raw_segment_data = inputs.get("raw_segment_data", {}) or {}

    return f"""
You are a quality-control judge for company segment decomposition.

Company: {company}
Industry hint: {industry}
Business description: {description}

Task:
1) Review candidate mapped segments for plausibility, completeness, and consistency.
2) Detect strange or noisy mappings (irrelevant sectors, duplicated nonsense, impossible shares).
3) If needed, return corrected segments using ONLY this schema.

Candidate mapped segments:
{json.dumps(segments, ensure_ascii=True)}

Raw segment evidence (from search pipeline):
{json.dumps(raw_segment_data, ensure_ascii=True)}

Output strict JSON:
{{
  "is_valid": true/false,
  "issues": ["short issue text"],
  "corrected_segments": [
    {{
      "name": "string",
      "revenue_share": 0.0,
      "operating_margin": 0.0,
      "sector": "kebab-case sector"
    }}
  ],
  "reasoning_summary": "short explanation"
}}

Rules:
- If candidate segments are already sound, set is_valid=true and corrected_segments=[].
- If correcting, revenue_share must sum to ~1.0 and sectors must be realistic for the company.
- Keep corrections conservative, based only on provided evidence.
- Return JSON only.
"""
