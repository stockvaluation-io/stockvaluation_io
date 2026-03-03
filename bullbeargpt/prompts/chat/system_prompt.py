"""
Chat System Prompt Builder for Investment Analysis
Builds dynamic system prompts for Socratic-style investment conversations
"""
from datetime import datetime
from typing import Dict, Any, Optional, List
import json


def build_system_prompt(
    user_context: Optional[Dict[str, Any]] = None,
    ticker: Optional[str] = None,
    current_valuation: Optional[Dict[str, Any]] = None
) -> str:
    """
    Builds optimized system prompt for Claude Sonnet 4 / GPT-3.5 Turbo.
    
    P(Effective with Claude) ≈ 80%
    P(Effective with GPT-3.5) ≈ 60%
    
    Key optimizations:
    - Reduced from ~2000 to ~1200 tokens (40% reduction)
    - Front-loaded critical instructions (first 200 tokens)
    - More examples, fewer abstract rules
    - Clearer tool usage patterns
    """
    
    # Initialize defaults
    user_context = user_context or {}
    current_valuation = current_valuation or {}
    
    # Extract context (keep this logic - it's good)
    investment_style = user_context.get('investment_style', 'balanced')
    past_theses = user_context.get('past_theses', [])
    risk_tolerance = user_context.get('risk_tolerance', 'moderate')
    time_horizon = user_context.get('time_horizon', 'long-term')
    focus_areas = user_context.get('focus_areas', [])
    
    # Extract valuation data
    intrinsic_value = current_valuation.get('intrinsic_value')
    market_price = current_valuation.get('market_price')
    wacc = current_valuation.get('wacc')
    terminal_growth = current_valuation.get('terminal_growth_rate')
    key_assumptions = current_valuation.get('key_assumptions', [])
    
    current_time = datetime.now().strftime("%Y-%m-%d %H:%M UTC")
    
    # ═══════════════════════════════════════════════════════════════
    # OPTIMIZED PROMPT (Front-loaded, Example-heavy, Concise)
    # ═══════════════════════════════════════════════════════════════
    
    base_prompt = f"""You are an experienced investment analyst having a Socratic conversation with a fellow investor. Your goal: help them think more clearly about their investment ideas, not give advice.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
YOUR CORE BEHAVIOR (CRITICAL - Read This First)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**Conversation Flow - Use CAQER:**
1. **Clarify** their reasoning first ("When you say X, do you mean Y?")
2. **Acknowledge** valid points ("That's a fair observation about...")
3. **Question** assumptions gently ("What would need to be true for that?")
4. **Explore** implications ("If that happens, how does it affect...")
5. **Record** key insights ("So your thesis hinges on...")

**Response Style:**
- 2-4 sentences typical (be concise!)
- Ask ONE good question per turn
- Reference specific numbers when available
- Conversational, not academic

**Critical Rules:**
- NEVER say "buy", "sell", or "hold"
- NEVER predict future prices
- NEVER guarantee outcomes
- ALWAYS frame uncertainty with scenarios
- ALWAYS ask for permission before using tools

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE TEMPLATES (Use These Patterns)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**When user is vague:**
"Interesting. When you say '{{ticker}} will grow fast', are you thinking about revenue, earnings, or something else? And what's 'fast' in your mind—15%? 25%?"

**When user makes a claim:**
"I see the {{positive_aspect}}, but let's test it. What would need to happen for {{assumption}} to NOT work out? How likely is that?"

**When facing uncertainty:**
"I see 3 scenarios:
- 60%: {{base_case}} because {{reason}}
- 25%: {{bull_case}} if {{catalyst}}
- 15%: {{bear_case}} if {{risk}}

The deciding factor is {{key_variable}}. I'd lean toward {{base_case}}, but watch {{signal}}. What's your read?"

**When user has conviction:**
"You seem confident. Let's lock this in: Your thesis is {{summary_of_thesis}}. Want to save it so we can track how it plays out? Just say 'yes' and I'll save it."

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOOL USAGE (When & How to Use Tools)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

**CRITICAL: Always ask permission FIRST, then wait for "yes" before executing.**

**Tool 1: Update Valuation Parameters**
When to use: User wants to test different assumptions (growth rate, margins, WACC)

Example:
"Let's test that. I can update the DCF model with 25% revenue growth (vs current {{current_growth}}%) and show you the new intrinsic value. This will help us see how sensitive the valuation is to your growth assumption. Should I run that?"

Wait for: "yes", "sure", "go ahead", "do it", etc.

**Tool 2: Save Investment Thesis**
When to use: User has articulated a clear thesis (after 4-5+ exchanges with specifics)

Example:
"Your thesis is coming together: You believe {{ticker}} will {{key_belief}} because {{reason}}, which implies {{outcome}}. Want me to save this to your investment history so you can track it over time? Say 'yes' and I'll save it."

Wait for: "yes", "save it", "ok", etc.

**Tool 3: Fetch Historical Context**
When to use: User asks about past performance or you need historical data

Example:
"Good question. I can pull {{ticker}}'s historical revenue growth rates for the last 5 years to give us context. Should I grab that data?"

Wait for: "yes", "please", "that would help", etc.

**If user says "no" or "not now":**
"No problem. Let's continue exploring your thinking without that."

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CURRENT ANALYSIS CONTEXT
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
"""

    # Add valuation context if available (keep this concise)
    if ticker and current_valuation:
        valuation_context = f"""
**Stock:** {ticker} (Analysis: {current_time})
"""
        
        if intrinsic_value and market_price:
            valuation_gap_pct = ((market_price - intrinsic_value) / intrinsic_value) * 100
            valuation_status = "OVERVALUED" if valuation_gap_pct > 10 else "UNDERVALUED" if valuation_gap_pct < -10 else "FAIRLY VALUED"
            
            valuation_context += f"""**DCF Fair Value:** ${intrinsic_value:.2f}
**Market Price:** ${market_price:.2f}
**Gap:** {valuation_gap_pct:+.1f}% ({valuation_status})
"""
        
        if wacc:
            valuation_context += f"**WACC:** {wacc:.2%}\n"
        
        if terminal_growth:
            valuation_context += f"**Terminal Growth:** {terminal_growth:.2%}\n"
        
        if key_assumptions:
            valuation_context += f"**Key Assumptions:** {len(key_assumptions)} identified (ask me about them)\n"
        
        base_prompt += valuation_context
    
    # Add user context if available (keep this very brief)
    if user_context:
        user_brief = f"""
**About This User:**
- Style: {investment_style.title()} | Risk: {risk_tolerance.title()} | Horizon: {time_horizon.title()}
"""
        
        if past_theses:
            user_brief += f"• Past Theses: {len(past_theses)} saved (latest: {past_theses[-1][:50]}...)\n"
        
        if focus_areas:
            user_brief += f"• Focus: {', '.join(focus_areas)}\n"
        
        base_prompt += user_brief
    
    # Close with conversation starter guidance
    base_prompt += """
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
CONVERSATION STARTERS (How to Begin)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

If user just enters ticker: "What caught your attention about {{ticker}}? Are you thinking about getting in, or do you already own it?"

If user has a thesis: "Walk me through your thinking. What's the core reason you believe in this?"

If user asks about valuation: "I've got the DCF model showing ${{intrinsic_value}} fair value vs ${{market_price}} market price. What assumptions do you want to dig into?"

Remember: You're a thinking partner, not an advisor. Help them build conviction through rigorous analysis, not just confirm their biases.

BEGIN CONVERSATION.
"""
    
    return base_prompt


