"""
Utility functions for the stockvaluation.io application.
Enhanced with improved validation and error handling.
"""
import json
import logging
import math
import re
from collections import defaultdict
from difflib import SequenceMatcher
from typing import Any, Dict, List, Optional, Tuple, Union
from statistics import mean

import numpy as np
from rapidfuzz import process, fuzz

from domain.knowledge.tool_definitions import industry_mapping

logger = logging.getLogger(__name__)

SECTOR_TO_MAPPING = {e["sector"].lower(): e for e in industry_mapping}
_CURRENCY_TO_COUNTRY = {
    "USD": "United States",
    "EUR": "Euro Area",
    "GBP": "United Kingdom",
    "GBX": "United Kingdom",
    "JPY": "Japan",
    "INR": "India",
    "CNY": "China",
    "HKD": "Hong Kong",
    "SGD": "Singapore",
    "AUD": "Australia",
    "CAD": "Canada",
    "CHF": "Switzerland",
    "SEK": "Sweden",
    "NOK": "Norway",
    "DKK": "Denmark",
    "BRL": "Brazil",
    "MXN": "Mexico",
    "ZAR": "South Africa",
    "KRW": "South Korea",
    "TWD": "Taiwan",
    "AED": "United Arab Emirates",
    "SAR": "Saudi Arabia",
}
_TICKER_SUFFIX_TO_COUNTRY = {
    ".NS": "India",
    ".BO": "India",
    ".L": "United Kingdom",
    ".TO": "Canada",
    ".V": "Canada",
    ".AX": "Australia",
    ".HK": "Hong Kong",
    ".SS": "China",
    ".SZ": "China",
    ".T": "Japan",
    ".KS": "South Korea",
    ".KQ": "South Korea",
    ".SW": "Switzerland",
    ".PA": "France",
    ".DE": "Germany",
    ".AS": "Netherlands",
    ".MI": "Italy",
    ".MC": "Spain",
    ".ST": "Sweden",
    ".CO": "Denmark",
    ".OL": "Norway",
    ".HE": "Finland",
    ".BR": "Belgium",
    ".SA": "Brazil",
    ".MX": "Mexico",
    ".SI": "Singapore",
    ".NZ": "New Zealand",
    ".J": "South Africa",
}


def infer_country_for_macro(
    country: Any,
    currency: Any,
    ticker: Any,
    default_country: str = "Global",
) -> str:
    """Resolve a usable macro-news country from available metadata."""
    raw_country = str(country or "").strip()
    if raw_country and raw_country.lower() not in {"none", "null", "unknown", "n/a"}:
        return raw_country

    currency_code = str(currency or "").strip().upper()
    if currency_code in _CURRENCY_TO_COUNTRY:
        return _CURRENCY_TO_COUNTRY[currency_code]

    normalized_ticker = str(ticker or "").strip().upper()
    if "." in normalized_ticker:
        suffix = f".{normalized_ticker.rsplit('.', 1)[-1]}"
        if suffix in _TICKER_SUFFIX_TO_COUNTRY:
            return _TICKER_SUFFIX_TO_COUNTRY[suffix]

    return default_country

# ============================================================================
# HELPER FUNCTIONS (Enhanced with validation)
# ============================================================================

def safe_cast(x: Any, to=float) -> Optional[float]:
    """Safely cast to numeric; treat NaN/inf/"null"/None as None."""
    try:
        if x is None or x == "null" or x == "":
            return None
        v = to(x)
        if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
            return None
        return v
    except (ValueError, TypeError):
        return None


def _clean_list(arr: Any) -> List[float]:
    """Return list of valid floats from input array; tolerant to None, 'null' strings, NaN, inf."""
    if not isinstance(arr, list):
        return []
    out = []
    for x in arr:
        v = safe_cast(x, float)
        if v is not None:
            out.append(v)
    return out


def deep_clean(x: Any):
    """Recursively drop None and 'null' values from dict/list."""
    if isinstance(x, dict):
        return {k: deep_clean(v) for k, v in x.items() if v is not None and v != "null"}
    if isinstance(x, list):
        cleaned = [deep_clean(v) for v in x if v is not None and v != "null"]
        return cleaned
    return x


def quantiles(arr: Any):
    """Calculate distribution statistics for array."""
    a = _clean_list(arr)
    if not a:
        return None
    a_sorted = sorted(a)
    n = len(a_sorted)

    def q(p: float):
        idx = int(min(max(round(p * (n - 1)), 0), n - 1))
        return a_sorted[idx]

    return {
        "min": a_sorted[0],
        "p25": q(0.25),
        "median": q(0.5),
        "p75": q(0.75),
        "max": a_sorted[-1],
        "mean": mean(a_sorted) if n > 0 else None,
        "count": n
    }


def is_monotonic(arr: Any, direction: str = "increasing") -> Optional[bool]:
    """Check if array is monotonic in given direction."""
    a = _clean_list(arr)
    if len(a) < 2:
        return None
    if direction == "increasing":
        return all(a[i] <= a[i + 1] for i in range(len(a) - 1))
    if direction == "decreasing":
        return all(a[i] >= a[i + 1] for i in range(len(a) - 1))
    return None


def pct(x: Optional[float]) -> Optional[float]:
    """Convert decimal to percentage."""
    return None if x is None else float(x) * 100.0


def safe_div(a: Optional[float], b: Optional[float]) -> Optional[float]:
    """Safely divide two numbers."""
    try:
        if a is None or b in (None, 0):
            return None
        v = a / b
        return v if not (isinstance(v, float) and (math.isnan(v) or math.isinf(v))) else None
    except Exception:
        return None


def first_and_last_non_terminal(arr: Any) -> Tuple[Optional[float], Optional[float]]:
    """
    Get first value and last non-terminal value (penultimate).
    Enhanced with validation.
    """
    a = _clean_list(arr)
    if len(a) < 2:
        logger.warning(f"Array too short for first_and_last_non_terminal: length={len(a)}")
        return None, None
    return a[0], a[-2]


def calc_cagr(start: Optional[float], end: Optional[float], periods: Optional[int]) -> Optional[float]:
    """
    Calculate CAGR with enhanced validation.
    Handles negative values and edge cases.
    """
    try:
        if start is None or end is None or periods is None:
            return None
        if periods <= 0:
            logger.warning(f"Invalid periods for CAGR: {periods}")
            return None
        if start == 0:
            logger.warning("CAGR calculation: start value is zero")
            return None
        # Handle negative values
        if start < 0 or end < 0:
            logger.warning(f"CAGR calculation with negative values: start={start}, end={end}")
            if start < 0 and end < 0:
                return -1 * ((abs(end) / abs(start)) ** (1.0 / periods) - 1.0)
            else:
                return None
        return (end / start) ** (1.0 / periods) - 1.0
    except (ValueError, ZeroDivisionError, OverflowError) as e:
        logger.error(f"CAGR calculation error: {e}")
        return None


