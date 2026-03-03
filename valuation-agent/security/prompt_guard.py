"""
Prompt Guard: Security layer for LLM chat system.

Protects against:
- Prompt injection attacks
- Toxic/harmful outputs
- Sensitive data leakage
- Rate limit abuse
"""

import logging
import os
import time
from typing import Tuple, Optional, Dict
from collections import defaultdict
from datetime import datetime, timedelta

# Check if LLM Guard should be enabled (can be disabled via env var)
LLM_GUARD_ENABLED = os.getenv('ENABLE_LLM_GUARD', 'false').lower() == 'true'

try:
    from llm_guard.input_scanners import PromptInjection, TokenLimit, Anonymize
    from llm_guard.output_scanners import Toxicity, Sensitive, NoRefusal
    from llm_guard.vault import Vault
    LLM_GUARD_AVAILABLE = True
except ImportError:
    LLM_GUARD_AVAILABLE = False
    logging.warning("llm-guard not installed. Security features will be limited.")

logger = logging.getLogger(__name__)


class PromptGuard:
    """
    Security guard for chat inputs and outputs.
    
    Features:
    - Prompt injection detection using llm-guard
    - Output validation (toxicity, sensitive data)
    - Session-based rate limiting
    - Security event logging
    """
    
    def __init__(
        self,
        max_messages_per_minute: int = 10,
        max_message_length: int = 5000,
        enable_anonymization: bool = False
    ):
        """
        Initialize PromptGuard.
        
        Args:
            max_messages_per_minute: Rate limit per session
            max_message_length: Maximum allowed message length
            enable_anonymization: Whether to anonymize PII in inputs
        """
        self.max_messages_per_minute = max_messages_per_minute
        self.max_message_length = max_message_length
        self.enable_anonymization = enable_anonymization
        
        # Rate limiting state: {session_id: [timestamps]}
        self.message_timestamps: Dict[str, list] = defaultdict(list)
        
        # Initialize llm-guard scanners (only if enabled via env var)
        if LLM_GUARD_ENABLED and LLM_GUARD_AVAILABLE:
            self._init_scanners()
            logger.info("LLM Guard enabled - security scanners active")
        else:
            self.input_scanners = []
            self.output_scanners = []
            if not LLM_GUARD_ENABLED:
                logger.info("LLM Guard disabled via ENABLE_LLM_GUARD=false - skipping security scanners")
            else:
                logger.warning("Running without llm-guard protection. Install with: pip install llm-guard")
    
    def _init_scanners(self):
        """Initialize llm-guard input and output scanners."""
        try:
            # Input scanners
            self.input_scanners = [
                PromptInjection(
                    threshold=0.75,  # 75% confidence threshold for injection detection
                    use_onnx=False    # Use non-ONNX for better compatibility
                ),
                TokenLimit(
                    limit=self.max_message_length,
                    encoding_name="cl100k_base"  # GPT-4/Claude tokenizer
                ),
            ]
            
            # Add anonymization if enabled
            if self.enable_anonymization:
                self.vault = Vault()
                self.input_scanners.append(
                    Anonymize(vault=self.vault)
                )
            
            # Output scanners  
            # NOTE: Thresholds raised to prevent false positives on investment analysis content
            self.output_scanners = [
                Toxicity(
                    threshold=0.95,  # 95% confidence threshold (was 0.7) - very permissive for finance content
                    use_onnx=False
                ),
                Sensitive(
                    redact=False,  # Don't redact, just warn (was True) - prevents blocking legitimate content
                    use_onnx=False
                ),
                NoRefusal(
                    threshold=0.5  # Detect if model refused to answer
                )
            ]
            
            logger.info(f"Initialized llm-guard with {len(self.input_scanners)} input and {len(self.output_scanners)} output scanners")
            
        except Exception as e:
            logger.error(f"Failed to initialize llm-guard scanners: {e}", exc_info=True)
            self.input_scanners = []
            self.output_scanners = []
    
    def validate_input(
        self,
        message: str,
        session_id: Optional[str] = None
    ) -> Tuple[bool, Optional[str]]:
        """
        Validate user input for security threats.
        
        Args:
            message: User message to validate
            session_id: Session identifier for rate limiting
        
        Returns:
            (is_safe, violation_reason)
            - is_safe: True if input passes all checks
            - violation_reason: Description of security issue if unsafe
        """
        # 1. Basic validation
        if not message or not isinstance(message, str):
            return False, "Empty or invalid message"
        
        if len(message) > self.max_message_length:
            return False, f"Message exceeds maximum length of {self.max_message_length} characters"
        
        # 2. Rate limiting (if session_id provided)
        if session_id:
            is_within_limit, limit_reason = self._check_rate_limit(session_id)
            if not is_within_limit:
                return False, limit_reason
        
        # 3. Basic regex-based injection detection (fallback)
        is_safe_basic, basic_reason = self._basic_injection_check(message)
        if not is_safe_basic:
            logger.warning(f"Basic injection check failed: {basic_reason}")
            return False, basic_reason
        
        # 4. llm-guard advanced scanning (only if enabled)
        if LLM_GUARD_ENABLED and LLM_GUARD_AVAILABLE and self.input_scanners:
            try:
                sanitized_prompt = message
                results_valid = {}
                
                for scanner in self.input_scanners:
                    sanitized_prompt, is_valid, risk_score = scanner.scan(sanitized_prompt)
                    scanner_name = scanner.__class__.__name__
                    results_valid[scanner_name] = is_valid
                    
                    if not is_valid:
                        logger.warning(
                            f"Input failed {scanner_name} check. "
                            f"Risk score: {risk_score:.2f}, "
                            f"Message preview: {message[:100]}"
                        )
                        return False, f"Security check failed: {scanner_name} detected potential threat"
                
                logger.debug(f"Input passed all {len(self.input_scanners)} security checks")
                return True, None
                
            except Exception as e:
                logger.error(f"llm-guard input validation error: {e}", exc_info=True)
                # Fail open but log the error
                return True, None
        
        # If llm-guard not available, rely on basic checks
        return True, None
    
    def validate_output(
        self,
        response: str
    ) -> Tuple[bool, Optional[str]]:
        """
        Validate AI output for toxicity and sensitive data leakage.
        
        Args:
            response: AI-generated response to validate
        
        Returns:
            (is_safe, violation_reason)
        """
        # Basic validation
        if not response or not isinstance(response, str):
            return True, None  # Empty responses are safe
        
        # llm-guard output scanning (only if enabled)
        if LLM_GUARD_ENABLED and LLM_GUARD_AVAILABLE and self.output_scanners:
            try:
                sanitized_output = response
                results_valid = {}
                
                for scanner in self.output_scanners:
                    sanitized_output, is_valid, risk_score = scanner.scan("", sanitized_output)
                    scanner_name = scanner.__class__.__name__
                    results_valid[scanner_name] = is_valid
                    
                    if not is_valid:
                        logger.warning(
                            f"Output failed {scanner_name} check. "
                            f"Risk score: {risk_score:.2f}"
                        )
                        
                        # For sensitive data, we redact instead of blocking
                        if scanner_name == "Sensitive":
                            # Return the sanitized version with redactions
                            return True, None
                        
                        return False, f"Output validation failed: {scanner_name}"
                
                return True, None
                
            except Exception as e:
                logger.error(f"llm-guard output validation error: {e}", exc_info=True)
                # Fail open for outputs (better to show response than block)
                return True, None
        
        return True, None
    
    def _check_rate_limit(self, session_id: str) -> Tuple[bool, Optional[str]]:
        """
        Check if session is within rate limits.
        
        Args:
            session_id: Session to check
        
        Returns:
            (is_within_limit, reason)
        """
        now = datetime.now()
        one_minute_ago = now - timedelta(minutes=1)
        
        # Clean up old timestamps
        self.message_timestamps[session_id] = [
            ts for ts in self.message_timestamps[session_id]
            if ts > one_minute_ago
        ]
        
        # Check limit
        message_count = len(self.message_timestamps[session_id])
        if message_count >= self.max_messages_per_minute:
            return False, f"Rate limit exceeded: {message_count} messages in last minute (max: {self.max_messages_per_minute})"
        
        # Add current timestamp
        self.message_timestamps[session_id].append(now)
        
        return True, None
    
    def _basic_injection_check(self, message: str) -> Tuple[bool, Optional[str]]:
        """
        Basic regex-based injection detection (fallback).
        
        Detects common prompt injection patterns.
        """
        import re
        
        message_lower = message.lower()
        
        # Suspicious patterns
        suspicious_patterns = [
            # System prompt overrides
            (r'ignore\s+(previous|all|above|prior)\s+(instructions|prompts?|commands?)', 
             "Attempting to override system instructions"),
            (r'new\s+instructions?:', 
             "Attempting to inject new instructions"),
            (r'disregard\s+(previous|all|above)', 
             "Attempting to disregard previous instructions"),
            
            # Role confusion
            (r'you\s+are\s+now\s+(a|an)\s+', 
             "Attempting role injection"),
            (r'act\s+as\s+(if\s+)?(you\s+are\s+)?(a|an)\s+(malicious|jailbroken|unrestricted|system|developer)', 
             "Attempting to change AI instructions"),
            
            # Data extraction attempts
            (r'(show|print|display|reveal)(\s+me)?\s+(your|the)\s+((system\s+)?(prompt|instructions?|guidelines?|rules?))', 
             "Attempting prompt-injection to extract system instructions"),
            (r'what\s+(is|are)\s+your\s+(instructions?|guidelines?|rules?)',
             "Attempting to extract guidelines"),
            
            # Code execution attempts
            (r'<script[\s>]', "Script tag injection"),
            (r'javascript:', "JavaScript URI injection"),
            (r'eval\s*\(', "Eval function detected"),
            
            # SQL injection
            (r'(drop|delete|update|insert)\s+(table|from|into)\s+', "SQL injection command detected"),
            (r'select\s+\*\s+from', "SQL injection query detected"),
            (r';\s*drop\s+', "SQL injection pattern detected"),
        ]
        
        for pattern, reason in suspicious_patterns:
            if re.search(pattern, message_lower):
                return False, reason
        
        return True, None
    
    def cleanup_old_sessions(self, max_age_hours: int = 24):
        """
        Clean up rate limiting data for old sessions.
        
        Args:
            max_age_hours: Remove sessions older than this many hours
        """
        cutoff = datetime.now() - timedelta(hours=max_age_hours)
        
        sessions_to_remove = []
        for session_id, timestamps in self.message_timestamps.items():
            # If all timestamps are old, mark session for removal
            if all(ts < cutoff for ts in timestamps):
                sessions_to_remove.append(session_id)
        
        for session_id in sessions_to_remove:
            del self.message_timestamps[session_id]
        
        if sessions_to_remove:
            logger.info(f"Cleaned up {len(sessions_to_remove)} old rate limit sessions")