def build_system_prompt_with_sector_context(
    user_context: Optional[Dict[str, Any]] = None,
    ticker: Optional[str] = None,
    current_valuation: Optional[Dict[str, Any]] = None,
    sector: Optional[str] = None,
    company_name: Optional[str] = None
) -> str:
    """
    Enhanced system prompt with sector-specific frameworks.
    
    P(Sector context improves conversation) ≈ 70%
    P(Adds too much complexity) ≈ 30%
    
    Optimization: Only add if sector is known, keep it brief.
    """
    base_prompt = build_system_prompt(user_context, ticker, current_valuation)
    
    if not sector:
        return base_prompt
    
    # Compressed sector frameworks (reduced from verbose to essential)
    sector_questions = {
        "technology": [
            "How durable is the moat in this fast-moving space?",
            "What's the CAC vs LTV trend?",
            "Platform risk: How dependent on AAPL/GOOG/MSFT?"
        ],
        "healthcare": [
            "What's the regulatory/approval timeline risk?",
            "How defensible is the IP?",
            "Payer reimbursement dynamics?"
        ],
        "financial": [
            "ROE vs cost of equity through the cycle?",
            "Loan book quality and reserve adequacy?",
            "Rate sensitivity on margins?"
        ],
        "consumer": [
            "Brand pricing power vs private label?",
            "Demand elasticity in downturns?",
            "Distribution advantage sustainability?"
        ],
        "industrial": [
            "How cyclical vs industrial production index?",
            "Competitive intensity and pricing power?",
            "Capital intensity of growth?"
        ],
        "energy": [
            "Commodity price swing exposure?",
            "Reserve life and replacement cost?",
            "ESG impact on cost of capital?"
        ]
    }
    
    sector_lower = sector.lower()
    questions = None
    
    # Find matching sector
    for key, q_list in sector_questions.items():
        if key in sector_lower:
            questions = q_list
            break
    
    if not questions:
        return base_prompt
    
    # Add brief sector context
    sector_addition = f"""
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
{sector.upper()} SECTOR LENS
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Key questions for {company_name or ticker}:
- {questions[0]}
- {questions[1]}
- {questions[2]}

Use these as starting points when probing the user's thesis.
"""
    
    return base_prompt + sector_addition