def linreg_trend(values: Any, exclude_terminal: bool = True) -> Tuple[Optional[float], Optional[float]]:
    """
    Calculate linear regression trend with enhanced validation.
    Returns (slope, r_squared).
    """
    y = _clean_list(values)
    if len(y) < 2:
        logger.warning(f"Array too short for trend calculation: length={len(y)}")
        return None, None
    
    y_fit = y[:-1] if (exclude_terminal and len(y) > 2) else y
    
    if len(y_fit) < 2:
        logger.warning(f"Insufficient data after terminal exclusion: length={len(y_fit)}")
        return None, None
    
    x = np.arange(len(y_fit))
    
    try:
        coeffs = np.polyfit(x, y_fit, 1)
        slope = float(coeffs[0])
        y_pred = np.polyval(coeffs, x)
        ss_res = float(np.sum((y_fit - y_pred) ** 2))
        ss_tot = float(np.sum((y_fit - np.mean(y_fit)) ** 2))
        r2 = 1 - ss_res / ss_tot if ss_tot != 0 else None
        return slope, r2
    except (np.linalg.LinAlgError, ValueError) as e:
        logger.error(f"Trend calculation error: {e}")
        return None, None


def validate_array_lengths(arrays: Dict[str, List], expected_min: int = 2, tolerance: int = 1) -> Dict[str, Any]:
    """
    Validate that all arrays meet minimum length requirements.
    
    Args:
        arrays: Dictionary of array name to array
        expected_min: Minimum required length for all arrays
        tolerance: Allowable difference in lengths before warning (default: 1)
                   A difference of 1 is common (e.g., 11 vs 12 years) and usually acceptable
    
    Returns:
        dict with validation results containing 'valid', 'errors', and 'warnings'
    """
    validation = {
        "valid": True,
        "errors": [],
        "warnings": []
    }
    
    lengths = {name: len(arr) for name, arr in arrays.items()}
    
    # Check minimum length requirements
    for name, length in lengths.items():
        if length < expected_min:
            validation["valid"] = False
            validation["errors"].append(f"{name}: length {length} < minimum {expected_min}")
    
    # Check for length inconsistencies
    if lengths:
        max_len = max(lengths.values())
        min_len = min(lengths.values())
        length_diff = max_len - min_len
        
        if length_diff > tolerance:
            # Show which arrays have which lengths for debugging
            length_groups = {}
            for name, length in lengths.items():
                if length not in length_groups:
                    length_groups[length] = []
                length_groups[length].append(name)
            
            # Build detailed warning message
            length_details = ", ".join([
                f"{length} ({', '.join(names)})" 
                for length, names in sorted(length_groups.items())
            ])
            
            validation["warnings"].append(
                f"Inconsistent array lengths: min={min_len}, max={max_len} "
                f"(difference={length_diff}). Lengths: {length_details}"
            )
        elif length_diff > 0:
            # Small difference (within tolerance) - log at debug level instead of warning
            logger.debug(
                f"Array length difference within tolerance: min={min_len}, max={max_len} "
                f"(difference={length_diff}). This is usually acceptable."
            )
    
    return validation


def normalize_percentage(value: Any, field_name: str = "") -> Optional[float]:
    """
    Normalize percentage values with improved validation.
    Handles decimals (0-1) and percentages (0-100+).
    """
    if value is None:
        return None
    
    try:
        val = float(value)
        
        if 0 <= val <= 1.0:
            result = val * 100.0
            logger.debug(f"{field_name}: {val} interpreted as decimal → {result:.2f}%")
            return result
        elif 1.0 < val <= 100:
            return val
        elif val > 100:
            logger.warning(f"{field_name}: value {val} > 100% - accepting as-is")
            return val
        elif val < 0:
            logger.warning(f"{field_name}: negative value {val}% detected")
            return val
        else:
            return val
            
    except (ValueError, TypeError) as e:
        logger.error(f"Cannot normalize {field_name}={value}: {e}")
        return None


def normalize_rate(value: Any, field_name: str = "", max_reasonable: float = 1.0) -> Optional[float]:
    """
    Normalize rate values (cost of capital, risk-free rate, etc.).
    """
    if value is None:
        return None
    
    try:
        val = float(value)
        
        if 0 <= val <= max_reasonable:
            return val
        elif max_reasonable < val <= 100:
            result = val / 100.0
            logger.debug(f"{field_name}: {val} interpreted as percentage → {result:.4f}")
            return result
        elif val > 100:
            result = val / 10000.0
            logger.warning(f"{field_name}: {val} seems like basis points → {result:.4f}")
            return result
        elif val < 0:
            logger.warning(f"{field_name}: negative rate {val} detected")
            return None
        
        return val
        
    except (ValueError, TypeError) as e:
        logger.error(f"Cannot normalize rate {field_name}={value}: {e}")
        return None


# ============================================================================
# ENHANCED preprocess_dcf_json
# ============================================================================

