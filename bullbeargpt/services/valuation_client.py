"""
Valuation Client for BullBearGPT.
Fetches valuation data from valuation-agent to provide chat context.
"""
import os
import logging
from typing import Optional, Dict, Any

import requests
from config import Config

logger = logging.getLogger(__name__)


class ValuationClient:
    """
    HTTP client to fetch valuation data from valuation-agent.
    
    Used to load DCF context when user starts a chat session with a valuation_id.
    """
    
    def __init__(self, valuation_agent_url: Optional[str] = None):
        """
        Initialize the client.
        
        Args:
            valuation_agent_url: Base URL for valuation-agent API.
        """
        self.valuation_agent_url = (
            valuation_agent_url
            or os.getenv('VALUATION_AGENT_URL')
            or getattr(Config, 'VALUATION_AGENT_URL', 'http://valuation-agent:5001')
        )
    
    def get_valuation_by_id(self, valuation_id: str, auth_header: Optional[str] = None) -> Optional[Dict[str, Any]]:
        """
        Fetch valuation data by ID from valuation-agent.
        
        Args:
            valuation_id: UUID of the valuation record
            
        Returns:
            Valuation dict with valuation_data, or None if not found
        """
        if not valuation_id:
            return None
        
        try:
            url = f"{self.valuation_agent_url}/api-s/valuation/{valuation_id}"
            logger.info(f"Fetching valuation from {url}")
            
            headers = {}
            if auth_header:
                headers["Authorization"] = auth_header

            response = requests.get(url, timeout=10, headers=headers or None)
            
            if response.status_code == 404:
                logger.warning(f"Valuation {valuation_id} not found")
                return None
            
            response.raise_for_status()
            data = response.json()
            
            logger.info(f"Loaded valuation for {data.get('ticker')} (ID: {valuation_id})")
            return data
            
        except requests.exceptions.Timeout:
            logger.error(f"Timeout fetching valuation {valuation_id}")
            return None
        except requests.exceptions.RequestException as e:
            logger.error(f"Error fetching valuation {valuation_id}: {e}")
            return None
        except Exception as e:
            logger.error(f"Unexpected error fetching valuation: {e}")
            return None
    
    def format_for_system_prompt(self, valuation: Dict[str, Any]) -> Dict[str, Any]:
        """
        Extract and format valuation fields for use in chat system prompt.
        
        Args:
            valuation: Full valuation dict from get_valuation_by_id
            
        Returns:
            Dict with formatted fields for prompt building
        """
        if not valuation:
            return {}
        
        valuation_data = valuation.get('valuation_data', {})
        
        # Extract key metrics
        merged_result = valuation_data.get('merged_result', valuation_data)
        dcf_analysis = merged_result.get('dcf_analysis', {})
        financials = merged_result.get('financials', valuation_data.get('financials', {}))
        
        # Get intrinsic value from various possible locations
        intrinsic_value = (
            valuation.get('fair_value') or
            dcf_analysis.get('intrinsic_value') or
            dcf_analysis.get('fair_value') or
            merged_result.get('fair_value')
        )
        
        # Get current market price
        market_price = (
            valuation.get('current_price') or
            financials.get('current_price') or
            financials.get('stock_price')
        )
        
        # Extract WACC and growth rates
        wacc = dcf_analysis.get('wacc') or merged_result.get('wacc')
        terminal_growth = (
            dcf_analysis.get('terminal_growth_rate') or 
            merged_result.get('terminal_growth_rate')
        )
        
        # Get revenue growth from financial DTO
        financial_dto = valuation_data.get('financialDTO', {})
        revenue_growth_rates = financial_dto.get('revenueGrowthRate', [])
        revenue_growth = revenue_growth_rates[0] if revenue_growth_rates else None
        
        # Build key assumptions list
        key_assumptions = []
        if revenue_growth:
            key_assumptions.append(f"Revenue growth: {revenue_growth:.1f}%")
        if wacc:
            key_assumptions.append(f"WACC: {wacc:.2f}%")
        if terminal_growth:
            key_assumptions.append(f"Terminal growth: {terminal_growth:.2f}%")
        
        return {
            'ticker': valuation.get('ticker'),
            'company_name': valuation.get('company_name'),
            'intrinsic_value': intrinsic_value,
            'market_price': market_price,
            'upside_percentage': valuation.get('upside_percentage'),
            'wacc': wacc,
            'terminal_growth_rate': terminal_growth,
            'revenue_growth_rate': revenue_growth,
            'key_assumptions': key_assumptions,
            'valuation_date': valuation.get('valuation_date'),
        }


# Singleton instance
_valuation_client: Optional[ValuationClient] = None


def get_valuation_client() -> ValuationClient:
    """Get the valuation client singleton."""
    global _valuation_client
    if _valuation_client is None:
        _valuation_client = ValuationClient()
    return _valuation_client
