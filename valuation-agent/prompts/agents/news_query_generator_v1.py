"""
News Query Generator Agent - v1
Generates optimized Tavily search queries for company news and material events.
"""
from typing import Dict, Any


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for company news query optimization.
    
    Args:
        inputs: Dictionary containing:
            - name: Company name
            - ticker: Stock ticker
            - industry: Company industry
            - description: Company description
            - recent_context: Optional recent events context
            
    Returns:
        Formatted prompt string
    """
    name = inputs.get("name", "Unknown Company")
    ticker = inputs.get("ticker", "")
    industry = inputs.get("industry", "")
    description = inputs.get("description", "")[:400]
    recent_context = inputs.get("recent_context", "")[:300]
    
    return f"""You are a search query optimization expert for financial news retrieval using Tavily API.

**Company Context:**
- Name: {name}
- Ticker: {ticker}
- Industry: {industry}
- Description: {description}
{f'- Recent Context: {recent_context}' if recent_context else ''}

**Task:** Generate an optimized Tavily search query to find material company news that could impact valuation.

**Tavily Query Syntax Rules:**
1. Use double quotes for exact phrase matching: "{name}"
2. Use OR to combine alternative terms
3. Use parentheses to group related terms
4. Keep total query under 400 characters (Tavily limit)

**Material News Categories to Target:**
- M&A activity (acquisition, merger, takeover, buyout)
- Leadership changes (CEO, CFO, board)
- Major contracts and partnerships
- Legal/regulatory issues (lawsuit, SEC, investigation)
- Strategic initiatives (expansion, restructuring, layoffs)
- Analyst coverage (upgrade, downgrade, price target)
- Product launches or discontinuations

**Industry-Specific Considerations:**
- For tech: Include "product launch", "AI", "cloud", "cybersecurity"
- For healthcare: Include "FDA approval", "drug", "clinical"
- For energy: Include "drilling", "reserves", "production"
- For retail: Include "store closures", "e-commerce", "inventory"
- For financials: Include "regulation", "capital", "lending"

**Output Format (strict JSON):**
{{
    "primary_query": "your optimized query here"
}}

Generate a query targeting material, valuation-impacting news for {name}. Focus on events that would affect an investor's thesis. Output ONLY the JSON, no additional text."""