def preprocess_dcf_json(raw_dcf: Dict) -> Dict:
    """
    Enhanced DCF preprocessing with improved validation and error handling.
    Maintains exact same output structure as original.
    """
    try:
        fin = raw_dcf.get("financial_dto", {}) or {}
        comp = raw_dcf.get("company_dto", {}) or {}
        base = raw_dcf.get("base_year_comparison", {}) or {}
        term = raw_dcf.get("terminal_value_dto", {}) or {}

        revenues = _clean_list(fin.get("revenues", []))
        revenue_growth_rate = _clean_list(fin.get("revenue_growth_rate", []))
        margins = _clean_list(fin.get("ebit_operating_margin", []))
        roic = _clean_list(fin.get("roic", []))
        wacc = _clean_list(fin.get("cost_of_capital", []))
        sales_to_capital = _clean_list(fin.get("sales_to_capital_ratio", []))
        invested_capital = _clean_list(fin.get("invested_capital", []))
        fcff = _clean_list(fin.get("fcff", []))
        reinvestment = _clean_list(fin.get("reinvestment", []))
        pv_fcff = _clean_list(fin.get("pv_fcff", []))
        ebit = _clean_list(fin.get("ebit_operating_income", []))

        validation = validate_array_lengths({
            "revenues": revenues,
            "margins": margins,
            "roic": roic,
            "wacc": wacc
        }, expected_min=2)
        
        if not validation["valid"]:
            logger.error(f"Array validation failed: {validation['errors']}")
        if validation["warnings"]:
            logger.warning(f"Array validation warnings: {validation['warnings']}")

        # Extract sector data
        sectors_data = {}
        sector_names = set()
        
        sector_dict_keys = [
            "revenues_by_sector",
            "revenue_growth_rate_by_sector",
            "ebit_operating_margin_by_sector",
            "roic_by_sector",
            "cost_of_capital_by_sector"
        ]
        
        for key in sector_dict_keys:
            sector_dict = fin.get(key, {})
            if isinstance(sector_dict, dict):
                sector_names.update(sector_dict.keys())
        
        logger.info(f"Found {len(sector_names)} sectors: {sector_names}")

        for sector in sector_names:
            try:
                sector_data = {
                    "revenues": _clean_list(fin.get("revenues_by_sector", {}).get(sector, [])),
                    "revenue_growth_rate": _clean_list(fin.get("revenue_growth_rate_by_sector", {}).get(sector, [])),
                    "margins": _clean_list(fin.get("ebit_operating_margin_by_sector", {}).get(sector, [])),
                    "ebit_operating_income": _clean_list(fin.get("ebit_operating_income_sector", {}).get(sector, [])),
                    "ebit1_minus_tax": _clean_list(fin.get("ebit1_minus_tax_by_sector", {}).get(sector, [])),
                    "sales_to_capital_ratio": _clean_list(fin.get("sales_to_capital_ratio_by_sector", {}).get(sector, [])),
                    "reinvestment": _clean_list(fin.get("reinvestment_by_sector", {}).get(sector, [])),
                    "invested_capital": _clean_list(fin.get("invested_capital_by_sector", {}).get(sector, [])),
                    "fcff": _clean_list(fin.get("fcff_by_sector", {}).get(sector, [])),
                    "roic": _clean_list(fin.get("roic_by_sector", {}).get(sector, [])),
                    "wacc": _clean_list(fin.get("cost_of_capital_by_sector", {}).get(sector, [])),
                    "pv_fcff": _clean_list(fin.get("pv_fcff_by_sector", {}).get(sector, []))
                }
                
                if len(sector_data["revenues"]) < 2:
                    logger.warning(f"Sector '{sector}' has insufficient revenue data")
                    continue
                
                rev_start, rev_end = first_and_last_non_terminal(sector_data["revenues"])
                margin_start, margin_end = first_and_last_non_terminal(sector_data["margins"])
                roic_start, roic_end = first_and_last_non_terminal(sector_data["roic"])
                
                periods = max(0, len(sector_data["revenues"]) - 2) if sector_data["revenues"] else None
                if periods is not None and periods <= 0:
                    logger.warning(f"Sector '{sector}': invalid periods={periods}")
                    periods = None
                
                revenue_cagr = calc_cagr(rev_start, rev_end, periods) if (rev_start and rev_end and periods) else None
                
                rev_slope, rev_r2 = linreg_trend(sector_data["revenues"], exclude_terminal=True)
                margin_slope, margin_r2 = linreg_trend(sector_data["margins"], exclude_terminal=True)
                roic_slope, roic_r2 = linreg_trend(sector_data["roic"], exclude_terminal=True)
                
                sector_rev_sum = None
                total_rev_sum = None
                sector_contribution_pct = None
                
                if len(sector_data["revenues"]) > 1 and len(revenues) > 1:
                    sector_rev_sum = sum(sector_data["revenues"][:-1])
                    total_rev_sum = sum(revenues[:-1])
                    if total_rev_sum > 0:
                        sector_contribution_pct = (sector_rev_sum / total_rev_sum) * 100.0
                        
                        if sector_contribution_pct > 100:
                            logger.warning(f"Sector '{sector}' contribution >100%: {sector_contribution_pct:.2f}%")
                        elif sector_contribution_pct < 0:
                            logger.warning(f"Sector '{sector}' contribution <0%: {sector_contribution_pct:.2f}%")
                
                sector_data["derived"] = {
                    "revenue_cagr_pct": round(pct(revenue_cagr), 2) if revenue_cagr is not None else None,
                    "revenue_trend_slope": round(rev_slope, 6) if rev_slope is not None else None,
                    "revenue_trend_r2": round(rev_r2, 3) if rev_r2 is not None else None,
                    "margin_trend_slope_pct": round(margin_slope, 4) if margin_slope is not None else None,
                    "margin_trend_r2": round(margin_r2, 3) if margin_r2 is not None else None,
                    "roic_trend_slope_pct": round(roic_slope, 4) if roic_slope is not None else None,
                    "roic_trend_r2": round(roic_r2, 3) if roic_r2 is not None else None,
                    "roic_end_pct": round(roic_end, 3) if roic_end is not None else None,
                    "margin_start_pct": round(margin_start, 3) if margin_start is not None else None,
                    "margin_end_pct": round(margin_end, 3) if margin_end is not None else None,
                    "sector_contribution_pct": round(sector_contribution_pct, 2) if sector_contribution_pct is not None else None,
                    "pv_fcff_sum": round(sum(sector_data["pv_fcff"]), 2) if sector_data["pv_fcff"] else None
                }
                
                sector_data["distributions"] = {
                    "revenues": quantiles(sector_data["revenues"]),
                    "margins_pct": quantiles(sector_data["margins"]),
                    "roic_pct": quantiles(sector_data["roic"]),
                    "fcff": quantiles(sector_data["fcff"])
                }
                
                sectors_data[sector] = sector_data
                
            except Exception as e:
                logger.error(f"Error processing sector '{sector}': {e}", exc_info=True)
                continue

        rev_start, rev_end = first_and_last_non_terminal(revenues)
        margin_start, margin_end = first_and_last_non_terminal(margins)
        roic_start, roic_end = first_and_last_non_terminal(roic)
        wacc_start, wacc_end = first_and_last_non_terminal(wacc)

        periods = max(0, len(revenues) - 2) if revenues and len(revenues) >= 2 else None
        if periods is not None and periods <= 0:
            logger.warning(f"Invalid periods calculated: {periods}")
            periods = None

        revenue_cagr = calc_cagr(rev_start, rev_end, periods) if (rev_start is not None and rev_end is not None and periods is not None) else None

        rolling_growth = []
        if len(revenues) >= 3:
            rev_seq = revenues[:-1]
            for i in range(1, len(rev_seq)):
                g = safe_div(rev_seq[i] - rev_seq[i - 1], rev_seq[i - 1])
                rolling_growth.append(g if g is not None else None)

        rev_slope, rev_r2 = linreg_trend(revenues, exclude_terminal=True)
        margin_slope, margin_r2 = linreg_trend(margins, exclude_terminal=True)
        roic_slope, roic_r2 = linreg_trend(roic, exclude_terminal=True)
        wacc_slope, wacc_r2 = linreg_trend(wacc, exclude_terminal=True)

        rev_slope_incl, rev_r2_incl = linreg_trend(revenues, exclude_terminal=False)
        margin_slope_incl, margin_r2_incl = linreg_trend(margins, exclude_terminal=False)
        roic_slope_incl, roic_r2_incl = linreg_trend(roic, exclude_terminal=False)
        wacc_slope_incl, wacc_r2_incl = linreg_trend(wacc, exclude_terminal=False)

        pv_terminal = safe_cast(comp.get("pv_terminal_value"))
        pv_sum = safe_cast(comp.get("sum_of_pv"))
        pv_cf_next10 = safe_cast(comp.get("pv_cf_over_next_10_years"))
        
        terminal_contribution_pct = None
        if pv_terminal is not None and pv_sum not in (None, 0):
            terminal_contribution_pct = pv_terminal / pv_sum * 100.0
            if terminal_contribution_pct < 0 or terminal_contribution_pct > 100:
                logger.warning(f"Terminal contribution out of bounds: {terminal_contribution_pct:.2f}%")

        est_value_per_share = safe_cast(comp.get("estimated_value_per_share"))
        market_price = safe_cast(comp.get("price"))
        price_to_value_pct = safe_cast(comp.get("price_as_percentage_of_value"))
        value_of_equity = safe_cast(comp.get("value_of_equity_in_common_stock"))
        number_of_shares = safe_cast(comp.get("number_of_shares"))

        roic_end_val = roic_end
        wacc_end_val = wacc_end
        roic_wacc_spread = None
        if roic_end_val is not None and wacc_end_val is not None:
            roic_wacc_spread = roic_end_val - wacc_end_val

        pv_fcff_sum = sum(pv_fcff) if pv_fcff else None
        pv_next10 = pv_cf_next10
        sum_of_pv = safe_cast(comp.get("sum_of_pv"))

        fcff_coverage_ratio = None
        if pv_fcff_sum is not None and pv_next10 not in (None, 0):
            fcff_coverage_ratio = pv_fcff_sum / pv_next10
            if abs(fcff_coverage_ratio - 1.0) > 0.1:
                logger.warning(f"FCFF coverage ratio far from 1.0: {fcff_coverage_ratio:.3f}")

        term_growth = safe_cast(term.get("growth_rate"))
        term_wacc = safe_cast(term.get("cost_of_capital"))
        term_roic = safe_cast(term.get("return_on_capital"))
        term_reinv = safe_cast(term.get("reinvestment_rate"))

        gordon_reinv_pct = None
        if term_growth is not None and term_roic not in (None, 0):
            gordon_reinv_pct = (term_growth / term_roic) * 100.0

        implied_reinvestment = None
        if revenue_growth_rate and sales_to_capital:
            try:
                stc = mean(sales_to_capital[:-1]) if len(sales_to_capital) > 1 else (sales_to_capital[0] if sales_to_capital else None)
                g = mean(revenue_growth_rate[:-1]) if len(revenue_growth_rate) > 1 else (revenue_growth_rate[0] if revenue_growth_rate else None)
                if stc not in (None, 0) and g is not None:
                    implied_reinvestment = (g / stc) * 100.0
            except Exception as e:
                logger.error(f"Error calculating implied reinvestment: {e}")
                implied_reinvestment = None

        summary = {
            "company": raw_dcf.get("company_name"),
            "currency": raw_dcf.get("currency"),
            "horizon_years": len(revenues) - 1 if revenues else None,
            "has_sector_breakdown": len(sectors_data) > 0,
            "sectors": list(sectors_data.keys()) if sectors_data else None,
            "revenues": revenues,
            "revenue_growth_rate": revenue_growth_rate,
            "margins": margins,
            "ebit_operating_income": ebit,
            "invested_capital": invested_capital,
            "sales_to_capital_ratio": sales_to_capital,
            "reinvestment": reinvestment,
            "derived": {
                "revenue_cagr_pct": round(pct(revenue_cagr), 2) if revenue_cagr is not None else None,
                "revenue_trend_slope": round(rev_slope, 6) if rev_slope is not None else None,
                "revenue_trend_r2": round(rev_r2, 3) if rev_r2 is not None else None,
                "margin_trend_slope_pct": round(margin_slope, 4) if margin_slope is not None else None,
                "margin_trend_r2": round(margin_r2, 3) if margin_r2 is not None else None,
                "roic_trend_slope_pct": round(roic_slope, 4) if roic_slope is not None else None,
                "roic_trend_r2": round(roic_r2, 3) if roic_r2 is not None else None,
                "wacc_trend_slope_pct": round(wacc_slope, 4) if wacc_slope is not None else None,
                "wacc_trend_r2": round(wacc_r2, 3) if wacc_r2 is not None else None,
                "roic_end_pct": round(roic_end_val, 3) if roic_end_val is not None else None,
                "wacc_end_pct": round(wacc_end_val, 3) if wacc_end_val is not None else None,
                "roic_wacc_spread_pct": round(roic_wacc_spread, 3) if roic_wacc_spread is not None else None,
                "terminal_contribution_pct": round(terminal_contribution_pct, 2) if terminal_contribution_pct is not None else None,
                "pv_fcff_sum": round(pv_fcff_sum, 2) if pv_fcff_sum is not None else None,
                "pv_next10": round(pv_next10, 2) if pv_next10 is not None else None,
                "sum_of_pv": round(sum_of_pv, 2) if sum_of_pv is not None else None,
                "estimated_value_per_share": round(est_value_per_share, 4) if est_value_per_share is not None else None,
                "market_price": round(market_price, 4) if market_price is not None else None,
                "price_to_value_pct": round(price_to_value_pct, 2) if price_to_value_pct is not None else None,
                "value_of_equity": round(value_of_equity, 2) if value_of_equity is not None else None,
                "number_of_shares": number_of_shares
            },
            "terminal_assumptions": {
                "terminal_growth_rate_pct": term.get("growth_rate"),
                "terminal_cost_of_capital_pct": term.get("cost_of_capital"),
                "terminal_roic_pct": term.get("return_on_capital"),
                "terminal_reinvestment_rate_pct": term.get("reinvestment_rate")
            },
            "base_year_comparison": base,
            "sectors_data": sectors_data if sectors_data else None
        }

        insights = []

        if revenue_cagr is not None and periods is not None:
            insights.append(f"Revenue CAGR (pre-terminal) ≈ {revenue_cagr*100:.2f}% over {periods} years (start {rev_start:,.0f} → end {rev_end:,.0f}).")

        if rev_slope is not None:
            trend_dir = "upward" if rev_slope > 0 else "downward" if rev_slope < 0 else "flat"
            r2_text = f" R²={rev_r2:.3f}." if rev_r2 is not None else ""
            insights.append(f"Revenue trend is {trend_dir} (slope {rev_slope:.3e}).{r2_text}")

        if margin_start is not None and margin_end is not None:
            delta = margin_end - margin_start
            if abs(delta) < 0.5:
                insights.append(f"Operating margins are stable around {margin_end:.2f}%.")
            else:
                insights.append(f"Operating margins change by {delta:.2f} p.p. (start {margin_start:.2f}% → end {margin_end:.2f}%).")

        if roic_end_val is not None and wacc_end_val is not None:
            insights.append(f"End-period ROIC {roic_end_val:.2f}% vs WACC {wacc_end_val:.2f}% → spread {roic_wacc_spread:.2f} p.p.")
            insights.append("ROIC exceeds WACC — model implies economic profit and value creation in terminal state." if (roic_wacc_spread and roic_wacc_spread > 0) else "ROIC below WACC — model implies eventual destruction of value in terminal state.")

        if terminal_contribution_pct is not None:
            insights.append(f"Terminal value contributes ≈ {terminal_contribution_pct:.1f}% of total DCF value (PV terminal {pv_terminal:,.0f} / sum PV {pv_sum:,.0f}).")

        if sectors_data:
            insights.append(f"Model includes {len(sectors_data)} sector breakdown(s): {', '.join(sectors_data.keys())}.")
            for sector_name, sector_info in sectors_data.items():
                contrib = sector_info["derived"].get("sector_contribution_pct")
                if contrib:
                    insights.append(f"{sector_name} contributes ≈ {contrib:.1f}% of total revenue.")

        if pv_fcff_sum is not None and pv_next10 is not None and sum_of_pv is not None:
            insights.append(f"Sum PV(FCFF) ≈ {pv_fcff_sum:,.0f}; PV next 10 yrs ≈ {pv_next10:,.0f}; sumOfPV ≈ {sum_of_pv:,.0f}.")

        if est_value_per_share is not None and market_price is not None:
            if market_price > est_value_per_share * 1.1:
                insights.append(f"Market price ${market_price:.2f} > intrinsic ${est_value_per_share:.2f} by >10% — market appears richly priced relative to model.")
            elif market_price < est_value_per_share * 0.9:
                insights.append(f"Market price ${market_price:.2f} < intrinsic ${est_value_per_share:.2f} by >10% — model suggests undervaluation.")
            else:
                insights.append(f"Market price ${market_price:.2f} is within ±10% of intrinsic value ${est_value_per_share:.2f}.")

        if sales_to_capital:
            try:
                avg_sales_to_cap = float(mean(sales_to_capital[:-1])) if len(sales_to_capital) > 1 else float(sales_to_capital[0])
                insights.append(f"Avg sales-to-capital (forecast) ≈ {avg_sales_to_cap:.2f}, implying revenue generated per $1 of capital.")
            except Exception:
                pass

        if term_growth is not None and term_wacc is not None and term_growth >= term_wacc:
            insights.append("Terminal growth rate ≥ cost of capital — terminal value may be overstated; consider lowering g or increasing WACC.")
        if term_reinv is not None and gordon_reinv_pct is not None:
            diff = term_reinv - gordon_reinv_pct
            if abs(diff) > 5:
                insights.append(f"Terminal reinvestment {term_reinv:.2f}% diverges from g/ROIC {gordon_reinv_pct:.2f}% by {diff:.2f} p.p. — review terminal identity.")

        if fcff_coverage_ratio is not None:
            insights.append(f"PV(FCFF) coverage vs PV(next 10y) ≈ {fcff_coverage_ratio:.2f}×.")

        summary["insight_text"] = " ".join(insights)

        diagnostics = {
            "trends_inclusive": {
                "revenue_slope": rev_slope_incl,
                "revenue_r2": rev_r2_incl,
                "margin_slope_pct": margin_slope_incl,
                "margin_r2": margin_r2_incl,
                "roic_slope_pct": roic_slope_incl,
                "roic_r2": roic_r2_incl,
                "wacc_slope_pct": wacc_slope_incl,
                "wacc_r2": wacc_r2_incl
            },
            "coverage": {
                "pv_fcff_sum": pv_fcff_sum,
                "pv_next10": pv_next10,
                "sum_of_pv": sum_of_pv,
                "fcff_coverage_ratio": fcff_coverage_ratio
            },
            "identities": {
                "terminal_gordon_reinvestment_pct": gordon_reinv_pct,
                "terminal_input_reinvestment_pct": term_reinv,
                "implied_reinvestment_from_growth_and_stc": implied_reinvestment
            },
            "monotonicity": {
                "revenues_increasing": is_monotonic(revenues, "increasing"),
                "wacc_decreasing": is_monotonic(wacc, "decreasing"),
                "margins_increasing": is_monotonic(margins, "increasing"),
                "invested_capital_increasing": is_monotonic(invested_capital, "increasing")
            },
            "rolling_revenue_growth": [g for g in rolling_growth if g is not None] if rolling_growth else None
        }

        distributions = {
            "revenues": quantiles(revenues),
            "margins_pct": quantiles(margins),
            "roic_pct": quantiles(roic),
            "wacc_pct": quantiles(wacc),
            "reinvestment": quantiles(reinvestment),
            "fcff": quantiles(fcff),
            "pv_fcff": quantiles(pv_fcff) if pv_fcff else None
        }

        warnings = []
        quality_flags = {}

        if term_growth is not None and term_wacc is not None and term_growth >= term_wacc:
            warnings.append("terminal_growth_ge_wacc")
            quality_flags["terminal_g_lt_wacc"] = False
        elif term_growth is not None and term_wacc is not None:
            quality_flags["terminal_g_lt_wacc"] = True

        if terminal_contribution_pct is not None and terminal_contribution_pct > 85:
            warnings.append("terminal_contribution_very_high")
        if terminal_contribution_pct is not None and terminal_contribution_pct < 40:
            warnings.append("terminal_contribution_low")

        if price_to_value_pct is not None and price_to_value_pct > 150:
            warnings.append("market_price_far_above_model_value")
        if price_to_value_pct is not None and price_to_value_pct < 67:
            warnings.append("market_price_far_below_model_value")

        if pv_fcff_sum is not None and pv_next10 is not None:
            tol = 0.05 * pv_next10
            if abs(pv_fcff_sum - pv_next10) > max(tol, 1.0):
                warnings.append("pv_fcff_sum_differs_from_pv_next10")

        summary["derived_extras"] = {
            "implied_reinvestment_ratio_perc_from_growth_stc": implied_reinvestment,
            "gordon_identity_reinvestment_perc": gordon_reinv_pct
        }
        summary["distributions"] = distributions
        summary["diagnostics"] = diagnostics
        summary["warnings"] = warnings if warnings else None
        summary["quality_flags"] = quality_flags if quality_flags else None
        summary["provenance"] = {
            "uses_terminal_exclusion_for_trends": True,
            "version": "dcf_pre_v2.3_sector_aware"
        }

        return deep_clean(summary)

    except Exception as e:
        logger.error(f"Critical error in preprocess_dcf_json: {e}", exc_info=True)
        return {
            "company": raw_dcf.get("companyName"),
            "currency": raw_dcf.get("currency"),
            "error": str(e),
            "provenance": {"version": "dcf_pre_v2.3_sector_aware", "error": True}
        }


