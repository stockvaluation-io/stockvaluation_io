"""
Hypothesis Detection Service
Analyzes conversation history to determine if enough information exists to form an investment hypothesis
"""
import logging
import re
from typing import Dict, Any, List, Optional

from services.conviction_tracker import calculate_conviction_score

logger = logging.getLogger(__name__)


def analyze_conversation_for_hypothesis(
    conversation_history: List[Dict[str, Any]],
    ticker: str,
    valuation_data: Optional[Dict[str, Any]] = None,
    session: Optional[Dict[str, Any]] = None,
    ai_service = None
) -> Dict[str, Any]:
    """
    Analyze conversation to detect if hypothesis can be formed.
    
    Args:
        conversation_history: List of conversation messages
        ticker: Stock ticker symbol
        valuation_data: Current valuation data (optional)
        session: Session data with metadata (optional)
        ai_service: AI service for conviction assessment (optional)
    
    Returns:
        Dict with:
            - ready: bool - Whether hypothesis can be formed
            - confidence: float - Confidence score (0-1)
            - conviction_score: int - Conviction score (0-10)
            - conviction_details: Dict - Detailed conviction breakdown
            - is_reprompt: bool - Whether this is a re-prompt attempt
            - reasons: List[str] - Reasons why ready/not ready
    """
    if not conversation_history or len(conversation_history) < 4:
        return {
            'ready': False,
            'confidence': 0.0,
            'reasons': ['Conversation too short - need at least 4-5 exchanges']
        }
    
    # Extract user and assistant messages
    user_messages = [msg for msg in conversation_history if msg.get('role') == 'user']
    assistant_messages = [msg for msg in conversation_history if msg.get('role') == 'assistant']
    
    if len(user_messages) < 2:
        return {
            'ready': False,
            'confidence': 0.0,
            'reasons': ['Need more user input - at least 2 user messages']
        }
    
    # Combine all messages for analysis
    all_text = ' '.join([
        msg.get('content', '') for msg in conversation_history
        if isinstance(msg.get('content'), str)
    ]).lower()
    
    confidence_score = 0.0
    reasons = []
    
    # Check for key indicators
    
    # 1. Growth rate/assumptions mentioned
    growth_patterns = [
        r'(\d+\.?\d*)\s*%?\s*growth',
        r'growth\s*(?:rate|of|at)\s*(\d+\.?\d*)',
        r'revenue\s*growth\s*(?:of|at|is)\s*(\d+\.?\d*)',
        r'(\d+\.?\d*)\s*%?\s*revenue',
    ]
    growth_mentioned = any(re.search(pattern, all_text) for pattern in growth_patterns)
    if growth_mentioned:
        confidence_score += 0.2
        reasons.append('Growth rate/assumptions discussed')
    
    # 2. Conviction level expressed
    conviction_patterns = [
        r'conviction',
        r'confident',
        r'certain',
        r'believe',
        r'think.*\d+/10',
        r'conviction.*\d+',
    ]
    conviction_mentioned = any(re.search(pattern, all_text) for pattern in conviction_patterns)
    if conviction_mentioned:
        confidence_score += 0.2
        reasons.append('Conviction level expressed')
    
    # 3. Specific reasoning provided
    reasoning_indicators = [
        'because',
        'due to',
        'reason',
        'why',
        'catalyst',
        'investment',
        'thesis',
    ]
    reasoning_count = sum(1 for indicator in reasoning_indicators if indicator in all_text)
    if reasoning_count >= 2:
        confidence_score += 0.15
        reasons.append('Specific reasoning provided')
    
    # 4. Valuation parameters discussed
    valuation_terms = [
        'fair value',
        'intrinsic value',
        'valuation',
        'undervalued',
        'overvalued',
        'price target',
        'target price',
    ]
    valuation_mentioned = any(term in all_text for term in valuation_terms)
    if valuation_mentioned:
        confidence_score += 0.15
        reasons.append('Valuation parameters discussed')
    
    # 5. Catalysts or risks mentioned
    risk_catalyst_terms = [
        'catalyst',
        'risk',
        'opportunity',
        'threat',
        'upside',
        'downside',
    ]
    risk_catalyst_count = sum(1 for term in risk_catalyst_terms if term in all_text)
    if risk_catalyst_count >= 2:
        confidence_score += 0.15
        reasons.append('Catalysts or risks discussed')
    
    # 6. Timeframe mentioned
    timeframe_terms = [
        'short-term',
        'long-term',
        'medium-term',
        'timeframe',
        'horizon',
        'years',
    ]
    timeframe_mentioned = any(term in all_text for term in timeframe_terms)
    if timeframe_mentioned:
        confidence_score += 0.1
        reasons.append('Investment timeframe mentioned')
    
    # 7. Minimum message count bonus
    if len(user_messages) >= 3:
        confidence_score += 0.05
        reasons.append('Sufficient conversation depth')
    
    # Normalize confidence to 0-1 range
    confidence_score = min(confidence_score, 1.0)
    
    # === NEW: CONVICTION-BASED READINESS ===
    # Calculate conviction score (0-10 scale)
    conviction_result = {'score': 0, 'behavioral': 0, 'llm_assessed': 0, 'signals': {}}
    if session and ai_service:
        try:
            conviction_result = calculate_conviction_score(
                conversation_history=conversation_history,
                session=session,
                ai_service=ai_service
            )
        except Exception as e:
            logger.error(f"Error calculating conviction score: {e}", exc_info=True)
    
    conviction_score = conviction_result['score']
    
    # Determine if this is a re-prompt attempt
    is_reprompt = session.get('hypothesis_prompt_count', 0) > 0 if session else False
    
    # Determine readiness based on conviction and confidence
    # First attempt: lenient (confidence >= 0.5)
    # Re-prompt: strict (confidence >= 0.6 AND conviction >= 7)
    if is_reprompt:
        ready = (confidence_score >= 0.6 and conviction_score >= 7)
        if not ready:
            if conviction_score < 7:
                reasons.append(f'Conviction score {conviction_score}/10 below re-prompt threshold (7/10)')
            if confidence_score < 0.6:
                reasons.append(f'Confidence score {confidence_score:.2f} below re-prompt threshold (0.6)')
    else:
        # First attempt - more lenient
        ready = confidence_score >= 0.5
        if not ready:
            reasons.append(f'Confidence score {confidence_score:.2f} below threshold (0.5)')
        elif conviction_score < 7:
            reasons.append(f'Note: Conviction score {conviction_score}/10 is moderate. System may re-prompt if it increases to 7+')
    
    logger.info(
        f"Hypothesis detection for {ticker}: ready={ready}, "
        f"confidence={confidence_score:.2f}, conviction={conviction_score}/10, "
        f"is_reprompt={is_reprompt}, reasons={reasons}"
    )
    
    return {
        'ready': ready,
        'confidence': confidence_score,
        'conviction_score': conviction_score,
        'conviction_details': conviction_result,
        'is_reprompt': is_reprompt,
        'reasons': reasons
    }

