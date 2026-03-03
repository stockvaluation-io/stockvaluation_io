"""
Macro Query Generator Agent - v1
Generates optimized Tavily search queries for macroeconomic news by country.
"""
from typing import Dict, Any


def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for macroeconomic news query optimization.
    
    Args:
        inputs: Dictionary containing:
            - country: Country name
            - industry: Industry exposure (optional)
            - economic_sensitivities: Key economic factors (optional)
            
    Returns:
        Formatted prompt string
    """
    country = inputs.get("country", "United States")
    industry = inputs.get("industry", "")
    sensitivities = inputs.get("economic_sensitivities", "")
    
    return f"""You are a search query optimization expert for macroeconomic news retrieval using Tavily API.

**Context:**
- Country: {country}
{f'- Industry Focus: {industry}' if industry else ''}
{f'- Economic Sensitivities: {sensitivities}' if sensitivities else ''}

**Task:** Generate an optimized Tavily search query to find recent macroeconomic news and data for {country}.

**Tavily Query Syntax Rules:**
1. Use double quotes for exact phrase matching: "{country}"
2. Use OR to combine alternative terms
3. Use parentheses to group related terms
4. Keep total query under 400 characters (Tavily limit)

**Key Economic Indicators to Target:**
- GDP growth and economic outlook
- Inflation (CPI, PPI, core inflation)
- Interest rates and central bank policy
- Unemployment and labor market
- Trade balance and currency
- Consumer confidence and spending
- Manufacturing/services PMI

**Country-Specific Considerations:**
- US: Federal Reserve, Fed funds rate, Treasury yields
- EU/Eurozone: ECB, euro, fiscal policy
- UK: Bank of England, Brexit impacts
- China: PBOC, RMB, trade tensions
- Japan: BOJ, yen, yield curve control
- India: RBI, rupee, fiscal deficit
- Emerging markets: Capital flows, sovereign risk

**Industry Sensitivity (if provided):**
{f'Focus on indicators most relevant to {industry}:' if industry else ''}
- Tech: Interest rates, capex spending, talent costs
- Consumer: Inflation, consumer confidence, retail sales
- Industrials: Manufacturing PMI, infrastructure spending
- Financials: Interest rates, credit conditions
- Energy: Oil prices, energy policy, carbon

**Output Format (strict JSON):**
{{
    "primary_query": "your optimized query here"
}}

Generate a query that captures the most relevant macroeconomic context for {country}. Output ONLY the JSON, no additional text."""