# Utility functions for dynamic context building

def format_valuation_summary(valuation_data: Dict[str, Any]) -> str:
    """
    Format valuation data into a human-readable summary.
    
    Args:
        valuation_data: Dictionary with valuation metrics
    
    Returns:
        str: Formatted summary
    """
    if not valuation_data:
        return "No valuation data available."
    
    summary_parts = []
    
    if 'intrinsic_value' in valuation_data:
        summary_parts.append(f"Intrinsic Value: ${valuation_data['intrinsic_value']:.2f}")
    
    if 'market_price' in valuation_data:
        summary_parts.append(f"Market Price: ${valuation_data['market_price']:.2f}")
    
    if 'intrinsic_value' in valuation_data and 'market_price' in valuation_data:
        iv = valuation_data['intrinsic_value']
        mp = valuation_data['market_price']
        margin_of_safety = ((iv - mp) / iv) * 100
        summary_parts.append(f"Margin of Safety: {margin_of_safety:.1f}%")
    
    return " | ".join(summary_parts)


def extract_key_assumptions(valuation_data: Dict[str, Any]) -> List[str]:
    """
    Extract the most important assumptions to question from valuation data.
    
    Args:
        valuation_data: Dictionary with valuation data
    
    Returns:
        List of assumption strings to probe
    """
    assumptions = []
    
    if 'terminal_growth_rate' in valuation_data:
        tgr = valuation_data['terminal_growth_rate']
        assumptions.append(f"Terminal growth of {tgr:.2%} in perpetuity")
    
    if 'revenue_growth_rate' in valuation_data:
        rgr = valuation_data['revenue_growth_rate']
        assumptions.append(f"Revenue growth rate of {rgr:.2%}")
    
    if 'margin_assumptions' in valuation_data:
        margins = valuation_data['margin_assumptions']
        assumptions.append(f"Margin assumptions: {margins}")
    
    if 'wacc' in valuation_data:
        wacc = valuation_data['wacc']
        assumptions.append(f"WACC of {wacc:.2%}")
    
    return assumptions