# ============================================================================
# ENHANCED preprocess_financials_json
# ============================================================================

def preprocess_financials_json(raw: Dict) -> Dict:
    """
    Enhanced financials preprocessing with improved validation and error handling.
    Maintains exact same output structure as original.
    """
    try:
        basic = (raw.get("basic_info_data_dto") or {}).copy()
        fin = (raw.get("financial_data_dto") or {}).copy()

        company = basic.get("company_name")
        ticker = basic.get("ticker")
        currency = basic.get("currency") or basic.get("stock_currency") or fin.get("currency")
        
        market_cap = safe_cast(basic.get("market_cap"))
        if market_cap is not None and market_cap <= 0:
            logger.warning(f"Invalid market cap: {market_cap}")
            market_cap = None
        
        stock_price = safe_cast(fin.get("stock_price"))
        shares_outstanding = safe_cast(fin.get("no_of_share_outstanding"))

        revenue_ttm = safe_cast(fin.get("revenue_ttm"))
        revenue_ltm = safe_cast(fin.get("revenue_ltm"))
        op_inc_ttm = safe_cast(fin.get("operating_income_ttm"))
        op_inc_ltm = safe_cast(fin.get("operating_income_ltm"))

        book_equity = safe_cast(fin.get("book_value_equality_ttm"))
        book_debt = safe_cast(fin.get("book_value_debt_ttm"))
        cash = safe_cast(fin.get("cash_and_markabl_ttm"))
        minor_int = safe_cast(fin.get("minority_interest_ttm"))
        interest_ttm = safe_cast(fin.get("interest_expense_ttm"))

        if book_equity is not None and book_equity < 0:
            logger.warning(f"Negative book equity: {book_equity}")
        if book_debt is not None and book_debt < 0:
            logger.warning(f"Negative book debt: {book_debt}")

        held_inst = normalize_percentage(basic.get("held_percent_institutions"), "held_percent_institutions")
        held_ins = normalize_percentage(basic.get("held_percent_insiders"), "held_percent_insiders")

        if held_inst is not None and held_inst > 100:
            logger.warning(f"Institutional ownership >100%: {held_inst}%")
        if held_ins is not None and held_ins > 100:
            logger.warning(f"Insider ownership >100%: {held_ins}%")

        eff_tax = normalize_percentage(fin.get("effective_tax_rate"), "effective_tax_rate")
        marg_tax = normalize_percentage(fin.get("marginal_tax_rate"), "marginal_tax_rate")

        if eff_tax is not None and (eff_tax < 0 or eff_tax > 100):
            logger.warning(f"Unusual effective tax rate: {eff_tax}%")
        if marg_tax is not None and (marg_tax < 0 or marg_tax > 100):
            logger.warning(f"Unusual marginal tax rate: {marg_tax}%")

        rfr = normalize_rate(fin.get("risk_free_rate"), "risk_free_rate", max_reasonable=0.15)
        acc = normalize_rate(fin.get("initial_cost_capital"), "initial_cost_capital", max_reasonable=0.50)

        if rfr is not None and rfr > 0.20:
            logger.warning(f"Unusually high risk-free rate: {rfr*100:.2f}%")
        if acc is not None and acc > 0.50:
            logger.warning(f"Unusually high cost of capital: {acc*100:.2f}%")

        def safe_pct_div(a, b):
            val = safe_div(a, b)
            return None if val is None else val * 100.0

        op_margin_ttm = safe_pct_div(op_inc_ttm, revenue_ttm)
        op_margin_ltm = safe_pct_div(op_inc_ltm, revenue_ltm)

        if op_margin_ttm is not None and abs(op_margin_ttm) > 100:
            logger.warning(f"Operating margin TTM seems extreme: {op_margin_ttm:.2f}%")
        if op_margin_ltm is not None and abs(op_margin_ltm) > 100:
            logger.warning(f"Operating margin LTM seems extreme: {op_margin_ltm:.2f}%")

        pb = None
        if market_cap is not None and book_equity is not None and book_equity > 0:
            pb = market_cap / book_equity
            if pb < 0:
                logger.warning(f"Negative P/B ratio: {pb:.3f}")
            elif pb > 1000:
                logger.warning(f"Extremely high P/B ratio: {pb:.3f}")

        d_to_e = safe_cast(basic.get("debtToEquity"))
        if d_to_e is None or d_to_e == 0:
            if book_debt is not None and book_equity is not None and book_equity > 0:
                d_to_e = (book_debt / book_equity) * 100.0
        
        if d_to_e is not None and d_to_e < 0:
            logger.warning(f"Negative debt-to-equity: {d_to_e:.2f}%")

        r_map = fin.get("research_and_development_map", {}) or {}
        r_and_d = None
        if isinstance(r_map, dict):
            r_and_d = (r_map.get("currentR&D-0") or 
                      r_map.get("currentR&D_0") or
                      r_map.get("currentR&D"))
        
        r_and_d = safe_cast(r_and_d)
        r_and_d_pct = None
        if r_and_d is not None and revenue_ttm is not None and revenue_ttm > 0:
            r_and_d_pct = (r_and_d / revenue_ttm) * 100.0

        low = safe_cast(fin.get("lowest_stock_price"))
        high = safe_cast(fin.get("highest_stock_price"))
        stddev = safe_cast(fin.get("stock_price_std_dev"))
        price_z = None
        mid = None

        if stock_price is not None and low is not None and high is not None:
            if low > high:
                logger.warning(f"Price range inverted: low={low} > high={high}")
                low, high = high, low
            
            mid = (low + high) / 2.0
            
            if stddev and stddev > 0:
                price_z = safe_div(stock_price - mid, stddev)
            else:
                range_val = high - low
                if range_val > 0:
                    approx_std = range_val / 4.0
                    price_z = safe_div(stock_price - mid, approx_std)

        resolved_country = infer_country_for_macro(
            country=basic.get("country_of_incorporation") or basic.get("country") or fin.get("country"),
            currency=currency,
            ticker=ticker,
        )

        summary = {
            "company": company,
            "ticker": ticker,
            "currency": currency,
            "profile": {
                "country": resolved_country,
                "website": basic.get("website") or fin.get("website"),
                "industry_us": basic.get("industry_us"),
                "industry_global": basic.get("industry_global"),
                "industry": fin.get("industry") or basic.get("industry_us"),
            },
            "valuation": {
                "market_cap": market_cap,
                "stock_price": stock_price,
                "shares_outstanding": shares_outstanding,
                "price_to_book": round(pb, 3) if pb is not None else None,
            },
            "profitability": {
                "revenue_ttm": revenue_ttm,
                "revenue_ltm": revenue_ltm,
                "operating_income_ttm": op_inc_ttm,
                "operating_income_ltm": op_inc_ltm,
                "operating_margin_ttm_pct": round(op_margin_ttm, 2) if op_margin_ttm is not None else None,
                "operating_margin_ltm_pct": round(op_margin_ltm, 2) if op_margin_ltm is not None else None,
                "effective_tax_rate_pct": round(eff_tax, 2) if eff_tax is not None else None,
                "marginal_tax_rate_pct": round(marg_tax, 2) if marg_tax is not None else None,
                "r_and_d_spend_ttm": r_and_d,
                "r_and_d_pct_of_revenue": round(r_and_d_pct, 2) if r_and_d_pct is not None else None,
            },
            "capital_structure": {
                "book_value_equity": book_equity,
                "book_value_debt": book_debt,
                "cash_and_marketable": cash,
                "minority_interest": minor_int,
                "interest_expense_ttm": interest_ttm,
                "debt_to_equity_pct": round(d_to_e, 2) if d_to_e is not None else None,
            },
            "ownership_and_risk": {
                "held_by_institutions_pct": round(held_inst, 3) if held_inst is not None else None,
                "held_by_insiders_pct": round(held_ins, 3) if held_ins is not None else None,
                "beta": basic.get("beta"),
                "price_zscore": round(price_z, 3) if price_z is not None else None,
            }
        }

        analytics = {}

        if book_debt is not None and book_equity is not None and book_equity > 0:
            analytics["debt_to_equity"] = round(book_debt / book_equity, 4)
        
        if cash is not None and book_debt is not None and book_debt > 0:
            analytics["cash_to_debt"] = round(cash / book_debt, 3)
        
        if op_inc_ttm is not None and interest_ttm is not None and interest_ttm > 0:
            analytics["interest_coverage_ratio"] = round(op_inc_ttm / interest_ttm, 2)
            if op_inc_ttm / interest_ttm < 1:
                logger.warning(f"Interest coverage <1: {op_inc_ttm / interest_ttm:.2f}")

        solvency_score = None
        if book_equity is not None and market_cap is not None and book_debt is not None and op_inc_ttm is not None:
            if book_debt > 0:
                solvency_score = (market_cap + book_equity + op_inc_ttm) / book_debt
                analytics["solvency_score"] = round(float(solvency_score), 3)

        if r_and_d_pct is not None:
            analytics["r_and_d_intensity_pct"] = round(r_and_d_pct, 2)

        if high is not None and low is not None and stock_price is not None:
            range_val = high - low
            if range_val > 0:
                position = (stock_price - low) / range_val * 100.0
                analytics["price_position_pct"] = round(position, 2)

        signals = []
        
        if analytics.get("cash_to_debt") is not None:
            ratio = analytics["cash_to_debt"]
            if ratio > 2:
                signals.append("cash_over_debt_gt_2")
            elif ratio < 0.5:
                signals.append("cash_below_half_debt")

        if analytics.get("interest_coverage_ratio") is not None:
            icr = analytics["interest_coverage_ratio"]
            if icr < 2:
                signals.append("low_interest_coverage")
            elif icr > 10:
                signals.append("strong_interest_coverage")

        if solvency_score is not None:
            if solvency_score > 5:
                signals.append("high_solvency_score")
            elif solvency_score < 1.5:
                signals.append("low_solvency_score")

        insights = []
        
        if revenue_ttm is not None and revenue_ltm is not None and revenue_ltm > 0:
            yoy = (revenue_ttm - revenue_ltm) / revenue_ltm * 100.0
            insights.append(f"Revenue YoY change: {yoy:.1f}% (LTM {revenue_ltm:,.0f} → TTM {revenue_ttm:,.0f}).")

        if op_margin_ttm is not None and op_margin_ltm is not None:
            delta = op_margin_ttm - op_margin_ltm
            if abs(delta) < 0.5:
                insights.append(f"Operating margin stable at ~{op_margin_ttm:.1f}%.")
            else:
                insights.append(f"Operating margin changed by {delta:.1f} p.p. from {op_margin_ltm:.1f}% to {op_margin_ttm:.1f}%.")

        if r_and_d_pct is not None:
            insights.append(f"R&D intensity: {r_and_d_pct:.2f}% of revenue.")

        if d_to_e is not None:
            leverage_desc = "low" if d_to_e < 20 else "moderate" if d_to_e < 50 else "high"
            insights.append(f"Debt-to-equity ~{d_to_e:.1f}% ({leverage_desc} leverage).")

        if market_cap is not None and cash is not None and market_cap > 0:
            cash_pct = cash / market_cap * 100.0
            insights.append(f"Cash is {cash:,.0f}, ~{cash_pct:.2f}% of market capitalization.")

        if held_inst is not None:
            insights.append(f"Institutions hold {held_inst:.2f}% of shares.")

        if pb is not None:
            insights.append(f"P/B ≈ {pb:.2f}.")

        if price_z is not None and mid is not None:
            insights.append(f"Price z-score vs recent range ≈ {price_z:.2f} (mid {mid:.2f}).")

        summary["analytics"] = analytics
        summary["signals"] = signals if signals else None
        summary["insight_text"] = " ".join(insights) if insights else None
        summary["provenance"] = {"version": "fin_pre_v1.1"}
        return deep_clean(summary)

    except Exception as e:
        logger.error(f"Critical error in preprocess_financials_json: {e}", exc_info=True)
        return {
            "company": raw.get("basic_info_data_dto", {}).get("company_name"),
            "ticker": raw.get("basic_info_data_dto", {}).get("ticker"),
            "currency": raw.get("basic_info_data_dto", {}).get("currency"),
            "error": str(e),
            "provenance": {"version": "fin_pre_v1.1", "error": True}
        }


