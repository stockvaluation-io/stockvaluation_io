"""
DCF Client - Client for calling Java DCF calculation API.
"""
import logging
import requests
from typing import Dict, Any, Optional
from dataclasses import dataclass
from datetime import datetime

from config import Config

logger = logging.getLogger(__name__)


@dataclass
class ValuationOutput:
    """Represents the output from Java DCF API."""
    ticker: str
    company_name: str
    fair_value: float
    current_price: float
    upside_pct: float
    currency: Optional[str]
    inputs: Dict[str, Any]
    full_model: Dict[str, Any]
    
    @classmethod
    def from_api_response(cls, data: Dict[str, Any]) -> 'ValuationOutput':
        """Create from Java API response."""
        company_dto = data.get('companyDTO', {})
        financial_dto = data.get('financialDTO', {})
        
        fair_value = company_dto.get('estimatedValuePerShare', 0.0)
        current_price = company_dto.get('price', 0.0)
        upside_pct = ((fair_value - current_price) / current_price * 100) if current_price else 0.0
        
        return cls(
            ticker=company_dto.get('ticker', data.get('ticker', '')),
            company_name=company_dto.get('companyName', ''),
            fair_value=fair_value,
            current_price=current_price,
            upside_pct=round(upside_pct, 2),
            currency=(
                data.get('currency')
                or company_dto.get('currency')
                or financial_dto.get('currency')
            ),
            inputs={
                'compoundAnnualGrowth2_5': financial_dto.get('compoundAnnualGrowth2_5'),
                'targetPreTaxOperatingMargin': financial_dto.get('targetPreTaxOperatingMargin'),
                'salesToCapitalYears1To5': financial_dto.get('salesToCapitalYears1To5'),
                'initialCostCapital': financial_dto.get('initialCostCapital'),
                'terminalGrowthRate': financial_dto.get('terminalGrowthRate'),
            },
            full_model=data,
        )
    
    def to_snapshot_dict(self) -> Dict[str, Any]:
        """Convert to dict suitable for DCFSnapshot."""
        return {
            'ticker': self.ticker,
            'fair_value': self.fair_value,
            'current_price': self.current_price,
            'upside_pct': self.upside_pct,
            'currency': self.currency,
            'inputs': self.inputs,
            'full_model': self.full_model,
            'calculation_date': datetime.utcnow().date().isoformat(),
        }


class DCFClient:
    """
    Client for calling Java DCF calculation API.
    
    Endpoints used:
    - POST /valuation-output (recalculate with overrides)
    - GET /{ticker}/story-valuation-output (get base valuation)
    """
    
    def __init__(self, base_url: Optional[str] = None):
        self.base_url = base_url or Config.VALUATION_SERVICE_URL
        self.timeout = 30  # seconds
        logger.info(f"DCFClient initialized with base_url: {self.base_url}")
    
    def recalculate_dcf(
        self,
        ticker: str,
        overrides: Dict[str, Any]
    ) -> Optional[ValuationOutput]:
        """
        Recalculate DCF with parameter overrides.
        
        Args:
            ticker: Stock ticker symbol
            overrides: DCF parameter overrides, e.g.:
                {
                    "compoundAnnualGrowth2_5": 25.0,
                    "targetPreTaxOperatingMargin": 0.20,
                    "salesToCapitalYears1To5": 2.5,
                    "initialCostCapital": 0.10
                }
        
        Returns:
            ValuationOutput with recalculated values
        """
        url = f"{self.base_url}/valuation-output"
        
        try:
            logger.info(f"Calling Java DCF API: POST {url}?ticker={ticker}")
            logger.info(f"Overrides: {overrides}")
            
            response = requests.post(
                url,
                params={'ticker': ticker},
                json=overrides,
                timeout=self.timeout,
                headers={
                    'Content-Type': 'application/json',
                }
            )
            
            response.raise_for_status()
            data = response.json()
            
            # Extract the actual data from response wrapper
            if 'data' in data:
                data = data['data']
            
            valuation = ValuationOutput.from_api_response(data)
            logger.info(f"DCF recalculated for {ticker}: fair_value=${valuation.fair_value:.2f}")
            
            return valuation
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Error calling Java DCF API: {e}")
            return None
        except Exception as e:
            logger.error(f"Error processing DCF response: {e}")
            return None
    
    def get_base_valuation(self, ticker: str) -> Optional[ValuationOutput]:
        """
        Get base valuation (without overrides).
        
        Args:
            ticker: Stock ticker symbol
        
        Returns:
            ValuationOutput with base valuation
        """
        url = f"{self.base_url}/{ticker}/story-valuation-output"
        
        try:
            logger.info(f"Calling Java DCF API: GET {url}")
            
            response = requests.get(
                url,
                timeout=self.timeout,
                headers={
                    'Accept': 'application/json',
                }
            )
            
            response.raise_for_status()
            data = response.json()
            
            if 'data' in data:
                data = data['data']
            
            valuation = ValuationOutput.from_api_response(data)
            logger.info(f"Base valuation for {ticker}: fair_value=${valuation.fair_value:.2f}")
            
            return valuation
            
        except requests.exceptions.RequestException as e:
            logger.error(f"Error calling Java DCF API: {e}")
            return None
        except Exception as e:
            logger.error(f"Error processing DCF response: {e}")
            return None


# Singleton instance
_dcf_client: Optional[DCFClient] = None


def get_dcf_client() -> DCFClient:
    """Get or create DCF client singleton."""
    global _dcf_client
    if _dcf_client is None:
        _dcf_client = DCFClient()
    return _dcf_client
