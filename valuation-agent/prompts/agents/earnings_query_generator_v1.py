"""
Earnings Query Generator Agent - v1
Generates optimized Tavily search queries for earnings reports and transcripts.
"""
from typing import Dict, Any


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for earnings query optimization.
    
    Args:
        inputs: Dictionary containing:
            - name: Company name
            - ticker: Stock ticker
            - industry: Company industry
            - description: Company description
            
    Returns:
        Formatted prompt string
    """
    name = inputs.get("name", "Unknown Company")
    ticker = inputs.get("ticker", "")
    industry = inputs.get("industry", "")
    description = inputs.get("description", "")[:500]  # Truncate long descriptions
    
    return f"""You are a search query optimization expert for financial data retrieval using Tavily API.

**Company Context:**
- Name: {name}
- Ticker: {ticker}
- Industry: {industry}
- Description: {description}

**Task:** Generate an optimized Tavily search query to find earnings reports, earnings call transcripts, and quarterly guidance for this company.

**Tavily Query Syntax Rules:**
1. Use double quotes for exact phrase matching: "{name}"
2. Use OR to combine alternative terms: (earnings OR results)
3. Use parentheses to group related terms
4. Keep total query under 400 characters (Tavily limit)

**Industry-Specific Considerations:**
- For tech/SaaS: Include terms like "ARR", "bookings", "billings", "net retention"
- For retail: Include "same-store sales", "comp sales", "SSS"
- For biotech/pharma: Include "pipeline", "FDA", "clinical trials"
- For banks/financials: Include "NII", "NIM", "credit quality", "provisions"
- For industrials: Include "backlog", "book-to-bill", "orders"

**Required Focus Areas:**
- Earnings call transcripts
- Quarterly/annual results
- Forward guidance
- SEC filings (10-K, 10-Q)
- Analyst expectations vs actual

**Output Format (strict JSON):**
{{
    "primary_query": "your optimized query here"
}}

Generate a precise, industry-aware query that will return the most relevant earnings content for {name}. Output ONLY the JSON, no additional text."""

