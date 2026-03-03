"""
User State Detector
Detects user's emotional/cognitive state to enable adaptive AI responses
"""
import logging
from typing import Dict, Any, List, Tuple, Optional

logger = logging.getLogger(__name__)


class UserState:
    """Enum-like class for user states"""
    FOMO = "FOMO"
    PANIC = "PANIC"
    OVERCONFIDENCE = "OVERCONFIDENCE"
    ANALYSIS_PARALYSIS = "ANALYSIS_PARALYSIS"
    CONFIRMATION_BIAS = "CONFIRMATION_BIAS"
    NEUTRAL = "NEUTRAL"


def detect_user_state(
    message: str,
    conversation_history: List[Dict[str, Any]],
    ticker_data: Optional[Dict[str, Any]] = None,
    session_metadata: Optional[Dict[str, Any]] = None
) -> Tuple[str, float]:
    """
    Detects user's emotional/cognitive state from their message.
    
    Args:
        message: Current user message
        conversation_history: List of previous messages in conversation
        ticker_data: Optional ticker performance data
        session_metadata: Optional session metadata
        
    Returns:
        Tuple of (state_name, confidence_score)
        
    Example:
        >>> detect_user_state(
        ...     "Stock is up 50%, should I buy?",
        ...     [],
        ...     {"recent_performance": {"1month": 52.3}}
        ... )
        ('FOMO', 0.85)
    """
    try:
        message_lower = message.lower()
        signals = []
        
        # === 1. FOMO DETECTION ===
        fomo_keywords = [
            'everyone is', 'everybody is', 'missing out', 'miss out', 
            'is up', 'going to moon', 'going to the moon', 'should i buy now',
            'too late', 'train is leaving', 'fomo', 'all my friends',
            'skyrocketing', 'explosive growth', 'before it\'s too late',
            'momentum', 'trending', 'hot stock', 'everyone\'s buying'
        ]
        
        fomo_count = sum(1 for phrase in fomo_keywords if phrase in message_lower)
        
        if fomo_count > 0:
            confidence = min(0.6 + (fomo_count * 0.15), 0.95)
            
            # Boost confidence if stock has strong recent performance
            if ticker_data:
                recent_perf = ticker_data.get('recent_performance', {})
                one_month = recent_perf.get('1month', recent_perf.get('1_month', 0))
                
                if one_month > 20:
                    confidence = min(confidence + 0.2, 0.95)
                elif one_month > 30:
                    confidence = min(confidence + 0.3, 0.95)
            
            signals.append((UserState.FOMO, confidence))
        
        # === 2. PANIC DETECTION ===
        panic_keywords = [
            'should i sell', 'should i cut', 'is this a trap', 'value trap',
            'going to zero', 'cut losses', 'cut my losses', 'stop loss',
            'panic', 'scared', 'worried', 'crashing', 'collapsing',
            'free fall', 'bleeding', 'bag holder', 'catching a falling knife',
            'dead money', 'disaster', 'should i exit', 'get out now'
        ]
        
        panic_count = sum(1 for phrase in panic_keywords if phrase in message_lower)
        
        if panic_count > 0:
            confidence = min(0.65 + (panic_count * 0.15), 0.95)
            
            # Boost confidence if stock has strong recent decline
            if ticker_data:
                recent_perf = ticker_data.get('recent_performance', {})
                one_month = recent_perf.get('1month', recent_perf.get('1_month', 0))
                
                if one_month < -15:
                    confidence = min(confidence + 0.2, 0.95)
                elif one_month < -25:
                    confidence = min(confidence + 0.3, 0.95)
            
            signals.append((UserState.PANIC, confidence))
        
        # === 3. OVERCONFIDENCE DETECTION ===
        overconfidence_keywords = [
            'guaranteed', 'guarantee', 'no way', 'definitely', 'cant lose',
            'can\'t lose', 'obvious', 'obviously', 'for sure', 'certain',
            'no doubt', 'easy money', 'sure thing', 'slam dunk', 'no brainer',
            'impossible to fail', 'can\'t go wrong', 'risk-free', 'safe bet',
            '100%', 'absolutely', 'zero risk', 'no downside'
        ]
        
        overconfidence_count = sum(1 for phrase in overconfidence_keywords if phrase in message_lower)
        
        if overconfidence_count > 0:
            confidence = min(0.6 + (overconfidence_count * 0.2), 0.95)
            
            # Check if user mentioned risks at all
            risk_keywords = ['risk', 'downside', 'concern', 'worry', 'problem', 'challenge', 'threat']
            has_risk_awareness = any(kw in message_lower for kw in risk_keywords)
            
            if not has_risk_awareness and overconfidence_count >= 2:
                confidence = min(confidence + 0.15, 0.95)
            
            signals.append((UserState.OVERCONFIDENCE, confidence))
        
        # === 4. ANALYSIS PARALYSIS DETECTION ===
        paralysis_keywords = [
            'just one more', 'should i adjust', 'what if i change',
            'recalculate', 're-calculate', 'tweak', 'fine tune',
            'minor adjustment', 'small change', 'decimal point',
            'precision', 'exact', 'optimize'
        ]
        
        paralysis_count = sum(1 for phrase in paralysis_keywords if phrase in message_lower)
        
        # Check conversation length and pattern
        if len(conversation_history) > 8:
            recent_user_msgs = [
                msg for msg in conversation_history[-12:] 
                if msg.get('role') == 'user'
            ]
            
            # Check if lots of back-and-forth without decision
            if len(recent_user_msgs) > 6:
                # Check if user hasn't committed to a thesis
                decision_keywords = ['save thesis', 'buy', 'sell', 'pass', 'decided', 'going with']
                has_made_decision = any(
                    any(kw in msg.get('content', '').lower() for kw in decision_keywords)
                    for msg in recent_user_msgs
                )
                
                if not has_made_decision:
                    confidence = 0.65
                    
                    # Boost if they're asking about small details
                    detail_keywords = ['basis point', 'bps', '0.1%', '0.01', 'decimal', 'exact']
                    detail_focus = sum(
                        1 for msg in recent_user_msgs[-5:]
                        if any(kw in msg.get('content', '').lower() for kw in detail_keywords)
                    )
                    
                    if detail_focus >= 2:
                        confidence = min(confidence + 0.2, 0.9)
                    
                    if paralysis_count > 0:
                        confidence = min(confidence + 0.15, 0.95)
                    
                    signals.append((UserState.ANALYSIS_PARALYSIS, confidence))
        
        # === 5. CONFIRMATION BIAS DETECTION ===
        bull_keywords = [
            'upside', 'growth', 'opportunity', 'potential', 'bullish',
            'positive', 'strong', 'expand', 'acceleration', 'breakout',
            'winner', 'outperform', 'gains', 'moonshot'
        ]
        
        bear_keywords = [
            'risk', 'downside', 'concern', 'problem', 'bearish',
            'negative', 'weak', 'headwind', 'challenge', 'threat',
            'competition', 'vulnerability', 'warning', 'red flag'
        ]
        
        # Count in current message and recent history
        all_recent_msgs = [message] + [
            msg.get('content', '') 
            for msg in conversation_history[-8:] 
            if msg.get('role') == 'user'
        ]
        
        bull_count = sum(
            sum(1 for kw in bull_keywords if kw in msg.lower())
            for msg in all_recent_msgs
        )
        
        bear_count = sum(
            sum(1 for kw in bear_keywords if kw in msg.lower())
            for msg in all_recent_msgs
        )
        
        # Strong bias if 3x+ more bullish than bearish mentions
        if bull_count > 0 and bear_count >= 0:
            ratio = bull_count / (bear_count + 1)  # +1 to avoid division by zero
            
            if ratio >= 3.0:
                confidence = min(0.65 + (ratio - 3.0) * 0.05, 0.9)
                signals.append((UserState.CONFIRMATION_BIAS, confidence))
        
        # === RETURN HIGHEST CONFIDENCE SIGNAL ===
        if signals:
            detected_state = max(signals, key=lambda x: x[1])
            logger.info(f"Detected user state: {detected_state[0]} (confidence: {detected_state[1]:.2f})")
            logger.debug(f"All signals: {signals}")
            return detected_state
        
        return (UserState.NEUTRAL, 1.0)
        
    except Exception as e:
        logger.error(f"Error detecting user state: {e}", exc_info=True)
        return (UserState.NEUTRAL, 1.0)


def get_state_description(state: str) -> str:
    """
    Get human-readable description of detected state.
    
    Args:
        state: User state name
        
    Returns:
        Description string
    """
    descriptions = {
        UserState.FOMO: "User shows signs of FOMO (chasing recent gains)",
        UserState.PANIC: "User shows signs of panic (reacting to losses)",
        UserState.OVERCONFIDENCE: "User shows signs of overconfidence (dismissing risks)",
        UserState.ANALYSIS_PARALYSIS: "User shows signs of analysis paralysis (overthinking details)",
        UserState.CONFIRMATION_BIAS: "User shows signs of confirmation bias (focusing only on bull case)",
        UserState.NEUTRAL: "User appears neutral/balanced"
    }
    return descriptions.get(state, "Unknown state")


def should_trigger_adaptive_response(state: str, confidence: float, threshold: float = 0.6) -> bool:
    """
    Determine if adaptive response should be triggered.
    
    Args:
        state: Detected user state
        confidence: Confidence score (0-1)
        threshold: Minimum confidence to trigger adaptive response
        
    Returns:
        True if adaptive response should be used
    """
    return state != UserState.NEUTRAL and confidence >= threshold

