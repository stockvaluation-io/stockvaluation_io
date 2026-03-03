"""
Segments Query Generator Agent - v1
Generates optimized Tavily search queries for company business segment information.
"""
from typing import Dict, Any


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for business segments query optimization.
    
    Args:
        inputs: Dictionary containing:
            - name: Company name
            - ticker: Stock ticker (optional)
            - industry: Current industry classification
            - description: Company description
            
    Returns:
        Formatted prompt string
    """
    name = inputs.get("name", "Unknown Company")
    ticker = inputs.get("ticker", "")
    industry = inputs.get("industry", "")
    description = inputs.get("description", "")[:500]
    
    return f"""You are a search query optimization expert for extracting business segment information using Tavily API.

**Company Context:**
- Name: {name}
{f'- Ticker: {ticker}' if ticker else ''}
- Industry: {industry}
- Description: {description}

**Task:** Generate an optimized Tavily search query to find detailed business segment disclosures for this company.

**Tavily Query Syntax Rules:**
1. Use double quotes for exact phrase matching: "{name}"
2. Use AND to require terms: "{name}" AND "segment"
3. Use OR to combine alternative terms
4. Use parentheses to group related terms
5. Keep total query under 400 characters (Tavily limit)

**Target Information Sources:**
- SEC filings (10-K, 10-Q) - "Note on Segment Reporting"
- Annual reports - segment breakdown tables
- Investor presentations - business unit summaries
- Official company disclosures

**Key Segment Disclosure Terms:**
- "business segments" / "reportable segments" / "operating segments"
- "segment revenue" / "revenue by segment" / "segment information"
- "lines of business" / "business units" / "operating units"
- "segment reporting" / "segment results" / "segment performance"
- "principal activities" / "geographic segments"

**Company Type Considerations:**
- Diversified conglomerate: Focus on distinct business unit breakdown
- Single-segment company: Focus on product/service line splits
- Multi-geography: Include geographic segment terms
- Holding company: Focus on subsidiary breakdowns

**Output Format (strict JSON):**
{{
    "primary_query": "your optimized query here"
}}

Generate a query that will find official segment disclosures with revenue shares and operating details for {name}. Target SEC filings and investor relations materials. Output ONLY the JSON, no additional text."""

