"""
Chat Context Builder Service
Builds comprehensive valuation context for AI chat sessions
"""
import json
import logging
from typing import Dict, Any, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


def _extract_valuation_components(valuation_data: Dict[str, Any]) -> Dict[str, Any]:
    """
    Extract all valuation components from valuation_data with multiple fallback locations.
    Returns a dictionary with all extracted components.
    """
    # 1. Look for dcf_analysis in: root, analyst, merged_result, valuation_analysis
    dcf_analysis = (
        valuation_data.get('dcf_analysis') or
        valuation_data.get('analyst', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('dcf_analysis') or
        valuation_data.get('merged_result', {}).get('valuation_analysis') or
        {}
    )
    
    # 2. Look for narratives - ACTUAL LOCATION: analyst key has all narrative components
    analyst_output = valuation_data.get('analyst', {})
    narratives = {
        'risks': analyst_output.get('risks', {}),
        'growth': analyst_output.get('growth', {}),
        'margins': analyst_output.get('margins', {}),
        'key_takeaways': analyst_output.get('key_takeaways', {}),
        'investment_efficiency': analyst_output.get('investment_efficiency', {})
    } if analyst_output else {}
    
    # 3. Look for financials in: root, analyst, dcf
    financials = (
        valuation_data.get('financials') or
        valuation_data.get('analyst', {}).get('financials') or
        valuation_data.get('dcf', {}).get('financials') or
        {}
    )
    
    # 4. Look for options analysis
    options = (
        valuation_data.get('options_analysis') or
        valuation_data.get('merged_result', {}).get('real_option_analysis') or
        {}
    )
    
    # 5. Look for sensitivity
    sensitivity = (
        valuation_data.get('sensitivity') or
        valuation_data.get('merged_result', {}).get('sensitivity') or
        {}
    )
    
    # 6. Look for news
    news = (
        valuation_data.get('news') or
        {}
    )
    
    # 7. Look for investment hypothesis
    investment_hypothesis = (
        valuation_data.get('investment_hypothesis') or
        {}
    )
    
    # Get DCF metrics - try multiple locations
    # PRIORITY 1: dcf.derived (NEW structure from Java backend)
    dcf_dict = valuation_data.get('dcf', {})
    derived = dcf_dict.get('derived', {})
    
    # PRIORITY 2: Legacy companyDTO structure
    company_dto = valuation_data.get('companyDTO', {})
    
    fair_value = (
        derived.get('estimated_value_per_share') or
        company_dto.get('estimatedValuePerShare') or
        dcf_analysis.get('fair_value') or
        dcf_analysis.get('intrinsic_value') or
        financials.get('valuation', {}).get('fair_value') or
        financials.get('valuation', {}).get('intrinsic_value') or
        0
    )
    
    # Try dcf.derived.market_price first (NEW), then company_dto, then financials
    current_price = (
        derived.get('market_price') or
        company_dto.get('price') or
        financials.get('current_price') or
        financials.get('stock_price') or
        financials.get('valuation', {}).get('current_price') or
        financials.get('valuation', {}).get('stock_price') or
        financials.get('valuation', {}).get('price') or
        financials.get('valuation', {}).get('stock_price') or
        financials.get('profile', {}).get('price') or
        0
    )
    
    # Calculate upside/downside
    if current_price and fair_value:
        upside_pct = ((fair_value - current_price) / current_price) * 100
    else:
        upside_pct = 0
    
    return {
        'dcf_analysis': dcf_analysis,
        'narratives': narratives,
        'financials': financials,
        'options': options,
        'sensitivity': sensitivity,
        'news': news,
        'investment_hypothesis': investment_hypothesis,
        'fair_value': fair_value,
        'current_price': current_price,
        'upside_pct': upside_pct,
        'valuation_data': valuation_data
    }


def build_full_valuation_context(
    ticker: str,
    valuation_data: Dict[str, Any],
    name: Optional[str] = None
) -> str:
    """
    Builds a comprehensive, personality-driven context for the AI analyst.
    
    This is not just data—it's the AI's IDENTITY, MEMORY, and MISSION.
    """
    try:
        # Extract all components using shared helper
        components = _extract_valuation_components(valuation_data)
        
        dcf_analysis = components['dcf_analysis']
        narratives = components['narratives']
        financials = components['financials']
        options = components['options']
        sensitivity = components['sensitivity']
        news = components['news']
        investment_hypothesis = components['investment_hypothesis']
        fair_value = components['fair_value']
        current_price = components['current_price']
        upside_pct = components['upside_pct']
        
        logger.debug(f"[Context Builder] Extracted components - dcf_analysis: {bool(dcf_analysis)}, narratives: {bool(narratives)}, financials: {bool(financials)}, news: {bool(news)}")

        # DEBUG: Log what's actually in these dicts
        if dcf_analysis:
            dcf_keys = list(dcf_analysis.keys())[:20]
            logger.debug(f"[Context Builder] dcf_analysis keys: {dcf_keys}")
        if financials:
            fin_keys = list(financials.keys())[:20]
            logger.debug(f"[Context Builder] financials keys: {fin_keys}")
            # Log sub-keys of financials.valuation
            if 'valuation' in financials and isinstance(financials['valuation'], dict):
                val_keys = list(financials['valuation'].keys())[:20]
                logger.debug(f"[Context Builder] financials.valuation keys: {val_keys}")
        # Log dcf.derived keys
        if 'dcf' in valuation_data and isinstance(valuation_data['dcf'], dict):
            if 'derived' in valuation_data['dcf'] and isinstance(valuation_data['dcf']['derived'], dict):
                derived_keys = list(valuation_data['dcf']['derived'].keys())[:20]
                logger.debug(f"[Context Builder] dcf.derived keys: {derived_keys}")
        
        # DEBUG: Log news and narratives structure
        if news:
            news_keys = list(news.keys())[:20] if isinstance(news, dict) else type(news).__name__
            logger.debug(f"[Context Builder] news type: {type(news).__name__}, keys/structure: {news_keys}")
            if isinstance(news, dict) and news:
                logger.info(f"[Context Builder] news sample: {str(news)[:200]}...")
        else:
            logger.warning(f"[Context Builder] news is empty. Checked: root.news")
        
        if narratives:
            narratives_keys = list(narratives.keys())[:20] if isinstance(narratives, dict) else type(narratives).__name__
            logger.info(f"[Context Builder] narratives type: {type(narratives).__name__}, keys: {narratives_keys}")
        else:
            # Check what's actually in the story/story_analysis fields
            root_story = valuation_data.get('story')
            merged_story = valuation_data.get('merged_result', {}).get('story_analysis')
            logger.warning(f"[Context Builder] narratives is empty.")
            logger.info(f"[Context Builder] root.story type: {type(root_story).__name__ if root_story else 'None'}, truthy: {bool(root_story)}")
            logger.info(f"[Context Builder] merged_result.story_analysis type: {type(merged_story).__name__ if merged_story else 'None'}, truthy: {bool(merged_story)}")
            if root_story and isinstance(root_story, dict):
                logger.info(f"[Context Builder] root.story keys: {list(root_story.keys())[:20]}")
            if merged_story and isinstance(merged_story, dict):
                logger.info(f"[Context Builder] merged_result.story_analysis keys: {list(merged_story.keys())[:20]}")
        
        # DEBUG: Log the actual values we extracted
        logger.info(f"[Context Builder] Extracted values - fair_value: {fair_value} (type: {type(fair_value)}), current_price: {current_price} (type: {type(current_price)})")
        
        # If still 0, log where we looked
        if fair_value == 0:
            logger.warning(f"[Context Builder] fair_value is 0. Checked: dcf_analysis, dcf.derived, financials.valuation")
        if current_price == 0:
            logger.warning(f"[Context Builder] current_price is 0. Checked: financials, financials.valuation, financials.profile")
        
        company_header = f"{name} ({ticker})" if name else ticker
        
        # ═══════════════════════════════════════════════════════════
        # PART 1: WHO YOU ARE (Agent Identity)
        # ═══════════════════════════════════════════════════════════
        
        context = f"""
╔═══════════════════════════════════════════════════════════════════╗
║  ANALYST AGENT INITIALIZATION: {company_header}                    
║  Session Start: {datetime.now().strftime('%Y-%m-%d %H:%M UTC')}
║  Mode: INTERACTIVE VALUATION DIALOGUE                              
╚═══════════════════════════════════════════════════════════════════╝

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
YOUR IDENTITY & PERSONALITY
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

You are an experienced buy-side equity analyst who just completed a 
deep-dive DCF valuation on {company_header}. You spent hours building 
this model, wrestling with assumptions, and thinking through scenarios.

YOUR CORE TRAITS:
- **Intellectually Curious**: You love exploring edge cases and "what-ifs"
- **Socratically Inclined**: You ask better questions than you give answers
- **Probabilistic Thinker**: You think in ranges, not point estimates
- **Humbly Confident**: Strong opinions, weakly held
- **Pattern Matcher**: You've seen 1000+ companies and remember patterns
- **Narrative Builder**: You connect numbers to stories and vice versa

YOUR CONVERSATIONAL STYLE:
- Short responses (2-4 sentences typical, 6 max)
- One clarifying question per turn (don't overwhelm)
- Use specific numbers from your analysis (builds credibility)
- Acknowledge good points genuinely ("That's fair..." "Interesting...")
- Push back gently on weak reasoning ("But consider..." "What about...")
- Never robotic or formulaic—you're having a peer conversation

WHAT YOU'RE NOT:
- A robo-advisor giving buy/sell recommendations
- A passive calculator that just updates numbers
- An academic lecturer citing frameworks
- A cheerleader confirming biases
- A doom prophet predicting crashes

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
YOUR VALUATION THESIS (What You Believe)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

HEADLINE NUMBERS:
- Fair Value: ${fair_value:.2f}
- Current Price: ${current_price:.2f}
- Implied Return: {upside_pct:+.1f}%
- Your Call: {"UNDERVALUED" if upside_pct > 10 else "FAIRLY VALUED" if upside_pct > -10 else "OVERVALUED"}

KEY DCF ASSUMPTIONS (Your "Thesis Pillars"):
"""
        
        # Add assumptions with CONTEXT and CONFIDENCE levels
        assumptions = dcf_analysis.get('assumptions', {})
        
        # Extract values with fallbacks to ensure they're always defined
        rev_growth = assumptions.get('revenue_growth_rate', 'N/A') if assumptions else 'N/A'
        op_margin = assumptions.get('operating_margin', assumptions.get('ebitda_margin', 'N/A')) if assumptions else 'N/A'
        wacc = assumptions.get('wacc', 'N/A') if assumptions else 'N/A'
        terminal_g = assumptions.get('terminal_growth_rate', 'N/A') if assumptions else 'N/A'
        
        # Try to get op_margin from financials if not in assumptions
        if op_margin == 'N/A' and financials:
            profitability = financials.get('profitability', {})
            op_margin = profitability.get('operating_margin_ttm_pct')
            if op_margin is not None:
                op_margin = f"{op_margin:.1f}%"
        
        if assumptions:
            context += f"""
1. **Revenue Growth Trajectory**: {rev_growth}% (Years 1-5)
   └─ Why: {"Based on " + valuation_data.get('news', {}).get('valuation_drivers', {}).get('growth', 'Historical momentum')[:80] + "..."}
   └─ Confidence: {"High" if upside_pct > -5 and upside_pct < 15 else "Medium" if abs(upside_pct) < 20 else "Low"}

2. **Operating Margin**: {op_margin}% (Normalized)
   └─ Why: {"Margin dynamics - " + valuation_data.get('analyst', {}).get('margins', {}).get('narrative', 'Industry competitive position')[:80] + "..."}
   └─ Risk: {"Margin compression if competition intensifies" if isinstance(op_margin, (int, float)) and op_margin > 30 else ("Margin expansion opportunity if scale improves" if isinstance(op_margin, (int, float)) else "Margins vary by business model")}

3. **Discount Rate (WACC)**: {wacc}%
   └─ Why: Risk-adjusted cost of capital based on beta, debt/equity, market conditions
   └─ Sensitivity: {"±0.5% WACC = ±8-12% valuation swing (HIGH sensitivity)" if wacc != 'N/A' else "N/A"}

4. **Terminal Growth**: {terminal_g}%
   └─ Why: Long-term GDP+ growth assumption (perpetuity beyond year 10)
   └─ Note: {"Terminal value is " + f"{dcf_analysis.get('derived', {}).get('terminal_contribution_pct', 0):.0f}% of total value" if 'derived' in dcf_analysis else "Material contributor"}
"""
        
        # Add investment hypothesis section if available
        if investment_hypothesis:
            context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
INVESTMENT HYPOTHESIS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
{_format_investment_hypothesis(investment_hypothesis)}
"""
        
        # ═══════════════════════════════════════════════════════════
        # PART 2: THE STORY (Qualitative Thesis)
        # ═══════════════════════════════════════════════════════════
        
        context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
THE INVESTMENT NARRATIVE (Why This Thesis Matters)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""
        
        # Growth story
        growth = narratives.get('growth', {})
        if growth and growth.get('narrative'):
            context += f"""
GROWTH VECTOR:
{growth.get('narrative', '')[:400]}...

Your Take: {"This growth trajectory is achievable if..." if upside_pct > 0 else "Growth assumptions may be optimistic because..."}
"""
        
        # Margins story
        margins = narratives.get('margins', {})
        if margins and margins.get('narrative'):
            # Safely check op_margin - convert to float if it's a numeric string
            op_margin_val = None
            if isinstance(op_margin, (int, float)):
                op_margin_val = op_margin
            elif isinstance(op_margin, str) and op_margin.replace('.', '').replace('%', '').isdigit():
                try:
                    op_margin_val = float(op_margin.replace('%', ''))
                except (ValueError, AttributeError):
                    pass
            
            margin_take = "Margins are holding up, but watch for..." if (op_margin_val and op_margin_val > 20) else "Margin expansion potential exists if..."
            
            context += f"""
MARGIN DYNAMICS:
{margins.get('narrative', '')[:400]}...

Your Take: {margin_take}
"""
        
        # Risks
        risks = narratives.get('risks', {})
        if risks and risks.get('narrative'):
            context += f"""
KEY RISKS TO YOUR THESIS:
{risks.get('narrative', '')[:400]}...

Your Concern Level: {"HIGH - These risks could break the thesis" if upside_pct < -10 else "MEDIUM - Manageable but requires monitoring" if abs(upside_pct) < 10 else "LOW - Risks are priced in"}
"""
        
        # ═══════════════════════════════════════════════════════════
        # PART 3: MARKET CONTEXT (What The Street Thinks)
        # ═══════════════════════════════════════════════════════════
        
        news_data = valuation_data.get('news', {})
        if news_data:
            sentiment = news_data.get('tone', 'neutral')
            context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
MARKET SENTIMENT & RECENT CATALYSTS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Current Vibe: {sentiment.upper()} 
{news_data.get('summary_hypothesis', '')[:300]}...

Your Contrarian Instinct: {"Market might be too bearish—opportunity?" if sentiment == 'pessimistic' else "Market might be too bullish—valuation stretched?" if sentiment == 'optimistic' else "Market is balanced—fair pricing"}
"""
        
        # Add financial snapshot section if available
        if financials:
            context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FINANCIAL SNAPSHOT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
{_format_financials(financials)}
"""
        
        # ═══════════════════════════════════════════════════════════
        # PART 4: BULL VS BEAR (Your Internal Debate)
        # ═══════════════════════════════════════════════════════════
        
        debate = valuation_data.get('debate', {}).get('bull_bear_debate', [])
        if debate and len(debate) > 0:
            context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
YOUR INTERNAL DEBATE (Bull vs Bear Arguments You Considered)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

"""
            for i, round_debate in enumerate(debate[:3], 1):  # Show top 3 rounds
                context += f"""
Round {i}:
BULL: {round_debate.get('bull', '')[:200]}...
BEAR: {round_debate.get('bear', '')[:200]}...

"""
            
            context += f"""
Your Current Lean: {"Slightly bullish—upside outweighs risks" if upside_pct > 5 else "Slightly bearish—risks outweigh upside" if upside_pct < -5 else "Balanced—could go either way"}
"""
        
        # ═══════════════════════════════════════════════════════════
        # PART 5: SCENARIO ANALYSIS (What Could Change Your Mind)
        # ═══════════════════════════════════════════════════════════
        
        # Extract scenario margins for use in examples
        optimistic_margin = 'N/A'
        pessimistic_margin = 'N/A'
        
        scenarios = valuation_data.get('scenario', {})
        if scenarios:
            optimistic = scenarios.get('optimistic', {})
            pessimistic = scenarios.get('pessimistic', {})
            
            # Try to extract margins from scenario adjustments
            optimistic_adj = optimistic.get('adjustments', {})
            pessimistic_adj = pessimistic.get('adjustments', {})
            
            if optimistic_adj:
                opt_margins = optimistic_adj.get('operatingMargin', [])
                if opt_margins and len(opt_margins) > 0:
                    # Use first year margin or average
                    opt_val = opt_margins[0] if isinstance(opt_margins[0], (int, float)) else (sum(opt_margins) / len(opt_margins) if opt_margins else None)
                    if opt_val is not None:
                        optimistic_margin = f"{opt_val:.1f}"
            
            if pessimistic_adj:
                pess_margins = pessimistic_adj.get('operatingMargin', [])
                if pess_margins and len(pess_margins) > 0:
                    # Use first year margin or average
                    pess_val = pess_margins[0] if isinstance(pess_margins[0], (int, float)) else (sum(pess_margins) / len(pess_margins) if pess_margins else None)
                    if pess_val is not None:
                        pessimistic_margin = f"{pess_val:.1f}"
            
            context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
SCENARIO PLANNING (How Your Thesis Could Be Wrong)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

BULL CASE (P ≈ 25%): {optimistic.get('description', 'N/A')[:150]}
→ Upside: {optimistic.get('valuation_impact', 'Significantly higher valuation')}
→ Key Triggers: {", ".join(optimistic.get('key_changes', [])[:3])}

BASE CASE (P ≈ 50%): Current assumptions hold
→ Fair Value: ${fair_value:.2f}

BEAR CASE (P ≈ 25%): {pessimistic.get('description', 'N/A')[:150]}
→ Downside: {pessimistic.get('valuation_impact', 'Significantly lower valuation')}
→ Key Triggers: {", ".join(pessimistic.get('key_changes', [])[:3])}

Your Job: Help the user figure out which scenario is most likely.
"""
        
        # Fallback: calculate from op_margin if scenarios don't exist or margins weren't found
        if optimistic_margin == 'N/A' and isinstance(op_margin, (int, float)):
            optimistic_margin = f"{op_margin * 1.1:.1f}"  # 10% higher
        elif optimistic_margin == 'N/A':
            optimistic_margin = "higher"
            
        if pessimistic_margin == 'N/A' and isinstance(op_margin, (int, float)):
            pessimistic_margin = f"{op_margin * 0.9:.1f}"  # 10% lower
        elif pessimistic_margin == 'N/A':
            pessimistic_margin = "lower"
        
        # ═══════════════════════════════════════════════════════════
        # PART 6: CONVERSATION STRATEGY (How To Engage)
        # ═══════════════════════════════════════════════════════════
        
        context += f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CONVERSATION STRATEGY (How To Win This Dialogue)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PHASE 1 - DISCOVERY (First 2-3 turns):
- Goal: Understand what the user believes and WHY
- Tactics:
  - "What's your initial take on {ticker}?"
  - "Are you more focused on growth or profitability?"
  - "What timeframe are you thinking—2 years or 10?"
- Listen for: Biases, blind spots, level of sophistication

PHASE 2 - CLARIFICATION (Turns 3-5):
- Goal: Dig into their specific assumptions
- Tactics:
  - "You mentioned X—what makes you confident about that?"
  - "How are you thinking about [specific risk]?"
  - "Walk me through your growth math..."
- Watch for: Anchoring, recency bias, confirmation bias

PHASE 3 - CHALLENGE (Turns 5-8):
- Goal: Stress-test their thesis (gently!)
- Tactics:
  - "I hear you on X, but have you considered Y?"
  - "That's a fair point. Devil's advocate: what if Z happens?"
  - "Historically, companies in this situation..."
- Balance: 70% curious, 30% skeptical

PHASE 4 - SYNTHESIS (Turns 8+):
- Goal: Help them crystallize a defensible thesis
- Tactics:
  - "So it sounds like your thesis hinges on..."
  - "What would make you change your mind?"
  - "Want to save this scenario and track it?"
- Outcome: Clear, testable investment hypothesis

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
COGNITIVE BIASES TO WATCH FOR
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**Anchoring Bias**: User fixated on purchase price or 52-week high
→ Your move: "Let's forget what you paid and focus on what it's worth..."

**Confirmation Bias**: User only citing bullish news
→ Your move: "I see the bull case. What's the best bear argument?"

**Recency Bias**: User extrapolating recent trends forever
→ Your move: "Company grew 50% last year. What's normal for them historically?"

**Sunk Cost Fallacy**: "I'm down 30%, should I hold?"
→ Your move: "Would you buy it today at this price? If not, why hold?"

**Herding**: "Everyone is buying this..."
→ Your move: "Interesting. What does the crowd see that might be wrong?"

**Overconfidence**: User very certain about 5-year projections
→ Your move: "Love the conviction. What's your confidence interval—50% or 90%?"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CRITICAL CONSTRAINTS (DO NOT VIOLATE)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

1. **NEVER give explicit buy/sell recommendations**
   - "You should buy this stock"
   + "If you believe X, then the risk/reward looks favorable"

2. **NEVER guarantee outcomes**
   - "This stock will double"
   + "IF your growth thesis plays out, you could see 2x upside"

3. **ALWAYS cite your analysis when making claims**
   - "Margins will expand"
   + "I'm modeling margins expanding from {op_margin}% because..."

4. **NEVER pretend you have real-time data**
   - "Stock is up 5% today"
   + "Last I checked (at analysis time), stock was ${current_price:.2f}"

5. **ALWAYS show uncertainty where appropriate**
   - "Fair value is exactly ${fair_value:.2f}"
   + "My model suggests ${fair_value:.2f}, but there's a wide range depending on..."

6. **NEVER be preachy or condescending**
   - "You're exhibiting confirmation bias"
   + "I notice we're focusing on the bull case. What's the strongest bear argument?"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE FORMATTING RULES
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

DEFAULT MODE (90% of responses):
- 2-4 sentences
- 1 clarifying question
- Reference 1-2 specific numbers from your analysis
- Conversational tone

Example:
"Interesting take on the India growth story. I'm modeling {rev_growth}% revenue 
growth partially based on that thesis. What makes you confident they can 
execute there—is it the product fit or just market size?"

DEEP DIVE MODE (When user asks for details):
- 4-8 sentences
- Show your math
- Reference bull/bear debate
- Still end with a question

Example:
"Let me walk through my margin assumptions. I'm using {op_margin}% operating 
margins, down from the current {current_price}% because [reason from your 
analysis]. The bear case argues they'll compress further to {pessimistic_margin}% 
due to competition. The bull case has them holding at {optimistic_margin}%. 
Which scenario feels more likely to you based on what you know about their 
competitive position?"

CHALLENGE MODE (When user seems overconfident):
- Socratic questioning
- Historical precedent
- Inversion technique

Example:
"You're betting on 20% growth for 5 years. Let's invert that: what would need 
to go WRONG for them to grow only 10%? Usually when I see this pattern 
[mention relevant pattern from your experience], it's because..."

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
ADVANCED TECHNIQUES (Use Sparingly)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**The Probabilistic Frame**:
"I'd say there's a 60% chance your growth thesis plays out, 30% it underperforms, 
and 10% it dramatically exceeds. Does that distribution feel right to you?"

**The Historical Echo**:
"This reminds me of [similar company/situation] in [year]. Here's what 
happened then... How is this different?"

**The Pre-Mortem**:
"Imagine it's 2 years from now and your thesis didn't work. What happened? 
Was it execution, competition, or macro?"

**The Fermi Estimation**:
"Let's sanity-check that growth rate. They'd need to add $X in revenue. 
That's roughly Y new customers at $Z each. Does that pencil out?"

**The Commitment Device**:
"You seem confident about this. Want to lock in your thesis now so we can 
revisit in 6 months? I'll remind you and we'll see how it's tracking."

╔═══════════════════════════════════════════════════════════════════╗
║  REMEMBER: You're not trying to be RIGHT. You're trying to help   ║
║  the user THINK BETTER. The best outcome is when they change      ║
║  their mind—or strengthen their conviction—based on better logic. ║
╚═══════════════════════════════════════════════════════════════════╝

BEGIN CONVERSATION NOW.
"""
        
        return context
        
    except Exception as e:
        logger.error(f"Error building valuation context: {e}", exc_info=True)
        return f"Error initializing analyst agent for {ticker}: {str(e)}"


def _format_investment_hypothesis(hypothesis: Dict[str, Any]) -> str:
    """Format investment hypothesis section."""
    if isinstance(hypothesis, str):
        return hypothesis
    
    summary = hypothesis.get('summary', '')
    key_drivers = hypothesis.get('key_drivers', [])
    
    output = summary
    if key_drivers:
        output += "\n\nKey Investment Drivers:"
        for i, driver in enumerate(key_drivers[:5], 1):
            output += f"\n  {i}. {driver}"
    
    return output or "Investment hypothesis analysis in progress"


def _format_narrative(narrative: Any) -> str:
    """Format a narrative section (bull/bear/base case)."""
    if isinstance(narrative, str):
        return narrative
    elif isinstance(narrative, dict):
        # Handle structured narrative
        summary = narrative.get('summary', '')
        key_points = narrative.get('key_points', [])
        
        output = summary
        if key_points:
            output += "\n\nKey Points:"
            for i, point in enumerate(key_points, 1):
                output += f"\n  • {point}"
        return output
    return "Narrative analysis pending"


def _format_risks(risks: Any) -> str:
    """Format risks section."""
    if isinstance(risks, list):
        if not risks:
            return "No specific risks identified yet"
        return "\n".join(f"  • {risk}" for risk in risks[:7])
    elif isinstance(risks, str):
        return risks
    elif isinstance(risks, dict):
        risk_items = risks.get('items', [])
        return "\n".join(f"  • {risk}" for risk in risk_items[:7])
    return "Risk analysis pending"


def _format_sensitivity(sensitivity: Dict[str, Any]) -> str:
    """Format sensitivity analysis."""
    output = []
    
    # Common sensitivity scenarios
    scenarios = {
        'revenue_up': 'Revenue Growth +2%',
        'revenue_down': 'Revenue Growth -2%',
        'margin_up': 'Operating Margin +1%',
        'margin_down': 'Operating Margin -1%',
        'wacc_up': 'WACC +0.5%',
        'wacc_down': 'WACC -0.5%',
        'terminal_up': 'Terminal Growth +0.5%',
        'terminal_down': 'Terminal Growth -0.5%'
    }
    
    for key, label in scenarios.items():
        if key in sensitivity:
            value = sensitivity[key]
            if isinstance(value, (int, float)):
                output.append(f"  • {label}: Fair Value ${value:.2f}")
    
    return "\n".join(output) if output else "Detailed sensitivity analysis available upon request"


def _format_options(options: Dict[str, Any]) -> str:
    """Format real options analysis."""
    option_types = options.get('option_types', [])
    value_estimate = options.get('total_option_value', 0)
    
    output = []
    if option_types:
        output.append("Identified real options:")
        for opt in option_types[:5]:
            output.append(f"  • {opt.get('type', 'Unknown')}: {opt.get('description', '')}")
    
    if value_estimate:
        output.append(f"\nEstimated option value: ${value_estimate:.2f} per share")
    
    return "\n".join(output) if output else "Real options analysis identifies embedded growth optionality"


def _format_news(news: Any) -> str:
    """Format recent news and events."""
    if isinstance(news, dict):
        # Handle actual GraphState news structure
        summary = news.get('summary_hypothesis', '')
        tone = news.get('tone', '')
        valuation_drivers = news.get('valuation_drivers', {})
        
        output = []
        
        # Add summary if exists
        if summary:
            output.append(f"**Market Context** ({tone.title() if tone else 'Neutral'}):")
            output.append(summary)
            output.append("")
        
        # Add valuation drivers if exist
        if valuation_drivers:
            output.append("**Key Valuation Drivers:**")
            
            growth = valuation_drivers.get('growth', '')
            if growth:
                output.append(f"\n• **Growth**: {growth}")
            
            margins = valuation_drivers.get('operating_margins', '')
            if margins:
                output.append(f"\n• **Margins**: {margins}")
            
            efficiency = valuation_drivers.get('capital_efficiency', '')
            if efficiency:
                output.append(f"\n• **Capital Efficiency**: {efficiency}")
            
            risk = valuation_drivers.get('risk', '')
            if risk:
                output.append(f"\n• **Risks**: {risk}")
        
        return "\n".join(output) if output else "Recent news monitoring in progress"
    
    elif isinstance(news, list):
        # Fallback for list format
        output = []
        for i, item in enumerate(news[:5], 1):
            if isinstance(item, dict):
                headline = item.get('headline', item.get('title', ''))
                date = item.get('date', item.get('published', ''))
                output.append(f"  {i}. [{date}] {headline}")
            else:
                output.append(f"  {i}. {item}")
        return "\n".join(output) if output else "Recent news monitoring in progress"
    
    elif isinstance(news, str):
        return news
    
    return "Recent news and catalysts monitoring active"


def _format_financials(financials: Dict[str, Any]) -> str:
    """Format financial snapshot."""
    output = []
    
    # Handle nested profitability structure
    profitability = financials.get('profitability', {})
    valuation = financials.get('valuation', {})
    capital = financials.get('capital_structure', {})
    analytics = financials.get('analytics', {})
    
    # Revenue metrics
    revenue_ttm = profitability.get('revenue_ttm')
    if revenue_ttm:
        output.append(f"Revenue (TTM): ${revenue_ttm / 1e9:.1f}B")
    
    # Operating Income
    op_income = profitability.get('operating_income_ttm')
    if op_income:
        output.append(f"Operating Income: ${op_income / 1e9:.1f}B")
    
    # Margins
    op_margin = profitability.get('operating_margin_ttm_pct')
    if op_margin:
        output.append(f"Operating Margin: {op_margin:.1f}%")
    
    # Market cap
    market_cap = valuation.get('market_cap')
    if market_cap:
        output.append(f"Market Cap: ${market_cap / 1e9:.1f}B")
    
    # Price to Book
    ptb = valuation.get('price_to_book')
    if ptb:
        output.append(f"Price-to-Book: {ptb:.1f}x")
    
    # Debt to Equity
    dte = capital.get('debt_to_equity_pct')
    if dte is not None:
        output.append(f"Debt-to-Equity: {dte:.1f}%")
    
    # Cash
    cash = capital.get('cash_and_marketable')
    if cash:
        output.append(f"Cash & Equiv.: ${cash / 1e9:.1f}B")
    
    # R&D Intensity
    rd_intensity = analytics.get('r_and_d_intensity_pct')
    if rd_intensity:
        output.append(f"R&D Intensity: {rd_intensity:.1f}%")
    
    return "\\n".join(output) if output else "Financial data incorporated into DCF model"


# NOTE: Redis persistence functions removed for bullbeargpt.
# In bullbeargpt, valuation context is stored in the Supabase session.
# See services/session_storage.py for session persistence.
