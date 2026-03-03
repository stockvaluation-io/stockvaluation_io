"""
Prompt template for segments agent.
"""
from typing import Dict, Any
import json

def get_prompt(inputs: Dict[str, Any]) -> str:
    """
    Generate prompt for segments agent.
    
    Args:
        inputs: Dictionary containing:
            - name: Company name
            - industry: Industry classification
            
    Returns:
        Formatted prompt string
    """
    company = inputs.get("name", "")
    current_industry = inputs.get("industry", "")
    
    prompt = f"""
Extract reportable business segments for {company} from the data provided below.

INSTRUCTIONS:
1. Identify segment names from the data
2. Calculate revenue_share (must sum to 1.0)
3. Estimate operating_margin if available
4. Assign sector using kebab-case naming

REQUIRED JSON STRUCTURE:
segments: array of objects, each with:
- name: string (segment name from data)
- revenue_share: decimal (0 to 1, must sum to 1.0)
- operating_margin: decimal (0 to 1, e.g. 0.25 for 25%)
- sector: string in kebab-case

SECTOR NAMING EXAMPLES:
Tech: software-application, information-technology-services, semiconductors
Healthcare: biotechnology, drug-manufacturers-general, medical-devices
Energy: oil-gas-e-p, oil-gas-integrated, utilities-renewable
Auto: auto-manufacturers, auto-parts
Finance: banks-regional, asset-management
Consumer: restaurants, specialty-retail, consumer-electronics

RULES:
- Extract only clearly mentioned segments from the data
- If no clear segments, return one segment for primary business
- Sector names must be kebab-case and industry-standard
- Revenue shares must sum to exactly 1.0

DATA TO ANALYZE:
Segment Data: <<SEGMENT_DATA>>
Description: <<DESCRIPTION>>

Return only valid JSON with "segments" array containing the extracted segment objects.
    """

    return prompt
