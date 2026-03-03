"""
Conviction Tracking Service
Tracks user conviction through behavioral signals and LLM assessment during Socratic conversation
"""
import logging
import re
from typing import Dict, Any, List, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


def calculate_conviction_score(
    conversation_history: List[Dict[str, Any]],
    session: Dict[str, Any],
    ai_service
) -> Dict[str, Any]:
    """
    Calculate user's conviction score based on behavioral signals and LLM assessment.
    
    Scoring system:
    - Behavioral signals: 0-4 points
    - LLM assessment: 0-6 points
    - Total: 0-10 scale
    
    Args:
        conversation_history: List of conversation messages
        session: Current session data with metadata
        ai_service: AI service for LLM-based assessment
    
    Returns:
        Dict with:
            - score: int (0-10) - Total conviction score
            - behavioral: int (0-4) - Behavioral component
            - llm_assessed: int (0-6) - LLM component
            - signals: Dict - Detailed signal breakdown
    """
    if not conversation_history or len(conversation_history) < 4:
        return {
            'score': 0,
            'behavioral': 0,
            'llm_assessed': 0,
            'signals': {
                'reason': 'Conversation too short',
                'message_count': len(conversation_history)
            }
        }
    
    # Extract messages
    user_messages = [msg for msg in conversation_history if msg.get('role') == 'user']
    
    if len(user_messages) < 2:
        return {
            'score': 0,
            'behavioral': 0,
            'llm_assessed': 0,
            'signals': {
                'reason': 'Insufficient user messages',
                'user_message_count': len(user_messages)
            }
        }
    
    # === BEHAVIORAL SCORING (0-4 points) ===
    behavioral_signals = _calculate_behavioral_signals(conversation_history, session)
    behavioral_score = min(behavioral_signals['total_score'], 4)
    
    # === LLM ASSESSMENT (0-6 points) ===
    llm_score = _llm_conviction_assessment(
        conversation_history,
        session,
        ai_service,
        behavioral_signals
    )
    
    # === TOTAL SCORE ===
    total_score = min(behavioral_score + llm_score, 10)
    
    logger.info(
        f"Conviction score calculated: total={total_score}/10 "
        f"(behavioral={behavioral_score}, llm={llm_score})"
    )
    
    return {
        'score': total_score,
        'behavioral': behavioral_score,
        'llm_assessed': llm_score,
        'signals': behavioral_signals
    }


def _calculate_behavioral_signals(
    conversation_history: List[Dict[str, Any]],
    session: Dict[str, Any]
) -> Dict[str, Any]:
    """
    Calculate behavioral conviction signals from conversation.
    
    Signals tracked:
    1. Parameter changes discussed/approved (max 1 point)
    2. Risks acknowledged (max 1 point)
    3. Catalysts mentioned (max 1 point)
    4. Timeframe specified (max 1 point)
    
    Returns:
        Dict with signal counts and total score
    """
    # Combine all text for analysis
    all_text = ' '.join([
        msg.get('content', '') for msg in conversation_history
        if isinstance(msg.get('content'), str)
    ]).lower()
    
    signals = {
        'parameter_changes': 0,
        'risks_discussed': 0,
        'catalysts_mentioned': 0,
        'timeframe_specified': False,
        'total_score': 0
    }
    
    # 1. Parameter changes (from session or conversation)
    pending_params = session.get('pending_parameter_changes', {})
    param_mentions = len(re.findall(
        r'(revenue growth|margin|operating margin|sales to capital|wacc|growth rate)',
        all_text
    ))
    signals['parameter_changes'] = len(pending_params) + (1 if param_mentions >= 2 else 0)
    if signals['parameter_changes'] >= 2:
        signals['total_score'] += 1
    
    # 2. Risks discussed
    risk_terms = [
        'risk', 'threat', 'downside', 'concern', 'worry',
        'challenge', 'bear case', 'what if', 'could fail'
    ]
    signals['risks_discussed'] = sum(1 for term in risk_terms if term in all_text)
    if signals['risks_discussed'] >= 2:
        signals['total_score'] += 1
    
    # 3. Catalysts mentioned
    catalyst_terms = [
        'catalyst', 'opportunity', 'upside', 'driver',
        'bull case', 'tailwind', 'growth driver', 'expansion'
    ]
    signals['catalysts_mentioned'] = sum(1 for term in catalyst_terms if term in all_text)
    if signals['catalysts_mentioned'] >= 2:
        signals['total_score'] += 1
    
    # 4. Timeframe specified
    timeframe_patterns = [
        r'\d+\s*years?',
        r'short[\s-]?term',
        r'medium[\s-]?term',
        r'long[\s-]?term',
        r'timeframe',
        r'horizon',
        r'quarters?',
    ]
    signals['timeframe_specified'] = any(
        re.search(pattern, all_text) for pattern in timeframe_patterns
    )
    if signals['timeframe_specified']:
        signals['total_score'] += 1
    
    return signals


