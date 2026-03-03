"""
Valuation Intent Extractor
Extracts user intent for DCF parameter updates from natural language
"""
import re
import logging
from typing import Dict, Any, List, Optional, Tuple

logger = logging.getLogger(__name__)


class ValuationIntentExtractor:
    """Extracts DCF parameter updates from user conversation."""
    
    # Parameter aliases for robust matching
    PARAMETER_ALIASES = {
        'revenueNextYear': [
            'revenue growth', 'revenue growth rate', 'revenue next year',
            'top line growth', 'sales growth', 'next year revenue'
        ],
        'operatingMarginNextYear': [
            'operating margin', 'operating margin next year', 'op margin',
            'ebit margin', 'operating margins', 'margin next year'
        ],
        'compoundAnnualGrowth2_5': [
            'revenue cagr', 'cagr', 'compound growth', 'growth rate years 2-5',
            'revenue growth 2-5', 'medium term growth'
        ],
        'targetPreTaxOperatingMargin': [
            'target margin', 'steady state margin', 'terminal margin',
            'target operating margin', 'long term margin', 'mature margin'
        ],
        'salesToCapitalYears1To5': [
            'sales to capital', 'sales capital ratio', 'capex efficiency',
            'capital efficiency', 'reinvestment rate'
        ],
        'salesToCapitalYears6To10': [
            'sales to capital 6-10', 'long term capital efficiency',
            'sales capital later years'
        ]
    }
    
    def extract_parameters(
        self,
        user_message: str,
        current_valuation: Dict[str, Any]
    ) -> Tuple[Dict[str, float], str, List[str]]:
        """
        Extract parameter updates from user message.
        
        Args:
            user_message: User's natural language request
            current_valuation: Current valuation data for context
            
        Returns:
            Tuple of (parameter_updates, intent_summary, assumptions)
        """
        updates = {}
        assumptions = []
        
        message_lower = user_message.lower()
        
        # Extract numeric values and their context
        # Improved patterns to catch more variations
        numeric_patterns = [
            # Pattern 1: "increase revenue by 5%" or "set revenue to 25%"
            (r'(?:increase|raise|boost|up|set|change|adjust|make|put)\s+(\w+(?:\s+\w+)*?)\s+(?:by|to|at|is|=)\s+(\d+\.?\d*)%?', 'param_first'),
            # Pattern 2: "decrease revenue by 5%" or "lower margin to 25%"
            (r'(?:decrease|reduce|lower|down|cut)\s+(\w+(?:\s+\w+)*?)\s+(?:by|to|at|is|=)\s+(\d+\.?\d*)%?', 'param_first'),
            # Pattern 3: "revenue growth is 25%" or "margin of 30%"
            (r'(\w+(?:\s+\w+)*?)\s+(?:is|are|of|at|to|=)\s+(\d+\.?\d*)%?', 'param_first'),
            # Pattern 4: "25% revenue growth" or "30% margin" - value first
            (r'(\d+\.?\d*)%?\s+(?:revenue|margin|cagr|growth|capital|sales)\s+(\w+(?:\s+\w+)*?)?', 'value_first'),
            # Pattern 5: "what if revenue is 25%" or "suppose margin is 30%"
            (r'(?:what\s+if|suppose|assume|let\'?s|imagine)\s+(\w+(?:\s+\w+)*?)\s+(?:is|are|at|to|=)\s+(\d+\.?\d*)%?', 'param_first'),
        ]
        
        for pattern_tuple in numeric_patterns:
            pattern, order = pattern_tuple
            matches = re.finditer(pattern, message_lower)
            for match in matches:
                if len(match.groups()) < 2:
                    continue
                
                if order == 'param_first':
                    param_text = match.group(1)
                    value_text = match.group(2)
                else:  # value_first
                    value_text = match.group(1)
                    param_text = match.group(2) if match.lastindex >= 2 else ''
                
                # Try to match to known parameters
                param_name = self._match_parameter(param_text) if param_text else None
                
                # If param_text didn't match, try to infer from context
                if not param_name and order == 'value_first':
                    # Extract context words from the pattern match
                    full_match = match.group(0)
                    param_name = self._match_parameter(full_match)
                
                if param_name:
                    try:
                        value = float(value_text)
                        
                        # Handle relative changes (increase/decrease by X%)
                        if any(word in message_lower for word in ['increase', 'raise', 'boost']):
                            current_val = self._get_current_value(param_name, current_valuation)
                            if current_val:
                                value = current_val + value
                                assumptions.append(f"Increased {param_name} by {value_text}% from current {current_val}%")
                        elif any(word in message_lower for word in ['decrease', 'reduce', 'lower', 'cut']):
                            current_val = self._get_current_value(param_name, current_valuation)
                            if current_val:
                                value = current_val - value
                                assumptions.append(f"Decreased {param_name} by {value_text}% from current {current_val}%")
                        
                        updates[param_name] = value
                        logger.debug(f"Extracted parameter: {param_name} = {value}% from message: {param_text}")
                        
                    except ValueError:
                        logger.warning(f"Could not parse numeric value: {value_text}")
        
        # Generate intent summary
        intent_summary = self._generate_summary(updates, user_message)
        
        # Add assumption about margin interpretation
        if any('margin' in k.lower() for k in updates.keys()):
            assumptions.append("Interpreted 'margins' as operating margins (not net margins, which are not supported)")
        
        return updates, intent_summary, assumptions
    
    def _match_parameter(self, text: str) -> Optional[str]:
        """Match text to parameter name using aliases."""
        text_clean = text.strip().lower()
        
        # Remove common words that don't help matching
        text_clean = re.sub(r'\b(the|a|an|is|are|at|to|of|for|with|by)\b', '', text_clean)
        text_clean = text_clean.strip()
        
        # Check each parameter's aliases
        for param_name, aliases in self.PARAMETER_ALIASES.items():
            for alias in aliases:
                alias_lower = alias.lower()
                # Check if alias is contained in text or vice versa
                if alias_lower in text_clean or text_clean in alias_lower:
                    return param_name
                # Also check word-by-word matching
                if any(word in text_clean.split() for word in alias_lower.split() if len(word) > 3):
                    return param_name
        
        # Direct keyword matching for common cases
        if 'revenue' in text_clean and ('growth' in text_clean or 'next year' in text_clean or 'next' in text_clean):
            return 'revenueNextYear'
        if 'revenue' in text_clean and ('cagr' in text_clean or 'compound' in text_clean or '2-5' in text_clean or '2 to 5' in text_clean):
            return 'compoundAnnualGrowth2_5'
        if ('operating' in text_clean or 'op' in text_clean) and 'margin' in text_clean:
            if 'target' in text_clean or 'steady' in text_clean or 'long term' in text_clean:
                return 'targetPreTaxOperatingMargin'
            return 'operatingMarginNextYear'
        if 'sales' in text_clean and 'capital' in text_clean:
            if '6-10' in text_clean or '6 to 10' in text_clean or 'later' in text_clean:
                return 'salesToCapitalYears6To10'
            return 'salesToCapitalYears1To5'
        
        return None
    
    def _get_current_value(self, param_name: str, valuation: Dict[str, Any]) -> Optional[float]:
        """Get current value of parameter from valuation data."""
        # Try multiple data structure paths
        if not valuation:
            return None
        
        # Try various paths where companyDriveDataDTO might be located
        company_drive = None
        
        # Path 1: Direct access
        company_drive = valuation.get('companyDriveDataDTO', {})
        
        # Path 2: Inside results/dcf
        if not company_drive:
            results = valuation.get('results', {})
            if results:
                company_drive = results.get('companyDriveDataDTO', {})
                if not company_drive:
                    dcf = results.get('dcf', {})
                    if dcf:
                        company_drive = dcf.get('companyDriveDataDTO', {})
        
        # Path 3: Inside merged_result
        if not company_drive:
            merged_result = valuation.get('merged_result', {})
            if merged_result:
                company_drive = merged_result.get('companyDriveDataDTO', {})
        
        # Path 4: Inside dcf_analysis
        if not company_drive:
            dcf_analysis = valuation.get('dcf_analysis', {})
            if dcf_analysis:
                company_drive = dcf_analysis.get('companyDriveDataDTO', {})
        
        # Path 5: Inside contextData.results
        if not company_drive:
            context_data = valuation.get('contextData', {})
            if context_data:
                results = context_data.get('results', {})
                if results:
                    company_drive = results.get('companyDriveDataDTO', {})
        
        if not company_drive:
            logger.debug(f"Could not find companyDriveDataDTO in valuation structure")
            return None
        
        param_mapping = {
            'revenueNextYear': company_drive.get('revenueNextYear'),
            'operatingMarginNextYear': company_drive.get('operatingMarginNextYear'),
            'compoundAnnualGrowth2_5': company_drive.get('compoundAnnualGrowth2_5'),
            'targetPreTaxOperatingMargin': company_drive.get('targetPreTaxOperatingMargin'),
            'salesToCapitalYears1To5': company_drive.get('salesToCapitalYears1To5'),
            'salesToCapitalYears6To10': company_drive.get('salesToCapitalYears6To10'),
        }
        
        value = param_mapping.get(param_name)
        if value is not None:
            # Convert from percentage to decimal if needed (assuming stored as 0.15 for 15%)
            if isinstance(value, (int, float)) and value < 1:
                return value * 100
            return value
        
        return None
    
    def _generate_summary(self, updates: Dict[str, float], original_message: str) -> str:
        """Generate human-readable summary of changes."""
        if not updates:
            return "No parameter changes detected"
        
        summary_parts = []
        for param, value in updates.items():
            friendly_name = param.replace('_', ' ').replace('Next Year', '').title()
            summary_parts.append(f"{friendly_name}: {value}%")
        
        return f"Updating: {', '.join(summary_parts)}"


# Validation functions
def validate_parameter_ranges(updates: Dict[str, float]) -> Tuple[bool, List[str]]:
    """
    Validate that parameter values are within reasonable ranges.
    
    Returns:
        Tuple of (is_valid, error_messages)
    """
    errors = []
    
    # Define reasonable ranges
    ranges = {
        'revenueNextYear': (-50, 200),  # -50% to 200% growth
        'operatingMarginNextYear': (-20, 60),  # -20% to 60% margin
        'compoundAnnualGrowth2_5': (-30, 100),
        'targetPreTaxOperatingMargin': (0, 60),
        'salesToCapitalYears1To5': (10, 500),
        'salesToCapitalYears6To10': (10, 500)
    }
    
    for param, value in updates.items():
        if param in ranges:
            min_val, max_val = ranges[param]
            if not (min_val <= value <= max_val):
                errors.append(
                    f"{param}: {value}% is outside reasonable range "
                    f"({min_val}% to {max_val}%)"
                )
    
    return len(errors) == 0, errors