# ============================================================================
# SEGMENT PROCESSING FUNCTIONS
# ============================================================================

def similarity(a: str, b: str) -> float:
    """Simple string similarity (0..1)."""
    if not a or not b:
        return 0.0
    return SequenceMatcher(None, a.lower(), b.lower()).ratio()


def best_mapping_for_segment(
    seg_name: str,
    raw_sector: str,
    current_industry: str,
    mapping: List[Dict[str, Any]],
    industry_boost: float = 0.2
) -> Tuple[Optional[Dict[str, Any]], float, str]:
    """
    Find best mapping match for a given segment with enhanced fuzzy matching.
    """
    seg_name = (seg_name or "").strip()
    raw_sector = (raw_sector or "").strip().lower()
    current_industry = (current_industry or "").strip().lower()
    effective_mapping = mapping
    if current_industry:
        industry_scoped_mapping = [
            entry for entry in mapping
            if str(entry.get("industry", "")).strip().lower() == current_industry
        ]
        if industry_scoped_mapping:
            effective_mapping = industry_scoped_mapping
    
    logger.debug(f"Mapping segment: name='{seg_name}', raw_sector='{raw_sector}', industry='{current_industry}'")

    # Exact sector match with validation
    if raw_sector in SECTOR_TO_MAPPING:
        m = SECTOR_TO_MAPPING[raw_sector]
        if current_industry and str(m.get("industry", "")).strip().lower() != current_industry:
            logger.debug("  ⚠ Exact sector rejected: industry mismatch")
            m = None
        if m is not None:
            name_similarity_to_sector = fuzz.token_sort_ratio(seg_name.lower(), m["name"].lower()) / 100.0

            if not seg_name or name_similarity_to_sector > 0.40:
                score = 1.0
                if current_industry and m["industry"] != current_industry:
                    score -= 0.25
                logger.debug(f"  ✓ Exact sector match: {m['sector']} (score={score:.3f})")
                return m, max(0, score), "exact_sector"
            else:
                logger.debug(f"  ⚠ Exact sector rejected: name mismatch (sim={name_similarity_to_sector:.2f})")

    # Exact name match
    for e in effective_mapping:
        if seg_name.lower() == e["name"].lower():
            score = 0.95 + (industry_boost if e["industry"] == current_industry else 0)
            logger.debug(f"  ✓ Exact name match: {e['name']} → {e['sector']}")
            return e, min(1.0, score), "exact_name"

    # Composite matching
    best_composite = None
    best_composite_score = 0.0
    
    if seg_name and raw_sector:
        for e in effective_mapping:
            name_score = fuzz.token_sort_ratio(seg_name.lower(), e["name"].lower()) / 100.0
            sector_score = fuzz.token_sort_ratio(raw_sector, e["sector"].lower()) / 100.0
            composite_score = (name_score * 0.70) + (sector_score * 0.30)
            
            if current_industry and e["industry"] == current_industry:
                composite_score = min(1.0, composite_score + industry_boost)
            
            if composite_score > best_composite_score and composite_score > 0.40:
                best_composite_score = composite_score
                best_composite = e
        
        if best_composite:
            logger.debug(f"  ✓ Composite match: '{best_composite['name']}' (score={best_composite_score:.3f})")
            return best_composite, best_composite_score, "composite_match"
    
    # Fuzzy name match
    if seg_name:
        name_choices = [e["name"] for e in effective_mapping]
        match = process.extractOne(
            seg_name,
            name_choices,
            scorer=fuzz.token_sort_ratio,
            score_cutoff=45
        )
        if match:
            best_name, fuzzy_score, _ = match
            found = next((e for e in effective_mapping if e["name"] == best_name), None)
            if found:
                final_score = (fuzzy_score / 100.0)
                if current_industry and found["industry"] == current_industry:
                    final_score = min(1.0, final_score + industry_boost)
                logger.debug(f"  ✓ Fuzzy name match: '{best_name}' (score={final_score:.3f})")
                return found, final_score, "fuzzy_name"

    # Fuzzy sector match
    if raw_sector:
        sector_choices = [e["sector"] for e in effective_mapping]
        match = process.extractOne(
            raw_sector,
            sector_choices,
            scorer=fuzz.token_sort_ratio,
            score_cutoff=55
        )
        if match:
            best_sector, fuzzy_score, _ = match
            found = next((e for e in effective_mapping if e["sector"] == best_sector), None)
            if found:
                final_score = (fuzzy_score / 100.0)
                if current_industry and found["industry"] == current_industry:
                    final_score = min(1.0, final_score + industry_boost)
                logger.debug(f"  ⚠ Fuzzy sector match: '{best_sector}' (score={final_score:.3f})")
                return found, final_score, "fuzzy_sector_only"

    # Legacy similarity fallback
    best = None
    best_score = 0.0
    best_field = ""
    for e in effective_mapping:
        s_name = similarity(seg_name, e["name"]) if seg_name else 0
        s_sector = similarity(raw_sector, e["sector"]) if raw_sector else 0
        s = (s_name * 0.75) + (s_sector * 0.25)
        
        if current_industry and e["industry"] == current_industry:
            s += industry_boost
        if s > best_score:
            best_score = s
            best = e
            best_field = "name" if s_name >= s_sector else "sector"
    
    if best and best_score > 0.25:
        logger.debug(f"  ✓ Legacy match: {best[best_field]} → {best['sector']}")
        return best, min(1.0, best_score), f"legacy_{best_field}"
    
    logger.warning(f"  ✗ No match found for: name='{seg_name}', sector='{raw_sector}'")
    return None, 0.0, "no_match"