def _llm_conviction_assessment(
    conversation_history: List[Dict[str, Any]],
    session: Dict[str, Any],
    ai_service,
    behavioral_signals: Dict[str, Any]
) -> int:
    """
    Use LLM to assess user's conviction level from conversation tone and content.
    
    Returns:
        int (0-6) - LLM-assessed conviction score
    """
    if not ai_service:
        logger.warning("No AI service provided for conviction assessment, returning 0")
        return 0
    
    try:
        # Format recent conversation (last 8 messages)
        recent_messages = conversation_history[-8:]
        conversation_text = "\n\n".join([
            f"{msg.get('role', 'unknown').upper()}: {msg.get('content', '')[:500]}"
            for msg in recent_messages
            if isinstance(msg.get('content'), str)
        ])
        
        ticker = session.get('context', {}).get('ticker', 'unknown')
        
        # Build assessment prompt
        assessment_prompt = f"""Analyze this investment conversation about {ticker} and assess the user's conviction level.

CONVERSATION:
{conversation_text}

BEHAVIORAL SIGNALS DETECTED:
- Parameter changes discussed: {behavioral_signals['parameter_changes']}
- Risks acknowledged: {behavioral_signals['risks_discussed']}
- Catalysts mentioned: {behavioral_signals['catalysts_mentioned']}
- Timeframe specified: {behavioral_signals['timeframe_specified']}

TASK:
Rate the user's conviction level on a 0-6 scale based on:
1. Confidence in their statements (certain vs uncertain language)
2. Depth of reasoning (superficial vs detailed analysis)
3. Willingness to commit (exploratory vs decisive)
4. Response to challenges (defensive vs thoughtful)

SCALE:
0 = No conviction (just exploring, highly uncertain)
1 = Very low (many doubts, unclear thesis)
2 = Low (some doubts, weak thesis)
3 = Moderate (balanced, developing thesis)
4 = Good (confident, clear thesis)
5 = Strong (very confident, well-reasoned thesis)
6 = Very strong (highly confident, comprehensive thesis)

Return ONLY a JSON object:
{{
  "score": <0-6>,
  "reasoning": "<1-2 sentence explanation>"
}}"""
        
        # Call LLM
        response = ai_service.chat(
            messages=[{'role': 'user', 'content': assessment_prompt}],
            max_tokens=150,
            temperature=0.0
        )
        
        content = response.get('content', '')
        if isinstance(content, list):
            content = ' '.join([
                item.get('text', '') if isinstance(item, dict) else str(item)
                for item in content
            ])
        
        # Extract JSON
        import json
        json_match = re.search(r'\{.*\}', content, re.DOTALL)
        if json_match:
            result = json.loads(json_match.group())
            score = int(result.get('score', 0))
            score = max(0, min(6, score))  # Clamp to 0-6
            
            logger.info(
                f"LLM conviction assessment: {score}/6 - {result.get('reasoning', 'N/A')}"
            )
            return score
        else:
            logger.warning(f"Failed to extract JSON from LLM response: {content[:200]}")
            return 3  # Default to moderate
            
    except Exception as e:
        logger.error(f"Error in LLM conviction assessment: {e}", exc_info=True)
        return 3  # Default to moderate on error


def has_conviction_increased(
    session: Dict[str, Any],
    threshold: int = 7,
    min_increase: int = 2
) -> bool:
    """
    Check if conviction has increased significantly since last hypothesis prompt.
    
    Args:
        session: Session data with conviction_history
        threshold: Minimum conviction score required
        min_increase: Minimum increase from last prompt
    
    Returns:
        bool - True if conviction has increased enough to re-prompt
    """
    conviction_history = session.get('conviction_history', [])
    current_conviction = session.get('current_conviction', 0)
    last_prompt_count = session.get('hypothesis_prompt_count', 0)
    
    if last_prompt_count == 0:
        # First attempt - check threshold only
        return current_conviction >= threshold
    
    if not conviction_history:
        return False
    
    # Find conviction at last prompt
    # Assume last prompt was at (prompt_count - 1) * average_messages_per_prompt
    last_prompt_conviction = 0
    if len(conviction_history) > 0:
        # Get conviction from middle of history (rough estimate of last prompt time)
        mid_point = len(conviction_history) // 2
        last_prompt_conviction = conviction_history[mid_point].get('score', 0)
    
    increase = current_conviction - last_prompt_conviction
    
    logger.info(
        f"Conviction check: current={current_conviction}, "
        f"last_prompt={last_prompt_conviction}, increase={increase}, "
        f"threshold={threshold}, meets_threshold={current_conviction >= threshold}, "
        f"sufficient_increase={increase >= min_increase}"
    )
    
    return current_conviction >= threshold and increase >= min_increase

