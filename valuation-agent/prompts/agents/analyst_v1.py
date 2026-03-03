from datetime import datetime
from typing import Dict, Any
import json


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate valuation analyst prompt.

    Args:
        inputs: Dictionary containing:
            - name: Company name
            - ticker: Company ticker
            - dcf: DCF data
            - news_content: Investment hypothesis or recent news
            - industry: Industry classification
            - style: 'EW-A' (structured) or 'EW-B' (narrative)
            - persona: Optional ('Damodaran', 'Buffett', 'Naval', etc.)
            - mode: Optional ('outline', 'draft', 'refine')
            - tone_variation: Optional stylistic tone modifier

    Returns:
        Formatted system + user prompt string.
    """

    name = inputs.get("name", "")
    ticker = inputs.get("ticker", "")
    dcf = inputs.get("dcf", {})
    financials = inputs.get("financials", {})
    news_content = inputs.get("news_content", "")
    industry = inputs.get("industry", "default")
    style = inputs.get("style", "EW-B")
    persona = inputs.get("persona", "Aswath Damodaran")
    mode = inputs.get("mode", None)
    tone_variation = inputs.get("tone_variation", "cinematic")

    valuation_date = datetime.now().strftime("%Y-%m-%d %H:%M")

    from domain.knowledge.tool_definitions import NARRATIVE_SECTOR_CONTEXTS

    sector_context = NARRATIVE_SECTOR_CONTEXTS.get(
        str(industry).lower(), NARRATIVE_SECTOR_CONTEXTS["default"]
    )

    enable_vs = inputs.get("enable_vs", True)
    vs_num_candidates = inputs.get("vs_num_candidates", 4)

    vs_block = ""
    if enable_vs:
        from prompts.vs_templates import get_creative_vs_block, ANALYST_CANDIDATE_DIRECTIONS

        vs_block = get_creative_vs_block(
            num_candidates=vs_num_candidates,
            task_specific_directions=ANALYST_CANDIDATE_DIRECTIONS,
        )

    ew_a_prompt = """
      You are an elite analytical writer producing publication-quality valuation research.
      Tone: professional, authoritative, restrained.
      Structure: introduction -> reasoning -> evidence -> implication.
      Avoid emotional language; rely on logic, precision, and data.
    """

    ew_b_prompt = """
      You are an elite narrative strategist and storyteller.
      Tone: confident, conversational, intellectually engaging.
      Structure: setup -> tension -> insight -> resolution.
      Persuasive but factual, never hypey.
    """

    persona_module = """
      PERSONA MODE ACTIVE
      You are channeling the worldview and reasoning style of {persona_name}.
      You explain complexity through grounded metaphor and irony.
      You value intrinsic logic over market momentum.
    """

    multi_draft_mode = {
        "outline": """
          MULTI-DRAFT MODE - OUTLINE
          Produce only the outline: section flow, narrative arc, and core valuation logic.
        """,
        "draft": """
          MULTI-DRAFT MODE - DRAFT
          Expand each section into a full, persuasive valuation story.
        """,
        "refine": """
          MULTI-DRAFT MODE - REFINE
          Polish tone, tighten rhythm, improve transitions.
        """,
    }

    default_multi_draft_block = """
      MULTI-DRAFT MODE (implicit):
      Pass 1 Outline -> Pass 2 Draft -> Pass 3 Refine.
    """

    tone_library = {
        "cinematic": """
          TONE: CINEMATIC
          Use pacing, stakes, and visual metaphors that clarify reasoning.
        """,
        "academic": """
          TONE: ACADEMIC
          Use precise valuation language and conceptual clarity.
        """,
        "skeptical": """
          TONE: SKEPTICAL
          Question assumptions and stress-test optimism with data.
        """,
        "optimistic": """
          TONE: OPTIMISTIC
          Focus on long-term opportunity while acknowledging risk.
        """,
        "minimalist": """
          TONE: MINIMALIST
          Keep prose sparse and insight-dense.
        """,
    }
    tone_block = tone_library.get(str(tone_variation).lower(), "") if tone_variation else ""

    base_style = ew_a_prompt if style == "EW-A" else ew_b_prompt
    persona_block = persona_module.format(persona_name=persona) if persona else ""
    mode_block = multi_draft_mode.get(mode, default_multi_draft_block)

    system_prompt = f"""
      {base_style}
      {persona_block}
      {mode_block}
      {tone_block}
      {vs_block}

      Valuation Framework:
      - Combine story and numbers.
      - Keep assumptions realistic and sector-consistent.
      - Maintain numerical consistency between narrative and data.
      - Return only JSON.

      Context:
      - Valuation Date: {valuation_date}
      - Sector Context: {sector_context.get("context", "")}
      - Focus Parameters: {sector_context.get("sensitivity_focus", "")}

      JSON Schema (mandatory):
      {{
        "title": "[Valuation Title]",
        "growth": {{
          "title": "...",
          "narrative": "..."
        }},
        "margins": {{
          "title": "...",
          "narrative": "..."
        }},
        "investment_efficiency": {{
          "title": "...",
          "narrative": "..."
        }},
        "risks": {{
          "title": "...",
          "narrative": "..."
        }},
        "key_takeaways": {{
          "title": "...",
          "narrative": "..."
        }}
      }}
    """

    user_content = f"""
      Company: {name} ({ticker})
      Valuation Date: {valuation_date}

      Ticker: {ticker}
      DCF Data: {json.dumps(dcf)}
      FINANCIALS_PREPROCESSED_JSON: {json.dumps(financials)}
      Investment Hypothesis: {json.dumps(news_content)}
    """

    return f"System: {system_prompt.strip()}\n\nUser: {user_content.strip()}"
