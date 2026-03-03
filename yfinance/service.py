"""
YFinance service for handling financial data endpoints.
"""
import json
from typing import Callable, Dict, Any, Optional
from yfinance import Ticker

from config import RateLimitConfig, CacheConfig


class YFinanceService:
    """Service for handling YFinance data operations."""
    
    def __init__(self):
        self.session = self._create_session()
    
    def _create_session(self):
        """Create a session with rate limiting and caching."""
        try:
            from requests_cache import CacheMixin, SQLiteCache
            from requests_ratelimiter import LimiterMixin, MemoryQueueBucket
            from pyrate_limiter import Duration, RequestRate, Limiter
            from curl_cffi import Session as CurlSession
            
            class ChromeSession(CurlSession):
                def __init__(self, **kwargs):
                    kwargs['impersonate'] = "chrome"
                    super().__init__(**kwargs)

            # Create a session class that combines caching and rate limiting
            class CachedLimiterSession(CacheMixin, LimiterMixin, ChromeSession):
                pass

            # Create a session with rate limiting and caching
            session = CachedLimiterSession(
                limiter=Limiter(RequestRate(RateLimitConfig.REQUESTS_PER_SECOND, Duration.SECOND * RateLimitConfig.DURATION_SECONDS)),
                bucket_class=MemoryQueueBucket,
                backend=SQLiteCache(CacheConfig.SQLITE_PATH, expire_after=3600),
                ignored_parameters=["sessionId", "crumb"]
            )
            return session
        except ImportError:
            # Fallback to basic session if dependencies are not available
            return None
    
    def get_endpoint_map(self) -> Dict[str, Callable]:
        """Get mapping of endpoints to handler functions."""
        return {
            'balance-sheet': self._balance_sheet,
            'cash-flow': self._cash_flow,
            'fast-info': self._fast_info,
            'financials': self._financials,
            'income-stmt': self._income_stmt,
            'info': self._info,
            'revenue-estimate': self._revenue_estimate,
            'dividends': self._dividends
        }
    
    def _balance_sheet(self, ticker: str, freq: str) -> str:
        """Get balance sheet data."""
        return Ticker(ticker, session=self.session).get_balance_sheet(freq=freq).to_json()

    def _cash_flow(self, ticker: str, freq: str) -> str:
        """Get cash flow data."""
        return Ticker(ticker, session=self.session).get_cash_flow(freq=freq).to_json()

    def _fast_info(self, ticker: str, freq: str) -> str:
        """Get fast info data."""
        return Ticker(ticker).get_fast_info().toJSON()

    def _financials(self, ticker: str, freq: str) -> str:
        """Get financials data."""
        return Ticker(ticker, session=self.session).get_financials().to_json()

    def _income_stmt(self, ticker: str, freq: str) -> str:
        """Get income statement data."""
        return Ticker(ticker, session=self.session).get_income_stmt(freq=freq).to_json()

    def _info(self, ticker: str, freq: str) -> Dict[str, Any]:
        """Get info data."""
        return Ticker(ticker).get_info()

    def _revenue_estimate(self, ticker: str, freq: str) -> str:
        """Get revenue estimate data."""
        return Ticker(ticker, session=self.session).get_revenue_estimate().to_json()
    
    def _dividends(self, ticker: str, freq: str) -> Dict[str, Any]:
        """Get dividend data."""
        t = Ticker(ticker, session=self.session)
        info = t.info
        dividends = t.dividends
        
        dividend_history = None
        if dividends is not None and not dividends.empty:
            dividend_history = {
                str(date.date()): float(value) 
                for date, value in dividends.items()
            }
        
        return {
            "dividendRate": info.get("dividendRate"),
            "dividendYield": info.get("dividendYield"),
            "payoutRatio": info.get("payoutRatio"),
            "trailingAnnualDividendRate": info.get("trailingAnnualDividendRate"),
            "trailingAnnualDividendYield": info.get("trailingAnnualDividendYield"),
            "exDividendDate": info.get("exDividendDate"),
            "lastDividendValue": info.get("lastDividendValue"),
            "lastDividendDate": info.get("lastDividendDate"),
            "fiveYearAvgDividendYield": info.get("fiveYearAvgDividendYield"),
            "dividendHistory": dividend_history
        }
