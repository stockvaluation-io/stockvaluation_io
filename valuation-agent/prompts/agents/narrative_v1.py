"""
Prompt template for narrative agent.
"""
from typing import Dict, Any

def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for narrative agent.
    
    Args:
        inputs: Dictionary containing:
            - company: Company name
            - news_content: News content
            - macro_news: Macro news content
            - industry: Industry classification
            - max_sentences: Maximum sentences for summary
            
    Returns:
        Formatted prompt string
    """
    company = inputs.get("company", "")
    news_content = inputs.get("news_content", "")
    macro_news = inputs.get("macro_news", "")
    industry = inputs.get("industry", "default")
    max_sentences = inputs.get("max_sentences", 10)
    
    # Import sector context from tools
    from domain.knowledge.tool_definitions import SECTOR_CONTEXT
    sector_context = SECTOR_CONTEXT.get(industry, SECTOR_CONTEXT["default"])
    
    # --- Verbalized Sampling (VS) Support (Simplified for Analytical Task) ---
    enable_vs = inputs.get("enable_vs", False)
    vs_num_candidates = inputs.get("vs_num_candidates", 3)
    
    vs_block = ""
    if enable_vs:
        from prompts.vs_templates import get_analytical_vs_block, NARRATIVE_TASK_FOCUS
        vs_block = get_analytical_vs_block(
            num_candidates=vs_num_candidates,
            task_focus=NARRATIVE_TASK_FOCUS
        )
    
    return f"""
      You are a financial storytelling assistant who writes in the analytical style of Aswath Damodaran. 
      Based only on the following Source text and Macro news, extract the company's investment narrative in four categories:

      {vs_block}

      Sector Context:
      {sector_context}

      1. Growth – revenue drivers, scale advantages, unit economics, market expansion, pricing leverage
      2. Operating Margins – profitability trends, pricing power, cost structure, scalability, competitive positioning
      3. Capital Efficiency – balance sheet strength, reinvestment discipline, return on capital, ROIC, asset utilization
      4. Risk – operational, regulatory, competitive, macro risks, cyclicality, macro shocks, competitive or regulatory headwinds

      Then provide:
      - A concise summary hypothesis (max {max_sentences} sentences)
      - A tone classification: "optimistic", "cautious", or "neutral"

      Source text:
      {news_content}

      Macro News:
      {macro_news}

      Return ONLY valid JSON (STRICT) with this structure:
      {{
        "company": "{company}",
        "valuation_drivers": {{
          "growth": "...",
          "operating_margins": "...",
          "capital_efficiency": "...",
          "risk": "..."
        }},
        "summary_hypothesis": "...",
        "tone": "optimistic | cautious | neutral"
      }}
    """
