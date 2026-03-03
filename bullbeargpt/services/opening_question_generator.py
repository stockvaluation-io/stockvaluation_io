"""
Opening Question Generator for AI Chat
Generates proactive, provocative opening questions based on valuation analysis
"""
import logging
from typing import Dict, Any, Optional, List, Tuple
import math

logger = logging.getLogger(__name__)


def _extract_fair_value(valuation_data: Dict[str, Any]) -> float:
    """Extract fair value from valuation_data with multiple fallback locations."""
    # PRIMARY: Java backend response structure (companyDTO)
    company_dto = valuation_data.get('companyDTO', {})
    
    dcf_analysis = (
        valuation_data.get('dcf_analysis') or
        valuation_data.get('analyst', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('valuation_analysis') or
        {}
    )
    
    return (
        company_dto.get('estimatedValuePerShare') or
        dcf_analysis.get('fair_value') or
        dcf_analysis.get('intrinsic_value') or
        valuation_data.get('dcf', {}).get('derived', {}).get('estimated_value_per_share') or
        valuation_data.get('dcf', {}).get('derived', {}).get('fair_value') or
        valuation_data.get('dcf', {}).get('derived', {}).get('intrinsic_value') or
        valuation_data.get('financials', {}).get('valuation', {}).get('fair_value') or
        valuation_data.get('financials', {}).get('valuation', {}).get('intrinsic_value') or
        0
    )


def _extract_current_price(valuation_data: Dict[str, Any]) -> float:
    """Extract current price from valuation_data with multiple fallback locations."""
    # PRIMARY: Java backend response structure (companyDTO.price)
    company_dto = valuation_data.get('companyDTO', {})
    
    financials = (
        valuation_data.get('financials') or
        valuation_data.get('analyst', {}).get('financials') or
        valuation_data.get('dcf', {}).get('financials') or
        {}
    )
    
    return (
        company_dto.get('price') or
        financials.get('current_price') or
        financials.get('stock_price') or
        financials.get('valuation', {}).get('current_price') or
        financials.get('valuation', {}).get('stock_price') or
        financials.get('valuation', {}).get('price') or
        financials.get('profile', {}).get('price') or
        0
    )


def _extract_dcf_analysis(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract DCF analysis from valuation_data with multiple fallback locations."""
    dcf_analysis = (
        valuation_data.get('dcf_analysis') or
        valuation_data.get('analyst', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('valuation_analysis') or
        valuation_data.get('dcf') or
        {}
    )
    
    # If we got the dcf dict, extract assumptions from it
    if 'dcf' in valuation_data and not dcf_analysis.get('assumptions'):
        dcf = valuation_data.get('dcf', {})
        # Try to build assumptions from dcf structure
        derived = dcf.get('derived', {})
        terminal = dcf.get('terminal_assumptions', {})
        
        if derived or terminal:
            assumptions = {}
            if 'revenue_cagr_pct' in derived:
                assumptions['revenue_growth_rate'] = derived.get('revenue_cagr_pct')
            if 'wacc_end_pct' in derived:
                assumptions['wacc'] = derived.get('wacc_end_pct')
            if terminal:
                assumptions['terminal_growth_rate'] = terminal.get('terminal_growth_rate_pct')
            
            if assumptions:
                dcf_analysis = dcf_analysis.copy() if dcf_analysis else {}
                dcf_analysis['assumptions'] = assumptions
    
    return dcf_analysis


def _extract_narratives(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract narratives from valuation_data with multiple fallback locations."""
    # Try analyst key first (contains risks, growth, margins, etc.)
    analyst_output = valuation_data.get('analyst', {})
    if analyst_output:
        narratives = {
            'risks': analyst_output.get('risks', {}),
            'growth': analyst_output.get('growth', {}),
            'margins': analyst_output.get('margins', {}),
            'key_takeaways': analyst_output.get('key_takeaways', {}),
            'investment_efficiency': analyst_output.get('investment_efficiency', {})
        }
        if any(narratives.values()):
            return narratives
    
    # Try merged_result.valuation_analysis
    merged_analysis = valuation_data.get('merged_result', {}).get('valuation_analysis', {})
    if merged_analysis:
        narratives = {
            'risks': merged_analysis.get('risks', {}),
            'growth': merged_analysis.get('growth', {}),
            'margins': merged_analysis.get('margins', {}),
            'key_takeaways': merged_analysis.get('key_takeaways', {})
        }
        if any(narratives.values()):
            return narratives
    
    # Try root narratives
    root_narratives = valuation_data.get('narratives', {})
    if root_narratives:
        return root_narratives
    
    # Try extracting from debate/bull_bear_debate
    debate = valuation_data.get('debate', {})
    bull_bear = debate.get('bull_bear_debate', []) if debate else []
    merged_debate = valuation_data.get('merged_result', {}).get('bull_bear_debate', [])
    
    if bull_bear or merged_debate:
        narratives = {}
        # Extract bull/bear from debate rounds
        debate_data = bull_bear or merged_debate
        if debate_data and len(debate_data) > 0:
            first_round = debate_data[0] if isinstance(debate_data, list) else debate_data
            if isinstance(first_round, dict):
                narratives['bull_case'] = first_round.get('bull', '')
                narratives['bear_case'] = first_round.get('bear', '')
        
        if narratives:
            return narratives
    
    return {}


def _extract_risk_assessment(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract risk assessment from valuation_data with multiple fallback locations."""
    # Try analyst.risks
    analyst_risks = valuation_data.get('analyst', {}).get('risks', {})
    if analyst_risks:
        risk_narrative = analyst_risks.get('narrative', '') if isinstance(analyst_risks, dict) else ''
        if risk_narrative:
            return {
                'key_risks': [risk_narrative] if risk_narrative else [],
                'narrative': risk_narrative
            }
    
    # Try merged_result.valuation_analysis.risks
    merged_risks = valuation_data.get('merged_result', {}).get('valuation_analysis', {}).get('risks', {})
    if merged_risks:
        risk_narrative = merged_risks.get('narrative', '') if isinstance(merged_risks, dict) else ''
        if risk_narrative:
            return {
                'key_risks': [risk_narrative] if risk_narrative else [],
                'narrative': risk_narrative
            }
    
    # Try root risk_assessment
    root_risk = valuation_data.get('risk_assessment', {})
    if root_risk:
        return root_risk
    
    # Try news.valuation_drivers.risk
    news = valuation_data.get('news', {})
    if news and isinstance(news, dict):
        drivers = news.get('valuation_drivers', {})
        if drivers:
            risk_text = drivers.get('risk', '')
            if risk_text:
                return {
                    'key_risks': [risk_text],
                    'narrative': risk_text
                }
    
    return {}


def _extract_sensitivity(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract sensitivity analysis from valuation_data with multiple fallback locations."""
    return (
        valuation_data.get('sensitivity') or
        valuation_data.get('merged_result', {}).get('sensitivity') or
        {}
    )


def _extract_market_data(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract market data from valuation_data with multiple fallback locations."""
    return (
        valuation_data.get('market_data') or
        valuation_data.get('financials', {}).get('analytics', {}) or
        {}
    )


def _extract_scenarios(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """Extract scenario analysis from valuation_data with multiple fallback locations."""
    return (
        valuation_data.get('scenario') or
        valuation_data.get('merged_result', {}).get('scenario_analysis') or
        {}
    )


def _extract_fair_value_currency(valuation_data: Dict[str, Any]) -> Optional[str]:
    """Currency used for fair value / valuation outputs."""
    return (
        valuation_data.get('currency') or
        valuation_data.get('java_valuation_output', {}).get('currency') or
        valuation_data.get('financials', {}).get('currency') or
        valuation_data.get('companyDTO', {}).get('currency') or
        valuation_data.get('financials', {}).get('profile', {}).get('currency') or
        valuation_data.get('dcf', {}).get('currency')
    )


def _extract_market_price_currency(valuation_data: Dict[str, Any]) -> Optional[str]:
    """Currency used for current market quote / stock price."""
    return (
        valuation_data.get('stockCurrency') or
        valuation_data.get('java_valuation_output', {}).get('stockCurrency') or
        valuation_data.get('companyDTO', {}).get('stockCurrency') or
        valuation_data.get('java_valuation_output', {}).get('companyDTO', {}).get('stockCurrency') or
        valuation_data.get('market_data', {}).get('currency')
    )


def _extract_currency(valuation_data: Dict[str, Any]) -> Optional[str]:
    """
    Backward-compatible currency extractor.
    Prefer fair value currency.
    """
    return _extract_fair_value_currency(valuation_data)


def _format_price(value: float, currency: Optional[str]) -> str:
    """Format a price value with the currency code."""
    return f"{value:.2f} {currency}" if currency else f"{value:.2f}"


def _format_price_short(value: float, currency: Optional[str]) -> str:
    """Format a price value with currency code, shorter format for large numbers."""
    return f"{value:.0f} {currency}" if currency else f"{value:.0f}"


def _to_float(value: Any) -> Optional[float]:
    """Best-effort conversion of mixed JSON values to float."""
    if value is None:
        return None

    if isinstance(value, bool):
        return None

    if isinstance(value, (int, float)):
        if math.isfinite(float(value)):
            return float(value)
        return None

    if isinstance(value, str):
        cleaned = value.strip().replace('%', '')
        if not cleaned:
            return None
        try:
            parsed = float(cleaned)
            if math.isfinite(parsed):
                return parsed
        except (TypeError, ValueError):
            return None
        return None

    if isinstance(value, (list, tuple)):
        # Prefer the last finite value for time-series style arrays.
        for item in reversed(value):
            parsed = _to_float(item)
            if parsed is not None:
                return parsed
        return None

    return None


def _extract_assumption_value(payload: Dict[str, Any], keys: List[str]) -> Optional[float]:
    """Extract the first finite assumption value found across known payload locations."""
    containers: List[Dict[str, Any]] = [
        payload.get('dcf_analysis', {}) or {},
        payload.get('merged_result', {}).get('dcf_analysis', {}) or {},
        payload.get('merged_result', {}).get('valuation_analysis', {}) or {},
        payload.get('dcf', {}) or {},
    ]

    expanded: List[Dict[str, Any]] = []
    for container in containers:
        expanded.append(container)
        if isinstance(container, dict):
            expanded.append(container.get('assumptions', {}) or {})
            expanded.append(container.get('proposed_assumptions', {}) or {})
            expanded.append(container.get('baseline_assumptions', {}) or {})
            expanded.append(container.get('key_assumptions', {}) or {})
            expanded.append(container.get('derived', {}) or {})

    expanded.extend([
        payload.get('valuation_metadata', {}).get('java_overrides', {}) or {},
        payload.get('java_valuation_output', {}).get('financialDTO', {}) or {},
        payload.get('companyDTO', {}) or {},
        payload.get('java_valuation_output', {}).get('companyDTO', {}) or {},
    ])

    for container in expanded:
        if not isinstance(container, dict):
            continue
        for key in keys:
            if key in container:
                parsed = _to_float(container.get(key))
                if parsed is not None:
                    return parsed
    return None


def _extract_growth_and_wacc(payload: Dict[str, Any]) -> Tuple[Optional[float], Optional[float]]:
    """Extract growth and discount-rate assumptions from varied valuation payload shapes."""
    growth = _extract_assumption_value(payload, [
        'revenue_growth_rate',
        'revenue_growth',
        'revenue_cagr',
        'growth_rate',
        'compoundAnnualGrowth2_5',
        'revenue_cagr_pct',
    ])

    wacc = _extract_assumption_value(payload, [
        'wacc',
        'discount_rate',
        'cost_of_capital',
        'costOfCapital',
        'initialCostCapital',
        'wacc_end_pct',
        'terminalCostOfCapital',
    ])

    return growth, wacc


def _format_percent(value: Optional[float]) -> str:
    if value is None:
        return "N/A"
    return f"{value:.2f}"


def generate_opening_question(
    ticker: str,
    valuation_data: Dict[str, Any],
    company_name: Optional[str] = None
) -> str:
    """
    Generates a provocative opening question based on valuation analysis.
    
    Instead of generic "Hi! How can I help?", this creates a specific question that:
    1. Acknowledges the valuation
    2. Surfaces the key tension
    3. Asks user to take a stance
    
    Args:
        ticker: Stock ticker symbol
        valuation_data: Complete valuation data from analysis
        company_name: Company name (optional)
        
    Returns:
        Opening message string with specific question
    """
    try:
        # Extract data using helper functions with multiple fallback locations
        dcf_analysis = _extract_dcf_analysis(valuation_data)
        narratives = _extract_narratives(valuation_data)
        sensitivity = _extract_sensitivity(valuation_data)
        market_data = _extract_market_data(valuation_data)
        
        # Get key metrics
        fair_value = _extract_fair_value(valuation_data)
        current_price = _extract_current_price(valuation_data)
        
        if current_price and fair_value:
            upside_pct = ((fair_value - current_price) / current_price) * 100
        else:
            upside_pct = 0
        
        # Identify tensions
        tensions = _identify_tensions(
            ticker=ticker,
            upside_pct=upside_pct,
            fair_value=fair_value,
            current_price=current_price,
            dcf_analysis=dcf_analysis,
            narratives=narratives,
            market_data=market_data,
            sensitivity=sensitivity,
            valuation_data=valuation_data,
        )
        
        if not tensions:
            # Fallback to basic question
            return _generate_fallback_question(ticker, fair_value, current_price, upside_pct)
        
        # Pick the most interesting tension
        primary_tension = max(tensions, key=lambda x: x['score'])
        
        # Craft question based on tension type
        question = _craft_question(
            ticker=ticker,
            tension=primary_tension,
            fair_value=fair_value,
            current_price=current_price,
            upside_pct=upside_pct,
            dcf_analysis=dcf_analysis,
            narratives=narratives,
            market_data=market_data,
            company_name=company_name,
            valuation_data=valuation_data  # Pass valuation_data for currency extraction
        )
        
        return question
        
    except Exception as e:
        logger.error(f"Error generating opening question: {e}", exc_info=True)
        return _generate_fallback_question(ticker, 0, 0, 0, {})


def _identify_tensions(
    ticker: str,
    upside_pct: float,
    fair_value: float,
    current_price: float,
    dcf_analysis: Dict[str, Any],
    narratives: Dict[str, Any],
    market_data: Dict[str, Any],
    sensitivity: Dict[str, Any],
    valuation_data: Optional[Dict[str, Any]] = None
) -> List[Dict[str, Any]]:
    """
    Identify interesting tensions in the valuation that make for good opening questions.
    """
    tensions = []
    
    # TENSION 1: Large Valuation Gap (>15%)
    if abs(upside_pct) > 15:
        direction = "undervalued" if upside_pct > 0 else "overvalued"
        score = min(abs(upside_pct) / 10, 10)  # Higher gap = higher score
        
        tensions.append({
            'type': 'valuation_gap',
            'score': score,
            'description': f"Market prices {ticker} {abs(upside_pct):.0f}% {'below' if direction=='undervalued' else 'above'} fair value",
            'direction': direction,
            'gap_pct': abs(upside_pct)
        })
    
    # TENSION 2: Wide Bull/Bear Spread (high uncertainty)
    bull_value = dcf_analysis.get('bull_case_value', 0)
    bear_value = dcf_analysis.get('bear_case_value', 0)
    
    if bull_value and bear_value and bear_value > 0:
        spread_pct = ((bull_value - bear_value) / bear_value) * 100
        
        if spread_pct > 50:
            score = min(spread_pct / 20, 10)
            tensions.append({
                'type': 'outcome_uncertainty',
                'score': score,
                'description': f"Wide outcome range: {spread_pct:.0f}% between bull and bear",
                'bull_value': bull_value,
                'bear_value': bear_value,
                'spread_pct': spread_pct
            })
    
    # TENSION 3: Aggressive Growth Assumption
    assumptions = dcf_analysis.get('assumptions', {})
    revenue_growth = _to_float(assumptions.get('revenue_growth_rate'))
    if revenue_growth is None:
        revenue_growth, _ = _extract_growth_and_wacc(valuation_data or {})
    revenue_growth = revenue_growth or 0
    
    if revenue_growth > 20:
        score = min(revenue_growth / 5, 8)
        tensions.append({
            'type': 'aggressive_assumption',
            'score': score,
            'description': f"{revenue_growth:.0f}% revenue growth is aggressive",
            'growth_rate': revenue_growth
        })
    
    # TENSION 4: Contrarian Opportunity (price down but fundamentals strong)
    recent_performance = market_data.get('recent_performance', {})
    three_month_return = recent_performance.get('3month', recent_performance.get('3_month', 0))
    
    if three_month_return < -15 and upside_pct > 10:
        score = 9  # Contrarian setups are interesting
        tensions.append({
            'type': 'contrarian_opportunity',
            'score': score,
            'description': f"Stock down {abs(three_month_return):.0f}% but fundamentals suggest {upside_pct:.0f}% upside",
            'recent_decline': abs(three_month_return)
        })
    
    # TENSION 5: High Sensitivity to Key Assumption
    if sensitivity:
        # Check if small changes in key assumptions lead to large value changes
        wacc_sensitivity = 0
        if 'wacc_up' in sensitivity and 'wacc_down' in sensitivity:
            wacc_range = abs(sensitivity['wacc_up'] - sensitivity['wacc_down'])
            if current_price:
                wacc_sensitivity = (wacc_range / current_price) * 100
        
        if wacc_sensitivity > 30:
            score = 7
            tensions.append({
                'type': 'high_sensitivity',
                'score': score,
                'description': f"Valuation highly sensitive to discount rate assumptions",
                'sensitivity_pct': wacc_sensitivity
            })
    
    # TENSION 6: Market Disagreement on Key Narrative
    risks = narratives.get('risks', narratives.get('key_risks', []))
    if isinstance(risks, list) and len(risks) > 0:
        # Check if first risk is regulatory or competitive
        first_risk = str(risks[0]).lower() if risks else ""
        if any(keyword in first_risk for keyword in ['regulatory', 'competition', 'disruption', 'antitrust']):
            score = 7
            tensions.append({
                'type': 'existential_risk',
                'score': score,
                'description': "Key existential risk could reshape the thesis",
                'risk': risks[0] if risks else ""
            })
    
    return tensions


def _craft_question(
    ticker: str,
    tension: Dict[str, Any],
    fair_value: float,
    current_price: float,
    upside_pct: float,
    dcf_analysis: Dict[str, Any],
    narratives: Dict[str, Any],
    market_data: Dict[str, Any],
    company_name: Optional[str] = None,
    valuation_data: Optional[Dict[str, Any]] = None
) -> str:
    """
    Crafts the actual opening question based on tension type.
    """
    company = company_name or ticker
    tension_type = tension['type']
    
    fair_currency = _extract_fair_value_currency(valuation_data or {})
    market_currency = _extract_market_price_currency(valuation_data or {})
    price_currency = market_currency or fair_currency
    growth_assumption, wacc_assumption = _extract_growth_and_wacc(valuation_data or {})
    growth_text = _format_percent(growth_assumption)
    wacc_text = _format_percent(wacc_assumption)
    currency_notice = ""
    if fair_currency and market_currency and fair_currency != market_currency:
        currency_notice = (
            f"\n\n**Currency note:** Fair value is in {fair_currency} while current market price is in {market_currency}."
        )
    
    if tension_type == 'valuation_gap':
        direction = "upside" if upside_pct > 0 else "downside"
        
        return f"""I just valued {company} at **{_format_price(fair_value, fair_currency)}**, which implies **{abs(upside_pct):.0f}% {direction}** from the current price of {_format_price(current_price, price_currency)}.

That's a significant disagreement with the market. Here's the tension:

**My model assumes:**
• Revenue growth: {growth_text}%
• Discount rate (WACC): {wacc_text}%

**The market is pricing in:** {_infer_market_view(upside_pct, narratives)}

**So here's the key question:** Is the market wrong, or am I missing something fundamental about {company}'s future?
{currency_notice}
What's your take—are you bullish or bearish on this setup?"""

    elif tension_type == 'outcome_uncertainty':
        bull_value = tension['bull_value']
        bear_value = tension['bear_value']
        
        bull_case = _summarize_narrative(narratives.get('bull_case', ''))
        bear_case = _summarize_narrative(narratives.get('bear_case', ''))
        base_case = _summarize_narrative(narratives.get('base_case', ''))
        
        return f"""I see **three divergent paths** for {company}:

**Bull Case:** {bull_case}
   → Fair Value: **{_format_price_short(bull_value, fair_currency)}** ({((bull_value - current_price) / current_price * 100):+.0f}% from here)

**Base Case:** {base_case}
   → Fair Value: **{_format_price_short(fair_value, fair_currency)}** ({upside_pct:+.0f}% from here)

**Bear Case:** {bear_case}
   → Fair Value: **{_format_price_short(bear_value, fair_currency)}** ({((bear_value - current_price) / current_price * 100):+.0f}% from here)

That's a **{_format_price_short(bull_value - bear_value, fair_currency)} spread** between bull and bear—significant uncertainty.
{currency_notice}

**Which scenario feels most likely to you, and why?** What would need to happen for the bull case to play out?"""

    elif tension_type == 'aggressive_assumption':
        growth_rate = tension['growth_rate']
        assumptions = dcf_analysis.get('assumptions', {})
        
        # Get historical context if available
        historical_growth = market_data.get('historical_growth', {}).get('5yr_cagr', 'N/A')
        comparison = ""
        if historical_growth != 'N/A':
            comparison = f" (vs. {historical_growth}% historical 5-yr CAGR)"
        
        return f"""Here's what I'm wrestling with on {company}:

I'm modeling **{growth_rate:.0f}% revenue growth**{comparison}—which is **{_characterize_growth(growth_rate)}**.

**Why I think it's achievable:**
{_extract_growth_justification(narratives)}

**But here's the risk:**
If growth comes in at just {growth_rate - 5:.0f}% instead, fair value drops to **{_estimate_lower_value(fair_value, 5)}**.

**Does this growth rate feel realistic to you?** Am I being too optimistic, or is there a compelling story here?"""

    elif tension_type == 'contrarian_opportunity':
        recent_decline = tension['recent_decline']
        
        return f"""{company} is down **{recent_decline:.0f}% in the last 3 months**—the market is clearly worried about something.

**But when I look at the fundamentals:**
{_extract_contrarian_case(narratives, dcf_analysis)}

This creates two possible interpretations:

1. **Buying opportunity** → Market overreacted to short-term noise, fundamentals intact
2. **Value trap** → Market sees deterioration that isn't in the numbers yet

At {_format_price(current_price, price_currency)} vs. my {_format_price(fair_value, fair_currency)} fair value ({upside_pct:+.0f}%), there's potential upside **if** the market is wrong.
{currency_notice}

**What's your instinct—is this a dip to buy or a falling knife?**"""

    elif tension_type == 'high_sensitivity':
        wacc = wacc_text
        
        return f"""Here's what makes {company} interesting—and risky:

The valuation is **highly sensitive** to discount rate assumptions. Small changes = big value swings:

• At {wacc}% WACC (my base): **{_format_price_short(fair_value, fair_currency)}**
• If WACC is 0.5% higher: **{_format_price_short(dcf_analysis.get('sensitivity', {}).get('wacc_up', 0), fair_currency) if dcf_analysis.get('sensitivity', {}).get('wacc_up') else 'N/A'}**
• If WACC is 0.5% lower: **{_format_price_short(dcf_analysis.get('sensitivity', {}).get('wacc_down', 0), fair_currency) if dcf_analysis.get('sensitivity', {}).get('wacc_down') else 'N/A'}**

This matters because {_explain_wacc_sensitivity(ticker, wacc)}
{currency_notice}

**How confident are you in the discount rate?** Is {wacc}% the right WACC for {company}, or would you adjust it?"""

    elif tension_type == 'existential_risk':
        risk = tension.get('risk', 'key business risk')
        
        return f"""I valued {company} at **{_format_price(fair_value, fair_currency)}** ({upside_pct:+.0f}% upside), but there's an **elephant in the room**:

**{risk}**

This isn't a normal risk—it's the kind that could **reshape the entire thesis**. 

**My model assumes:** This risk is manageable / unlikely to materialize
**The market might be thinking:** This is a real threat that erodes the moat

{_extract_risk_context(narratives)}
{currency_notice}

**How much probability would you assign to this risk actually impacting the business?** Does it make you avoid the stock entirely, or is it priced in?"""

    else:
        return _generate_fallback_question(ticker, fair_value, current_price, upside_pct, valuation_data)


def _generate_fallback_question(ticker: str, fair_value: float, current_price: float, upside_pct: float, valuation_data: Optional[Dict[str, Any]] = None) -> str:
    """Generate a generic but still engaging opening question."""
    fair_currency = _extract_fair_value_currency(valuation_data or {})
    market_currency = _extract_market_price_currency(valuation_data or {}) or fair_currency
    currency_notice = ""
    if fair_currency and market_currency and fair_currency != market_currency:
        currency_notice = f"\n**Currency note:** Fair value is in {fair_currency}, current market price is in {market_currency}."
    if fair_value and current_price:
        return f"""I just completed a DCF valuation for **{ticker}**.

**Fair Value:** {_format_price(fair_value, fair_currency)}
**Current Price:** {_format_price(current_price, market_currency)}
**Implied Return:** {upside_pct:+.1f}%
{currency_notice}

I've built a full model with assumptions on growth, margins, and discount rates—plus bull/bear scenarios.

**What aspect of the valuation would you like to dig into first?** The assumptions, the scenarios, or the key risks?"""
    else:
        return f"""I've completed a comprehensive valuation analysis for **{ticker}**, including DCF modeling, scenario analysis, and risk assessment.

**What would you like to explore?** We can dig into the assumptions, test different scenarios, or discuss what could go right or wrong with this investment."""


def _infer_market_view(upside_pct: float, narratives: Dict[str, Any]) -> str:
    """Infer what the market is pricing in based on valuation gap."""
    if upside_pct > 15:
        # Market is pessimistic
        bear_case = narratives.get('bear_case', '')
        if bear_case:
            return f"Something closer to the bear case: {_summarize_narrative(bear_case)}"
        return "slower growth, higher risks, or compressed multiples"
    elif upside_pct < -15:
        # Market is optimistic
        bull_case = narratives.get('bull_case', '')
        if bull_case:
            return f"Something closer to the bull case: {_summarize_narrative(bull_case)}"
        return "accelerating growth, margin expansion, or multiple re-rating"
    return "roughly in line with base case expectations"


def _summarize_narrative(narrative: Any, max_length: int = 120) -> str:
    """Summarize a narrative to key points."""
    if isinstance(narrative, str):
        if len(narrative) <= max_length:
            return narrative
        # Take first sentence or truncate intelligently
        sentences = narrative.split('.')
        if sentences and len(sentences[0]) <= max_length:
            return sentences[0].strip()
        return narrative[:max_length].strip() + "..."
    elif isinstance(narrative, dict):
        return narrative.get('summary', narrative.get('key_points', [''])[0] if narrative.get('key_points') else '')[:max_length]
    return "Analysis pending"


def _characterize_growth(growth_rate: float) -> str:
    """Characterize how aggressive a growth rate is."""
    if growth_rate > 30:
        return "extremely aggressive"
    elif growth_rate > 20:
        return "quite aggressive"
    elif growth_rate > 15:
        return "above-average growth"
    elif growth_rate > 10:
        return "solid growth"
    else:
        return "moderate growth"


def _extract_growth_justification(narratives: Dict[str, Any]) -> str:
    """Extract growth justification from narratives."""
    bull_case = narratives.get('bull_case', '')
    base_case = narratives.get('base_case', '')
    
    # Look for growth drivers in bull case
    if isinstance(bull_case, str) and len(bull_case) > 20:
        summary = _summarize_narrative(bull_case, 200)
        return f"• {summary}"
    elif isinstance(base_case, str) and len(base_case) > 20:
        summary = _summarize_narrative(base_case, 200)
        return f"• {summary}"
    
    return "• Strong market position and expanding TAM support continued growth"


def _extract_contrarian_case(narratives: Dict[str, Any], dcf_analysis: Dict[str, Any]) -> str:
    """Extract why fundamentals might be strong despite price decline."""
    base_case = narratives.get('base_case', '')
    
    if isinstance(base_case, str) and len(base_case) > 20:
        return _summarize_narrative(base_case, 200)
    
    # Fallback to DCF assumptions
    assumptions = dcf_analysis.get('assumptions', {})
    growth = assumptions.get('revenue_growth_rate', 'N/A')
    margin = assumptions.get('operating_margin', 'N/A')
    
    return f"Revenue growth of {growth}%, operating margins of {margin}%, and solid free cash flow generation suggest the business remains healthy."


def _extract_risk_context(narratives: Dict[str, Any]) -> str:
    """Extract context around a key risk."""
    bear_case = narratives.get('bear_case', '')
    
    if isinstance(bear_case, str) and len(bear_case) > 20:
        return f"**Bear case context:** {_summarize_narrative(bear_case, 200)}"
    
    return "**This risk could significantly impact the investment thesis if it materializes.**"


def _explain_wacc_sensitivity(ticker: str, wacc: Any) -> str:
    """Explain why WACC sensitivity matters for this company."""
    try:
        wacc_val = float(wacc)
        if wacc_val > 12:
            return f"{ticker} has a relatively high cost of capital, making it more sensitive to rate changes"
        elif wacc_val > 9:
            return f"with a WACC around {wacc}%, valuation is moderately sensitive to discount rate changes"
        else:
            return f"even with a relatively low WACC of {wacc}%, small rate changes matter for long-duration cash flows"
    except:
        return "the discount rate assumption significantly impacts present value of future cash flows"


def _estimate_lower_value(fair_value: float, growth_reduction: float) -> str:
    """Estimate lower fair value if growth is reduced."""
    # Rough estimate: 5% growth reduction = ~15% value impact
    estimated_reduction = (growth_reduction / 5) * 0.15
    lower_value = fair_value * (1 - estimated_reduction)
    return f"${lower_value:.0f}"


def generate_opening_suggestions(
    ticker: str,
    valuation_data: Dict[str, Any],
    company_name: Optional[str] = None,
    num_suggestions: int = 4
) -> Dict[str, Any]:
    """
    Generate multiple clickable question suggestions (Supabase-style).
    
    Returns a primary question plus 3-4 contextual suggestion chips that users
    can click to start a conversation without typing.
    
    Args:
        ticker: Stock ticker symbol
        valuation_data: Complete valuation data from analysis
        company_name: Company name (optional)
        num_suggestions: Number of suggestion chips to generate (default 4)
        
    Returns:
        {
            "primary_question": "Main proactive question",
            "suggestions": [
                {"icon": "", "text": "Question text", "category": "scenario"},
                ...
            ]
        }
    """
    try:
        # Extract data using helper functions with multiple fallback locations
        dcf_analysis = _extract_dcf_analysis(valuation_data)
        narratives = _extract_narratives(valuation_data)
        risk_assessment = _extract_risk_assessment(valuation_data)
        scenarios = _extract_scenarios(valuation_data)
        
        # Get key metrics
        fair_value = _extract_fair_value(valuation_data)
        current_price = _extract_current_price(valuation_data)
        
        if current_price and fair_value:
            upside_pct = ((fair_value - current_price) / current_price) * 100
        else:
            upside_pct = 0
        
        # Generate primary question (use existing function)
        primary_question = generate_opening_question(ticker, valuation_data, company_name)

        # Generate contextual suggestions
        suggestions = []
        
        # 1. Valuation Gap Question
        if abs(upside_pct) > 5:
            if upside_pct > 0:
                suggestions.append({
                    "icon": "",
                    "text": f"Why is {ticker} undervalued by {abs(upside_pct):.0f}%?",
                    "category": "valuation"
                })
            else:
                suggestions.append({
                    "icon": "",
                    "text": f"Why is {ticker} overvalued by {abs(upside_pct):.0f}%?",
                    "category": "valuation"
                })
        
        # 2. Scenario Analysis Question
        bull_case = narratives.get('bull_case') or narratives.get('optimistic_case')
        bear_case = narratives.get('bear_case') or narratives.get('pessimistic_case')
        
        # Also check scenarios dict
        if scenarios:
            if not bull_case:
                bull_case = scenarios.get('optimistic', {}).get('description') or scenarios.get('optimistic')
            if not bear_case:
                bear_case = scenarios.get('pessimistic', {}).get('description') or scenarios.get('pessimistic')
        
        if bull_case or bear_case or scenarios:
            suggestions.append({
                "icon": "",
                "text": "Show me bull vs bear scenarios",
                "category": "scenario"
            })
        
        # 3. Risk Question
        risks = risk_assessment.get('key_risks', [])
        if risks and len(risks) > 0:
            suggestions.append({
                "icon": "",
                "text": "What are the biggest risks?",
                "category": "risk"
            })
        else:
            suggestions.append({
                "icon": "",
                "text": "What could go wrong with this thesis?",
                "category": "risk"
            })
        
        # 4. Key Assumptions Question
        assumptions = dcf_analysis.get('assumptions', {})
        key_assumptions = dcf_analysis.get('key_assumptions', {}) or assumptions
        
        if key_assumptions or assumptions:
            # Check for aggressive growth assumptions
            revenue_growth = (
                key_assumptions.get('revenue_growth') or
                key_assumptions.get('growth_rate') or
                assumptions.get('revenue_growth_rate') or
                valuation_data.get('dcf', {}).get('derived', {}).get('revenue_cagr_pct')
            )
            
            if revenue_growth and revenue_growth > 10:
                suggestions.append({
                    "icon": "🔍",
                    "text": f"Is {revenue_growth:.1f}% revenue growth sustainable?",
                    "category": "assumption"
                })
            else:
                terminal_growth = (
                    key_assumptions.get('terminal_growth_rate') or
                    assumptions.get('terminal_growth_rate') or
                    valuation_data.get('dcf', {}).get('terminal_assumptions', {}).get('terminal_growth_rate_pct')
                )
                if terminal_growth:
                    suggestions.append({
                        "icon": "🔍",
                        "text": f"Why {terminal_growth:.1f}% terminal growth?",
                        "category": "assumption"
                    })
                else:
                    suggestions.append({
                        "icon": "🔍",
                        "text": "Explain the key assumptions",
                        "category": "assumption"
                    })
        
        # 5. Sensitivity/What-If Question
        sensitivity = _extract_sensitivity(valuation_data)
        if sensitivity:
            suggestions.append({
                "icon": "",
                "text": "How sensitive is this to WACC changes?",
                "category": "sensitivity"
            })
        
        # 6. Market Comparison
        suggestions.append({
            "icon": "",
            "text": "How does this compare to analyst consensus?",
            "category": "comparison"
        })
        
        # 7. Deep Dive on Drivers
        if narratives.get('key_drivers') or narratives.get('growth', {}).get('narrative'):
            suggestions.append({
                "icon": "",
                "text": "What's driving the value?",
                "category": "drivers"
            })
        
        # Return top N suggestions (sorted by relevance)
        # Prioritize: valuation gap > scenarios > risks > assumptions
        priority_order = {
            "valuation": 1,
            "scenario": 2,
            "risk": 3,
            "assumption": 4,
            "sensitivity": 5,
            "comparison": 6,
            "drivers": 7
        }
        
        suggestions_sorted = sorted(
            suggestions,
            key=lambda x: priority_order.get(x['category'], 99)
        )[:num_suggestions]
        
        return {
            "primary_question": primary_question,
            "suggestions": suggestions_sorted
        }
        
    except Exception as e:
        logger.error(f"Error generating opening suggestions: {e}")
        # Fallback to generic suggestions
        return {
            "primary_question": f"I've analyzed {company_name or ticker}. What would you like to explore?",
            "suggestions": [
                {"icon": "", "text": "Show me the valuation summary", "category": "summary"},
                {"icon": "", "text": "What are the key risks?", "category": "risk"},
                {"icon": "", "text": "Explain the assumptions", "category": "assumption"},
                {"icon": "", "text": "Give me investment advice", "category": "advice"}
            ]
        }
