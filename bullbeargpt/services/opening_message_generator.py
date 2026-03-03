"""
Opening Message Generator Service.
Generates contextual opening messages for chat sessions based on valuation data.
"""
from typing import Dict, Any, List, Optional
from dataclasses import dataclass

# Import tension-based generator for rich opening questions
from services.opening_question_generator import generate_opening_suggestions


@dataclass
class ChatSuggestion:
    """Suggestion chip for user actions."""
    icon: str
    text: str
    
    def to_dict(self) -> Dict[str, str]:
        return {"icon": self.icon, "text": self.text}


@dataclass
class OpeningMessage:
    """Opening message with suggestions."""
    message: str
    suggestions: List[ChatSuggestion]
    
    def to_dict(self) -> Dict[str, Any]:
        return {
            "opening_message": self.message,
            "suggestions": [s.to_dict() for s in self.suggestions]
        }


class OpeningMessageGenerator:
    """
    Generates contextual opening messages based on valuation data.
    Uses tension-based generator for provocative, engaging opening questions.
    Falls back to LLM or templates if tension-based generation fails.
    """
    
    # Default suggestions when no valuation context
    DEFAULT_SUGGESTIONS = [
        ChatSuggestion("", "How do you value a company?"),
        ChatSuggestion("", "Explain DCF analysis"),
        ChatSuggestion("", "What affects intrinsic value?"),
        ChatSuggestion("", "Help me understand key metrics"),
    ]
    
    # Context-aware suggestions based on valuation
    VALUATION_SUGGESTIONS = {
        "undervalued": [
            ChatSuggestion("", "Why is {ticker} undervalued?"),
            ChatSuggestion("", "Show bull vs bear scenarios"),
            ChatSuggestion("", "What are the key risks?"),
            ChatSuggestion("", "Explain key assumptions"),
        ],
        "overvalued": [
            ChatSuggestion("", "Why is {ticker} overvalued?"),
            ChatSuggestion("", "What would justify the current price?"),
            ChatSuggestion("", "What could go wrong for shorts?"),
            ChatSuggestion("", "Compare bull vs bear scenarios"),
        ],
        "fairly_valued": [
            ChatSuggestion("", "Is {ticker} fairly priced?"),
            ChatSuggestion("", "Show scenario analysis"),
            ChatSuggestion("", "What are the key drivers?"),
            ChatSuggestion("", "What would change your view?"),
        ],
    }
    
    def __init__(
        self,
        use_llm: bool = True,
        openai_api_key: Optional[str] = None,
        anthropic_api_key: Optional[str] = None,
    ):
        self.use_llm = use_llm
        # Legacy args kept for compatibility with older call sites.
        del openai_api_key, anthropic_api_key
    
    def generate(
        self,
        ticker: Optional[str] = None,
        valuation_data: Optional[Dict[str, Any]] = None,
        company_name: Optional[str] = None,
    ) -> OpeningMessage:
        """
        Generate an opening message with suggestions.
        
        Uses tension-based generator for rich, engaging opening questions.
        Falls back to LLM or templates if tension-based generation fails.
        
        Args:
            ticker: Stock ticker symbol
            valuation_data: DCF valuation results
            company_name: Company name (optional)
            
        Returns:
            OpeningMessage with greeting and suggestions
        """
        if not ticker or not valuation_data:
            return self._generate_default_opening()
        
        # Try tension-based generation first (preferred)
        try:
            # Use the comprehensive tension-based generator
            suggestions_data = generate_opening_suggestions(
                ticker=ticker,
                valuation_data=valuation_data,
                company_name=company_name,
                num_suggestions=4
            )
            
            message = suggestions_data.get('primary_question', '')
            raw_suggestions = suggestions_data.get('suggestions', [])
            
            if message:
                # Convert suggestions to ChatSuggestion objects
                suggestions = [
                    ChatSuggestion(icon=s.get('icon', ''), text=s.get('text', ''))
                    for s in raw_suggestions
                ]
                return OpeningMessage(message=message, suggestions=suggestions or self.DEFAULT_SUGGESTIONS)
        except Exception as e:
            print(f"Tension-based opening generation failed: {e}")
        
        # Fallback to original logic
        intrinsic_value = valuation_data.get('intrinsicValue') or valuation_data.get('intrinsic_value')
        current_price = valuation_data.get('currentPrice') or valuation_data.get('current_price')
        upside = valuation_data.get('upside')
        
        if not intrinsic_value or not current_price:
            return self._generate_default_opening(ticker)
        
        # Calculate upside if not provided
        if upside is None:
            upside = ((intrinsic_value - current_price) / current_price) * 100
        
        # Determine valuation stance
        if upside > 10:
            stance = "undervalued"
            stance_text = f"trades {abs(upside):.1f}% below"
        elif upside < -10:
            stance = "overvalued"
            stance_text = f"trades {abs(upside):.1f}% above"
        else:
            stance = "fairly_valued"
            stance_text = f"trades within {abs(upside):.1f}% of"
        
        # Use template-based message for fallback
        message = self._generate_template_opening(ticker, stance_text, intrinsic_value, current_price, upside)
        suggestions = self._get_contextual_suggestions(ticker, stance)
        return OpeningMessage(message=message, suggestions=suggestions)
    
    def _generate_default_opening(self, ticker: Optional[str] = None) -> OpeningMessage:
        """Generate default opening without valuation context."""
        if ticker:
            message = f"I'm ready to discuss {ticker}. What would you like to know about the company?"
        else:
            message = "Hello! I'm your AI valuation assistant. How can I help you today?"
        
        return OpeningMessage(message=message, suggestions=self.DEFAULT_SUGGESTIONS)
    
    def _generate_template_opening(
        self,
        ticker: str,
        stance_text: str,
        intrinsic_value: float,
        current_price: float,
        upside: float,
    ) -> str:
        """Generate template-based opening message."""
        return (
            f"{ticker} {stance_text} my DCF estimate of ${intrinsic_value:.2f}. "
            f"At ${current_price:.2f}, the stock shows {upside:+.1f}% potential. "
            f"What would you like to explore?"
        )
    
    def _get_contextual_suggestions(self, ticker: str, stance: str) -> List[ChatSuggestion]:
        """Get suggestions based on valuation stance."""
        templates = self.VALUATION_SUGGESTIONS.get(stance, self.DEFAULT_SUGGESTIONS)
        return [
            ChatSuggestion(
                icon=s.icon,
                text=s.text.format(ticker=ticker)
            )
            for s in templates
        ]
    
# Factory function
def get_opening_generator(use_llm: bool = True) -> OpeningMessageGenerator:
    """Get configured opening message generator."""
    return OpeningMessageGenerator(use_llm=use_llm)