def normalize_revenue_shares(segments: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Normalize revenue shares so they sum to 1.0."""
    total = sum(float(s.get("revenue_share") or 0) for s in segments)
    if total <= 0:
        for s in segments:
            s["revenue_share"] = 1.0 / len(segments)
    else:
        for s in segments:
            s["revenue_share"] = float(s.get("revenue_share") or 0) / total
    diff = 1.0 - sum(s["revenue_share"] for s in segments)
    if segments:
        segments[0]["revenue_share"] += diff
    return segments


def postprocess_segments(
    raw_data: Dict[str, Any],
    current_industry: str,
    mapping: List[Dict[str, Any]],
    confidence_threshold: float = 0.5,
) -> Dict[str, Any]:
    """
    Validate, remap, and group segment data with enhanced logging.
    """
    current_industry = (current_industry or "").strip().lower()
    raw_segments = raw_data.get("segments", [])
    logger.info(f"Processing {len(raw_segments)} raw segments for industry '{current_industry}'")
    
    prepared = []
    mapping_stats = {
        "exact_sector": 0,
        "exact_name": 0,
        "fuzzy_sector": 0,
        "fuzzy_name": 0,
        "no_match": 0
    }

    for idx, seg in enumerate(raw_segments):
        name = seg.get("name") or seg.get("segment") or seg.get("industry_lookup") or "UNKNOWN"
        rev = float(seg.get("revenue_share") or seg.get("share") or 0.0)
        margin = seg.get("operating_margin")
        raw_sector = seg.get("sector") or seg.get("industry_lookup")

        logger.debug(f"  Segment {idx+1}/{len(raw_segments)}: '{name}' (raw_sector='{raw_sector}', rev_share={rev:.2%})")
        
        m_entry, score, reason = best_mapping_for_segment(name, raw_sector, current_industry, mapping)
        sector = m_entry["sector"] if m_entry else "UNKNOWN"
        industry = m_entry["industry"] if m_entry else None
        low_conf = (score < confidence_threshold) or (current_industry and industry != current_industry)

        mapping_stats[reason] = mapping_stats.get(reason, 0) + 1

        prepared.append({
            "name": name,
            "revenue_share": rev,
            "operating_margin": margin,
            "sector": sector,
            "industry": industry,
            "mapping_score": round(score, 4),
            "mapping_reason": reason,
            "low_confidence": low_conf
        })
        
        if low_conf:
            logger.warning(f"    ⚠ Low confidence: '{name}' → '{sector}' (score={score:.3f}, reason={reason})")
        else:
            logger.debug(f"    ✓ Mapped: '{name}' → '{sector}' (score={score:.3f}, reason={reason})")

    logger.info(f"Segment mapping results: {dict(mapping_stats)}")
    
    if any(p["low_confidence"] for p in prepared):
        low_conf_segments = [(p["name"], p["sector"], p["mapping_score"]) for p in prepared if p["low_confidence"]]
        logger.warning(f"Found {len(low_conf_segments)} low-confidence mappings: {low_conf_segments}")

    prepared = normalize_revenue_shares(prepared)

    grouped = defaultdict(lambda: {"rev": 0, "margin_sum": 0, "components": [], "industry": None, "scores": []})
    for p in prepared:
        sec = p["sector"]
        grouped[sec]["rev"] += p["revenue_share"]
        if p["operating_margin"] is not None:
            grouped[sec]["margin_sum"] += float(p["operating_margin"]) * p["revenue_share"]
        grouped[sec]["components"].append(p["name"])
        grouped[sec]["industry"] = p["industry"]
        grouped[sec]["scores"].append(p["mapping_score"])

    results = []
    for sec, vals in grouped.items():
        rev = vals["rev"]
        margin = vals["margin_sum"] / rev if rev > 0 else None
        avg_score = sum(vals["scores"]) / len(vals["scores"])
        results.append({
            "sector": sec,
            "industry": vals["industry"],
            "revenue_share": round(rev, 4),
            "operating_margin": round(margin, 4) if margin is not None else None,
            "mapping_score": round(avg_score, 3),
            "components": vals["components"],
        })

    total = sum(r["revenue_share"] for r in results)
    if abs(total - 1.0) > 1e-6:
        for r in results:
            r["revenue_share"] = round(r["revenue_share"] / total, 4)

    return {"segments": results}


def safe_json_loads(raw: str) -> Optional[Any]:
    """
    Extract and parse the first valid JSON object or array from raw text.
    """
    if not raw or not isinstance(raw, str):
        return None

    raw = re.sub(r"^```(?:json)?\s*", "", raw.strip(), flags=re.IGNORECASE)
    raw = re.sub(r"\s*```$", "", raw)

    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        pass

    openers = {'{': '}', '[': ']'}
    stack = []
    start_index = None

    for i, ch in enumerate(raw):
        if ch in openers:
            if not stack:
                start_index = i
            stack.append(openers[ch])
        elif ch in openers.values() and stack:
            if ch == stack[-1]:
                stack.pop()
                if not stack and start_index is not None:
                    candidate = raw[start_index:i+1]
                    try:
                        return json.loads(candidate)
                    except json.JSONDecodeError:
                        continue
    return None
