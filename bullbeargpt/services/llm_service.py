"""
LLM Service for investment reasoning chat.
Supports Anthropic Claude, OpenAI, Groq, Gemini, and OpenRouter with streaming.
Model defaults and provider config come from shared/llm_models.py — no hardcoded strings here.
"""
import json
import os
import sys
from datetime import datetime
from typing import Dict, Any, Optional, Generator, List
from dataclasses import dataclass
from enum import Enum

# Resolve repo root so we can import shared/llm_models
_repo_root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
if _repo_root not in sys.path:
    sys.path.insert(0, _repo_root)

from shared.llm_models import (
    get_default_model,
    get_provider_for_role,
    get_model_for_role,
    PROVIDER_REGISTRY,
)

# Import prompt dumper for debugging/cost analysis
from prompts.utils import get_prompt_dumper


class IntentType(Enum):
    """Types of user intents."""
    CHALLENGE_ASSUMPTION = "challenge_assumption"
    REQUEST_CALIBRATION = "request_calibration"
    RUN_ANALYSIS = "run_analysis"
    COMPARE_SCENARIOS = "compare_scenarios"
    SAVE_SCENARIO = "save_scenario"
    GENERAL_QUESTION = "general_question"
    BULL_BEAR_DISCUSSION = "bull_bear_discussion"


@dataclass
class Intent:
    """Classified user intent."""
    type: IntentType
    parameters: Dict[str, Any]
    confidence: float
    
    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> 'Intent':
        """Create Intent from dictionary."""
        intent_type = data.get('type', 'general_question')
        try:
            intent_enum = IntentType(intent_type)
        except ValueError:
            intent_enum = IntentType.GENERAL_QUESTION
            
        return cls(
            type=intent_enum,
            parameters=data.get('parameters', {}),
            confidence=data.get('confidence', 0.5),
        )


# Valid provider literals (extensible)
LLMProvider = str  # 'claude' | 'openai' | 'groq' | 'gemini' | 'openrouter'


class LLMService:
    """
    Service for LLM-powered analysis.
    Provider and model are resolved from environment variables at startup.
    Supports: Anthropic Claude, OpenAI, Groq (Llama), Gemini, OpenRouter.
    """

    def __init__(
        self,
        default_provider: Optional[str] = None,
        model_override: Optional[str] = None,
    ):
        """
        Initialize with auto-detected provider and latest-by-default model.

        Args:
            default_provider: Override the auto-detected provider (useful in tests).
            model_override: Force a specific model (overrides LLM_MODEL env var too).
        """
        self.default_provider = (default_provider or get_provider_for_role("agent")).lower()

        # Resolve model for each supported provider
        self._models: Dict[str, str] = {
            p: model_override or get_model_for_role("agent", p)
            for p in PROVIDER_REGISTRY
        }

        # Build clients lazily (only create what's configured)
        self._clients: Dict[str, Any] = {}
        self._init_clients()

    # --- convenience properties (backward compat) ---
    @property
    def openai_model(self) -> str:
        return self._models.get('openai', get_default_model('openai'))

    @property
    def claude_model(self) -> str:
        return self._models.get('claude', get_default_model('claude'))

    @property
    def openai_api_key(self) -> str:
        return os.getenv('OPENAI_API_KEY', '')

    @property
    def anthropic_api_key(self) -> str:
        return os.getenv('ANTHROPIC_API_KEY', '')

    @property
    def openai_client(self):
        provider = self.default_provider
        if self._is_anthropic_provider(provider):
            return None
        return self._provider_client(provider)

    @property
    def anthropic_client(self):
        provider = self.default_provider
        if not self._is_anthropic_provider(provider):
            return None
        return self._provider_client(provider)

    def _init_clients(self):
        """Initialize SDK clients for all configured providers."""
        for provider, cfg in PROVIDER_REGISTRY.items():
            api_key = os.getenv(cfg['api_key_env'], '').strip()
            if not api_key:
                continue
            try:
                backend = cfg['backend']
                if backend == 'anthropic':
                    import anthropic as _anthropic
                    self._clients[provider] = _anthropic.Anthropic(api_key=api_key)
                elif backend in ('openai', 'openai_compat'):
                    from openai import OpenAI
                    kwargs: Dict[str, Any] = {'api_key': api_key}
                    base_url = cfg.get('base_url')
                    # Check env override for base_url
                    if cfg.get('base_url_env'):
                        base_url = os.getenv(cfg['base_url_env'], '').strip() or base_url
                    if base_url:
                        kwargs['base_url'] = base_url

                    # Workaround for httpx incompatibility: newer httpx versions removed
                    # the 'proxies' constructor arg that the OpenAI SDK passes internally.
                    # Creating an explicit http_client avoids the issue entirely.
                    try:
                        import httpx
                        kwargs['http_client'] = httpx.Client()
                    except Exception:
                        pass  # httpx not available — let OpenAI SDK manage its own client

                    self._clients[provider] = OpenAI(**kwargs)
            except Exception as e:
                import logging
                logging.getLogger(__name__).warning(
                    "Could not init client for provider '%s': %s", provider, e
                )


    def _normalize_provider(self, provider: Optional[LLMProvider]) -> str:
        return (provider or self.default_provider).lower()

    def _provider_backend(self, provider: Optional[LLMProvider]) -> str:
        provider_key = self._normalize_provider(provider)
        cfg = PROVIDER_REGISTRY.get(provider_key, {})
        return str(cfg.get("backend", "openai")).lower()

    def _is_anthropic_provider(self, provider: Optional[LLMProvider]) -> bool:
        return self._provider_backend(provider) == "anthropic"

    def _provider_model(self, provider: Optional[LLMProvider], model_override: Optional[str] = None) -> str:
        if model_override:
            return model_override
        provider_key = self._normalize_provider(provider)
        return self._models.get(provider_key, get_default_model(provider_key))

    def _provider_client(self, provider: Optional[LLMProvider]):
        provider_key = self._normalize_provider(provider)
        return self._clients.get(provider_key)

    def _invoke_text_completion(
        self,
        prompt: str,
        provider: Optional[LLMProvider],
        max_tokens: int,
        temperature: float = 0.2,
    ) -> str:
        """Run a single-turn text completion on the selected provider."""
        provider_key = self._normalize_provider(provider)
        client = self._provider_client(provider_key)
        selected_model = self._provider_model(provider_key)
        if client is None:
            raise RuntimeError(f"LLM client unavailable for provider={provider_key}")

        if self._is_anthropic_provider(provider_key):
            response = client.messages.create(
                model=selected_model,
                max_tokens=max_tokens,
                messages=[{"role": "user", "content": prompt}],
            )
            return response.content[0].text.strip()

        response = client.chat.completions.create(
            model=selected_model,
            messages=[{"role": "user", "content": prompt}],
            temperature=temperature,
            max_tokens=max_tokens,
        )
        return (response.choices[0].message.content or "").strip()
    
    def is_available(self, provider: Optional[LLMProvider] = None) -> bool:
        """Check if the specified provider is available (i.e., client was initialised)."""
        provider = self._normalize_provider(provider)
        return provider in self._clients
    
    def stream_chat(
        self,
        messages: List[Dict[str, Any]],
        system_prompt: str = "",
        model: Optional[str] = None,
        temperature: float = 0.7,
        max_tokens: int = 2048,
        provider: Optional[LLMProvider] = None
    ) -> Generator[str, None, None]:
        """
        Stream a chat response using conversation history.
        
        This is the main interface for the MessageHandler to generate responses.
        
        Args:
            messages: List of conversation messages with 'role' and 'content'
            system_prompt: System prompt for context
            model: Optional model override
            temperature: Generation temperature
            max_tokens: Maximum tokens to generate
            provider: LLM provider to use
            
        Yields:
            Response text chunks
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            yield "I'm sorry, but the AI service is currently unavailable. Please try again later."
            return
        
        try:
            if self._is_anthropic_provider(provider):
                yield from self._stream_chat_claude(
                    messages, system_prompt, model, temperature, max_tokens, provider
                )
            else:
                yield from self._stream_chat_openai(
                    messages, system_prompt, model, temperature, max_tokens, provider
                )
        except Exception as e:
            print(f"Stream chat error: {e}")
            yield f"I encountered an error while generating a response: {str(e)}"
    
    def _stream_chat_claude(
        self,
        messages: List[Dict[str, Any]],
        system_prompt: str,
        model: Optional[str],
        temperature: float,
        max_tokens: int,
        provider: Optional[LLMProvider],
    ) -> Generator[str, None, None]:
        """Stream chat using Claude."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider, model)
        if client is None:
            raise RuntimeError(f"Anthropic client unavailable for provider={provider}")

        # Convert messages to Claude format
        claude_messages = []
        for msg in messages:
            role = msg.get('role', 'user')
            content = msg.get('content', '')
            # Claude only accepts 'user' and 'assistant' roles
            if role in ['user', 'assistant'] and content:
                claude_messages.append({"role": role, "content": content})
        
        # Ensure alternating roles
        if not claude_messages:
            claude_messages = [{"role": "user", "content": "Hello"}]
        
        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_messages(
            agent_name="stream_chat_claude",
            messages=claude_messages,
            system_prompt=system_prompt,
            response="",  # Will be updated after streaming
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
                "temperature": temperature,
                "max_tokens": max_tokens,
            }
        )
        
        # Collect full response for dumping
        full_response = []
        
        with client.messages.stream(
            model=selected_model,
            max_tokens=max_tokens,
            system=system_prompt,
            messages=claude_messages,
        ) as stream:
            for text in stream.text_stream:
                full_response.append(text)
                yield text
        
        # Dump response after streaming completes
        if prompt_timestamp:
            dumper.dump_response(
                agent_name="stream_chat_claude",
                response="".join(full_response),
                timestamp=prompt_timestamp,
                metadata={
                    "provider": self._normalize_provider(provider),
                    "model": selected_model,
                }
            )
    
    def _stream_chat_openai(
        self,
        messages: List[Dict[str, Any]],
        system_prompt: str,
        model: Optional[str],
        temperature: float,
        max_tokens: int,
        provider: Optional[LLMProvider],
    ) -> Generator[str, None, None]:
        """Stream chat using OpenAI."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider, model)
        if client is None:
            raise RuntimeError(f"OpenAI-compatible client unavailable for provider={provider}")

        # Build OpenAI messages
        openai_messages = []
        if system_prompt:
            openai_messages.append({"role": "system", "content": system_prompt})
        
        for msg in messages:
            role = msg.get('role', 'user')
            content = msg.get('content', '')
            if content:
                openai_messages.append({"role": role, "content": content})
        
        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_messages(
            agent_name="stream_chat_openai",
            messages=openai_messages,
            system_prompt=system_prompt,
            response="",  # Will be updated after streaming
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
                "temperature": temperature,
                "max_tokens": max_tokens,
            }
        )
        
        # Collect full response for dumping
        full_response = []
        
        response = client.chat.completions.create(
            model=selected_model,
            messages=openai_messages,
            temperature=temperature,
            max_tokens=max_tokens,
            stream=True,
        )
        
        for chunk in response:
            if chunk.choices[0].delta.content:
                full_response.append(chunk.choices[0].delta.content)
                yield chunk.choices[0].delta.content
        
        # Dump response after streaming completes
        if prompt_timestamp:
            dumper.dump_response(
                agent_name="stream_chat_openai",
                response="".join(full_response),
                timestamp=prompt_timestamp,
                metadata={
                    "provider": self._normalize_provider(provider),
                    "model": selected_model,
                }
            )

    
    def get_available_providers(self) -> List[Dict[str, Any]]:
        """Get list of available (configured) providers."""
        providers = []
        for provider, client in self._clients.items():
            cfg = PROVIDER_REGISTRY.get(provider, {})
            providers.append({
                'id': provider,
                'name': cfg.get('display_name', provider),
                'current_model': self._models.get(provider, ''),
                'active': provider == self.default_provider,
            })
        return providers

    def set_provider(self, provider: str, model: Optional[str] = None):
        """Set the active provider and optionally override its model."""
        self.default_provider = provider.lower()
        if model:
            self._models[self.default_provider] = model
    
    def classify_intent(
        self, 
        message: str, 
        context: Dict[str, Any],
        recent_cells: List[Dict[str, Any]] = None,
        provider: Optional[LLMProvider] = None
    ) -> Intent:
        """
        Classify the user's intent from their message.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            return self._fallback_intent_classification(message)
        
        recent_context = ""
        if recent_cells:
            recent_context = "\n".join([
                f"- {c.get('author_type', 'ai')}: {c.get('content', {}).get('message', '')[:200]}"
                for c in recent_cells[-5:]
            ])
        
        prompt = f"""You are an AI assistant helping classify user intents in an investment analysis chat.

Given this user message in a DCF valuation analysis chat:
"{message}"

Current analysis context:
- Company: {context.get('company_name', 'Unknown')} ({context.get('ticker', 'N/A')})
- Current Price: ${context.get('current_price', 0):.2f}
- Fair Value: ${context.get('fair_value', 0):.2f}
- Upside: {context.get('upside_pct', 0):.1f}%

Recent conversation:
{recent_context}

Classify the intent as ONE of:
1. challenge_assumption - User questioning a DCF premise or assumption
2. request_calibration - Wants to adjust DCF parameters (growth, margins, WACC)
3. run_analysis - Wants to run a specific analysis (sensitivity, comparison)
4. compare_scenarios - Wants to see bull/bear/base scenarios compared
5. save_scenario - Wants to save current state as a named scenario
6. bull_bear_discussion - Wants to discuss bull vs bear case arguments
7. general_question - Needs information or clarification about the analysis

Return JSON with:
{{
    "type": "<intent_type>",
    "parameters": {{}},
    "confidence": 0.95
}}

For request_calibration, include parameters like:
- "revenue_growth_delta": number (change in %)
- "operating_margin_delta": number (change in %)
- "wacc_delta": number (change in %)
- "terminal_growth_delta": number (change in %)
- "rationale": string (reason for the adjustment)

Only return valid JSON, no other text."""

        try:
            if self._is_anthropic_provider(provider):
                return self._classify_intent_claude(prompt, provider)
            else:
                return self._classify_intent_openai(prompt, provider)
        except Exception as e:
            print(f"Intent classification error: {e}")
            return self._fallback_intent_classification(message)
    
    def _classify_intent_claude(self, prompt: str, provider: Optional[LLMProvider] = None) -> Intent:
        """Classify intent using Claude."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider)
        if client is None:
            raise RuntimeError(f"Anthropic client unavailable for provider={provider}")

        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_prompt(
            agent_name="classify_intent_claude",
            prompt=prompt,
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
            }
        )
        
        response = client.messages.create(
            model=selected_model,
            max_tokens=500,
            messages=[{"role": "user", "content": prompt}],
        )
        
        content = response.content[0].text.strip()
        
        # Dump response
        if prompt_timestamp:
            dumper.dump_response(
                agent_name="classify_intent_claude",
                response=content,
                timestamp=prompt_timestamp,
                metadata={
                    "provider": self._normalize_provider(provider),
                    "model": selected_model,
                }
            )
        
        return self._parse_intent_response(content)
    
    def _classify_intent_openai(self, prompt: str, provider: Optional[LLMProvider] = None) -> Intent:
        """Classify intent using OpenAI."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider)
        if client is None:
            raise RuntimeError(f"OpenAI-compatible client unavailable for provider={provider}")

        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_prompt(
            agent_name="classify_intent_openai",
            prompt=prompt,
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
                "temperature": 0.1,
            }
        )
        
        response = client.chat.completions.create(
            model=selected_model,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.1,
            max_tokens=500,
        )
        
        content = response.choices[0].message.content.strip()
        
        # Dump response
        if prompt_timestamp:
            dumper.dump_response(
                agent_name="classify_intent_openai",
                response=content,
                timestamp=prompt_timestamp,
                metadata={
                    "provider": self._normalize_provider(provider),
                    "model": selected_model,
                }
            )
        
        return self._parse_intent_response(content)
    
    def _parse_intent_response(self, content: str) -> Intent:
        """Parse the LLM response into an Intent."""
        # Clean up potential markdown code blocks
        if content.startswith('```'):
            content = content.split('```')[1]
            if content.startswith('json'):
                content = content[4:]
        
        data = json.loads(content)
        return Intent.from_dict(data)
    
    def _fallback_intent_classification(self, message: str) -> Intent:
        """Simple keyword-based intent classification fallback."""
        message_lower = message.lower()
        
        calibration_keywords = ['adjust', 'change', 'increase', 'decrease', 'what if', 'assume']
        if any(kw in message_lower for kw in calibration_keywords):
            return Intent(
                type=IntentType.REQUEST_CALIBRATION,
                parameters={'rationale': message},
                confidence=0.6
            )
        
        scenario_keywords = ['bull', 'bear', 'scenario', 'compare', 'vs']
        if any(kw in message_lower for kw in scenario_keywords):
            return Intent(
                type=IntentType.BULL_BEAR_DISCUSSION,
                parameters={},
                confidence=0.7
            )
        
        if 'save' in message_lower or 'bookmark' in message_lower:
            return Intent(
                type=IntentType.SAVE_SCENARIO,
                parameters={},
                confidence=0.8
            )
        
        challenge_keywords = ['why', 'how', 'explain', 'what drives', 'assumption']
        if any(kw in message_lower for kw in challenge_keywords):
            return Intent(
                type=IntentType.CHALLENGE_ASSUMPTION,
                parameters={},
                confidence=0.6
            )
        
        return Intent(
            type=IntentType.GENERAL_QUESTION,
            parameters={},
            confidence=0.5
        )
    
    def generate_reasoning_response(
        self,
        message: str,
        intent: Intent,
        context: Dict[str, Any],
        stream: bool = True,
        provider: Optional[LLMProvider] = None
    ) -> Generator[str, None, None]:
        """
        Generate an AI reasoning response.
        Yields response chunks for streaming.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            yield self._generate_fallback_response(message, intent, context)
            return
        
        system_prompt = self._build_system_prompt(context)
        user_prompt = self._build_user_prompt(message, intent, context)
        
        try:
            if self._is_anthropic_provider(provider):
                yield from self._generate_claude_response(system_prompt, user_prompt, stream, provider)
            else:
                yield from self._generate_openai_response(system_prompt, user_prompt, stream, provider)
        except Exception as e:
            print(f"LLM generation error: {e}")
            yield self._generate_fallback_response(message, intent, context)
    
    def _generate_claude_response(
        self, 
        system_prompt: str, 
        user_prompt: str, 
        stream: bool,
        provider: Optional[LLMProvider] = None,
    ) -> Generator[str, None, None]:
        """Generate response using Claude."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider)
        if client is None:
            raise RuntimeError(f"Anthropic client unavailable for provider={provider}")

        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_chat_turn(
            agent_name="generate_reasoning_claude",
            system_prompt=system_prompt,
            user_message=user_prompt,
            assistant_response="",  # Will be dumped separately
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
                "stream": stream,
            }
        )
        
        if stream:
            full_response = []
            with client.messages.stream(
                model=selected_model,
                max_tokens=1500,
                system=system_prompt,
                messages=[{"role": "user", "content": user_prompt}],
            ) as stream_response:
                for text in stream_response.text_stream:
                    full_response.append(text)
                    yield text
            
            # Dump response after streaming
            if prompt_timestamp:
                dumper.dump_response(
                    agent_name="generate_reasoning_claude",
                    response="".join(full_response),
                    timestamp=prompt_timestamp,
                    metadata={"provider": self._normalize_provider(provider), "model": selected_model}
                )
        else:
            response = client.messages.create(
                model=selected_model,
                max_tokens=1500,
                system=system_prompt,
                messages=[{"role": "user", "content": user_prompt}],
            )
            response_text = response.content[0].text
            
            # Dump response
            if prompt_timestamp:
                dumper.dump_response(
                    agent_name="generate_reasoning_claude",
                    response=response_text,
                    timestamp=prompt_timestamp,
                    metadata={"provider": self._normalize_provider(provider), "model": selected_model}
                )
            
            yield response_text
    
    def _generate_openai_response(
        self, 
        system_prompt: str, 
        user_prompt: str, 
        stream: bool,
        provider: Optional[LLMProvider] = None,
    ) -> Generator[str, None, None]:
        """Generate response using OpenAI."""
        client = self._provider_client(provider)
        selected_model = self._provider_model(provider)
        if client is None:
            raise RuntimeError(f"OpenAI-compatible client unavailable for provider={provider}")

        # Dump prompt before LLM call
        dumper = get_prompt_dumper()
        prompt_timestamp = dumper.dump_chat_turn(
            agent_name="generate_reasoning_openai",
            system_prompt=system_prompt,
            user_message=user_prompt,
            assistant_response="",  # Will be dumped separately
            metadata={
                "provider": self._normalize_provider(provider),
                "model": selected_model,
                "stream": stream,
                "temperature": 0.7,
            }
        )
        
        if stream:
            full_response = []
            response = client.chat.completions.create(
                model=selected_model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=0.7,
                max_tokens=1500,
                stream=True,
            )
            
            for chunk in response:
                if chunk.choices[0].delta.content:
                    full_response.append(chunk.choices[0].delta.content)
                    yield chunk.choices[0].delta.content
            
            # Dump response after streaming
            if prompt_timestamp:
                dumper.dump_response(
                    agent_name="generate_reasoning_openai",
                    response="".join(full_response),
                    timestamp=prompt_timestamp,
                    metadata={"provider": self._normalize_provider(provider), "model": selected_model}
                )
        else:
            response = client.chat.completions.create(
                model=selected_model,
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_prompt},
                ],
                temperature=0.7,
                max_tokens=1500,
            )
            response_text = response.choices[0].message.content
            
            # Dump response
            if prompt_timestamp:
                dumper.dump_response(
                    agent_name="generate_reasoning_openai",
                    response=response_text,
                    timestamp=prompt_timestamp,
                    metadata={"provider": self._normalize_provider(provider), "model": selected_model}
                )
            
            yield response_text
    
    def _build_system_prompt(self, context: Dict[str, Any]) -> str:
        """Build the system prompt for the LLM."""
        return f"""You are an expert investment analyst assistant helping users understand and refine a DCF valuation model.

You are analyzing {context.get('company_name', 'a company')} ({context.get('ticker', 'N/A')}).

Current Valuation Summary:
- Current Market Price: ${context.get('current_price', 0):.2f}
- DCF Fair Value: ${context.get('fair_value', 0):.2f}
- Implied Upside: {context.get('upside_pct', 0):.1f}%

Key Investment Thesis:
{context.get('growth_story', '')}
{context.get('profitability_story', '')}
{context.get('risk_story', '')}

Guidelines:
1. Be concise but thorough in your analysis
2. Reference specific numbers from the DCF model when relevant
3. When suggesting calibrations, be specific about the parameter changes
4. Use markdown formatting for clarity (headers, bullet points, bold for emphasis)
5. Always end with a thought-provoking question to continue the analysis
6. When discussing scenarios, reference the bull/bear/base cases
7. Be balanced - acknowledge both upside potential and risks"""
    
    def _build_user_prompt(
        self, 
        message: str, 
        intent: Intent, 
        context: Dict[str, Any]
    ) -> str:
        """Build the user prompt based on intent."""
        base_prompt = f"User question: {message}\n\n"
        
        if intent.type == IntentType.CHALLENGE_ASSUMPTION:
            return base_prompt + """The user is challenging an assumption in the DCF model.
Explain the assumption, its basis, and how changing it would impact the valuation.
Be specific about sensitivities."""
        
        elif intent.type == IntentType.REQUEST_CALIBRATION:
            params = intent.parameters
            return base_prompt + f"""The user wants to adjust the DCF model.
Suggested changes: {json.dumps(params)}

Explain:
1. The impact of these changes on fair value
2. Whether these assumptions are reasonable
3. What evidence would support or refute these changes

Suggest specific calibration values if the user hasn't provided exact numbers."""
        
        elif intent.type == IntentType.BULL_BEAR_DISCUSSION:
            debate = context.get('bull_bear_debate', [])
            scenarios = context.get('scenarios', {})
            return base_prompt + f"""The user wants to discuss bull vs bear scenarios.

Pre-computed scenarios:
- Bull Case: ${scenarios.get('bull', {}).get('fair_value', 0):.2f}
- Base Case: ${scenarios.get('base', {}).get('fair_value', 0):.2f}
- Bear Case: ${scenarios.get('bear', {}).get('fair_value', 0):.2f}

Previous debate points:
{json.dumps(debate, indent=2)}

Provide a balanced analysis of both perspectives."""
        
        elif intent.type == IntentType.COMPARE_SCENARIOS:
            scenarios = context.get('scenarios', {})
            return base_prompt + f"""Compare the different valuation scenarios:

{json.dumps(scenarios, indent=2)}

Explain what drives the differences and which scenario seems most likely."""
        
        elif intent.type == IntentType.SAVE_SCENARIO:
            return base_prompt + """The user wants to save their current scenario.
Summarize what makes this scenario unique and suggest a descriptive name for it."""
        
        else:
            return base_prompt + "Provide a helpful response to the user's question about the DCF valuation."
    
    def _generate_fallback_response(
        self, 
        message: str, 
        intent: Intent, 
        context: Dict[str, Any]
    ) -> str:
        """Generate a fallback response when LLM is unavailable."""
        company = context.get('company_name', 'the company')
        fair_value = context.get('fair_value', 0)
        current_price = context.get('current_price', 0)
        upside = context.get('upside_pct', 0)
        
        if intent.type == IntentType.BULL_BEAR_DISCUSSION:
            scenarios = context.get('scenarios', {})
            return f"""## Bull vs Bear Analysis for {company}

### Bull Case (${scenarios.get('bull', {}).get('fair_value', 0):.2f})
The optimistic scenario assumes stronger revenue growth and margin expansion, driven by successful execution of growth initiatives.

### Bear Case (${scenarios.get('bear', {}).get('fair_value', 0):.2f})
The pessimistic scenario accounts for competitive pressures and potential margin compression.

### Base Case (${scenarios.get('base', {}).get('fair_value', 0):.2f})
The base case represents our best estimate based on current trends.

**What factors do you think are most likely to push the outcome toward the bull or bear case?**"""
        
        elif intent.type == IntentType.REQUEST_CALIBRATION:
            return f"""## Calibration Analysis

Current fair value is **${fair_value:.2f}** with {upside:.1f}% upside.

To adjust the model, I can modify:
- **Revenue Growth**: Currently projecting strong growth converging to terminal rate
- **Operating Margins**: Assuming stable margins in the growth phase
- **Cost of Capital (WACC)**: Reflects the risk premium for {company}
- **Terminal Growth**: Long-term sustainable growth rate

**What specific assumptions would you like to adjust?**"""
        
        else:
            return f"""## Analysis Summary

{company} is currently trading at **${current_price:.2f}** against a DCF fair value of **${fair_value:.2f}**, implying **{upside:.1f}% upside**.

Key value drivers include:
- {context.get('growth_story', 'Revenue growth trajectory')}
- {context.get('profitability_story', 'Margin profile')}

**What aspect of this valuation would you like to explore further?**"""
    
    def extract_calibration_params(
        self, 
        message: str, 
        context: Dict[str, Any],
        provider: Optional[LLMProvider] = None
    ) -> Dict[str, Any]:
        """Extract specific calibration parameters from a user message."""
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            return {}
        
        prompt = f"""Extract DCF calibration parameters from this user message:
"{message}"

Current values:
- Revenue Growth: ~17% initial, 4.6% terminal
- Operating Margin: ~45% initial, 40% terminal
- WACC: 10.07% initial, 8.54% terminal
- Terminal Growth: 4.6%

Return JSON with any of these delta values (change from current):
{{
    "revenue_growth_delta": null or number (-5 to +5 typical),
    "operating_margin_delta": null or number (-10 to +10 typical),
    "wacc_delta": null or number (-2 to +2 typical),
    "terminal_growth_delta": null or number (-2 to +2 typical),
    "rationale": "brief explanation"
}}

        Only return valid JSON."""

        try:
            content = self._invoke_text_completion(
                prompt=prompt,
                provider=provider,
                max_tokens=300,
                temperature=0.1,
            )
            
            if content.startswith('```'):
                content = content.split('```')[1]
                if content.startswith('json'):
                    content = content[4:]
            
            return json.loads(content)
            
        except Exception as e:
            print(f"Parameter extraction error: {e}")
            return {}
    
    def generate_thesis_title(
        self,
        cells: List[Dict[str, Any]],
        ticker: str,
        company_name: str = "",
        provider: Optional[LLMProvider] = None
    ) -> str:
        """
        Generate an AI-powered title for a saved thesis based on cell content.
        
        Args:
            cells: List of cell dictionaries (first 3-5 cells analyzed)
            ticker: Stock ticker symbol
            company_name: Company name
            provider: LLM provider to use (defaults to current provider)
            
        Returns:
            Generated title (e.g., "META Bull Case: AI Growth Analysis")
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            # Fallback title
            date_str = datetime.now().strftime('%Y-%m-%d')
            return f"{ticker} Investment Thesis - {date_str}"
        
        # Analyze first 3-5 cells to extract themes
        cells_to_analyze = cells[:5]
        cell_summaries = []
        
        for cell in cells_to_analyze:
            user_input = cell.get('user_input') or cell.get('content', {}).get('user_input', '')
            ai_output = cell.get('ai_output') or cell.get('content', {}).get('ai_output', {})
            ai_message = ai_output.get('message', '') if isinstance(ai_output, dict) else str(ai_output)
            
            if user_input:
                cell_summaries.append(f"User: {user_input[:200]}")
            if ai_message:
                cell_summaries.append(f"AI: {ai_message[:200]}")
        
        cells_text = "\n".join(cell_summaries)
        
        prompt = f"""Analyze this investment analysis conversation and generate a concise, descriptive title.

Company: {company_name or ticker} ({ticker})
Conversation preview:
{cells_text}

Generate a title that captures:
1. The main theme (Bull Case, Bear Case, Calibration, Sensitivity Analysis, etc.)
2. The key focus area (AI Growth, Margin Expansion, Regulatory Risk, Market Share, etc.)

Format: "{ticker} [Theme]: [Focus]"
Examples:
- "META Bull Case: AI Growth Analysis"
- "AAPL Bear Case: China Risk Assessment"
- "TSLA Calibration: Margin Expansion Scenario"

        Keep it under 60 characters. Return ONLY the title, no quotes or explanation."""

        try:
            title = self._invoke_text_completion(
                prompt=prompt,
                provider=provider,
                max_tokens=100,
                temperature=0.7,
            )
            
            # Clean up title (remove quotes if present)
            title = title.strip('"\'')
            
            # Ensure it starts with ticker
            if not title.upper().startswith(ticker.upper()):
                title = f"{ticker} {title}"
            
            # Truncate to 60 characters
            if len(title) > 60:
                title = title[:57] + "..."
            
            return title
            
        except Exception as e:
            print(f"Title generation error: {e}")
            # Fallback title
            date_str = datetime.now().strftime('%Y-%m-%d')
            return f"{ticker} Investment Thesis - {date_str}"
    
    # ==========================================
    # AGENTIC WORKFLOW METHODS
    # ==========================================
    
    def rewrite_query(
        self,
        message: str,
        context: Dict[str, Any],
        provider: Optional[LLMProvider] = None
    ) -> str:
        """
        Rewrite user query for clarity and specificity.
        
        Makes vague queries more precise for better tool selection and response.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            return message  # Return original if LLM unavailable
        
        ticker = context.get('ticker', '')
        company_name = context.get('company_name', '')
        
        prompt = f"""Rewrite this user question to be more specific and actionable for investment analysis.

Original question: "{message}"
Company: {company_name or ticker} ({ticker})

Rules:
1. Keep the core intent but make it clearer
2. Add specificity where helpful (but don't invent facts)
3. If already clear, return it mostly unchanged
4. Keep it concise (under 100 words)
5. Frame for investment/valuation analysis context

Return ONLY the rewritten question, no explanation."""

        try:
            return self._invoke_text_completion(
                prompt=prompt,
                provider=provider,
                max_tokens=200,
                temperature=0.3,
            )
        except Exception as e:
            print(f"Query rewriting error: {e}")
            return message  # Return original on error
    
    def select_tools(
        self,
        message: str,
        context: Dict[str, Any],
        available_tools: List[str],
        provider: Optional[LLMProvider] = None
    ) -> List[Dict[str, Any]]:
        """
        Select which tools to use and with what parameters.
        
        Returns list of tool selections with params.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            return []
        
        tools_str = ", ".join(available_tools)
        valuation_summary = self._summarize_valuation_data(context.get('valuation_data', {}))
        
        prompt = f"""Analyze this user question and select which tools to use.

Question: "{message}"
Available tools: [{tools_str}]
Current valuation data summary: {valuation_summary}

Tool descriptions:
- dcf_recalculator: Recalculate DCF valuation with new assumptions. Use when user wants to change terminal growth, WACC, revenue growth, operating margin, or other valuation parameters. Returns before/after comparison.
- scenario_builder: Create, manage, and compare valuation scenarios (Bull/Bear/Base). Use when user wants to build scenarios, set probabilities, or calculate probability-weighted fair value.
- get_industry_comparables: Get industry averages to validate assumptions. Use when user asks if an assumption is reasonable, or wants to compare values against industry benchmarks.
- tavily_search: Search the web for news, analyst opinions, industry data. Use ONLY if valuation_data lacks the needed info.
- python_interpreter: Execute Python code for calculations. Use for custom formulas, sensitivity analysis, numerical comparisons.
- valuation_loader: Load fresh valuation data from database. Use if no valuation_data exists.
- llm_guard: Scan text for safety. Usually not needed for normal queries.

For dcf_recalculator, extract the parameter and value from the user's request:
- "Change terminal growth to 7%" → {{"tool": "dcf_recalculator", "params": {{"terminal_growth": 7.0}}}}
- "What if WACC was 10%?" → {{"tool": "dcf_recalculator", "params": {{"wacc": 10.0}}}}
- "Recalculate with 20% revenue growth" → {{"tool": "dcf_recalculator", "params": {{"revenue_growth": 20.0}}}}

For scenario_builder, use actions:
- "Create a bull case" → {{"tool": "scenario_builder", "params": {{"action": "create", "name": "Bull Case", "scenario_type": "bull"}}}}
- "Show me bull and bear scenarios" → {{"tool": "scenario_builder", "params": {{"action": "auto_generate"}}}}
- "Compare scenarios" → {{"tool": "scenario_builder", "params": {{"action": "compare"}}}}
- "What's the probability-weighted value?" → {{"tool": "scenario_builder", "params": {{"action": "compare"}}}}

For get_industry_comparables:
- "Is 7% terminal growth reasonable?" → {{"tool": "get_industry_comparables", "params": {{"metric": "terminal_growth", "user_value": 7.0}}}}
- "How does my WACC compare to industry?" → {{"tool": "get_industry_comparables", "params": {{"metric": "wacc"}}}}
- "Show me Auto industry metrics" → {{"tool": "get_industry_comparables", "params": {{"industry": "Auto & Truck"}}}}

Return a JSON array of tools to use, with parameters:
[
  {{"tool": "tool_name", "params": {{"key": "value"}}, "reason": "why needed"}}
]

If no tools needed (can answer from context), return: []
Return ONLY valid JSON, no markdown or explanation."""

        try:
            content = self._invoke_text_completion(
                prompt=prompt,
                provider=provider,
                max_tokens=500,
                temperature=0.2,
            )
            
            # Parse JSON
            if content.startswith('```'):
                content = content.split('```')[1]
                if content.startswith('json'):
                    content = content[4:]
            
            tools = json.loads(content)
            return tools if isinstance(tools, list) else []
            
        except Exception as e:
            print(f"Tool selection error: {e}")
            return []
    
    def _summarize_valuation_data(self, valuation_data: Dict[str, Any]) -> str:
        """Create a brief summary of available valuation data."""
        if not valuation_data:
            return "No valuation data loaded"
        
        keys = list(valuation_data.keys())[:10]
        return f"Available: {', '.join(keys)}" + (
            "..." if len(valuation_data) > 10 else ""
        )
    
    def generate_code(
        self,
        message: str,
        context: Dict[str, Any],
        provider: Optional[LLMProvider] = None
    ) -> Optional[str]:
        """
        Generate Python code for calculations when needed.
        
        Returns Python code string or None if not needed.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            return None
        
        # Check if calculation is likely needed
        calc_keywords = ['calculate', 'compute', 'what if', 'sensitivity', 'compare', 'growth', 'margin', '%']
        if not any(kw in message.lower() for kw in calc_keywords):
            return None
        
        valuation_data = context.get('valuation_data', {})
        dcf_state = context.get('dcf_state', {})
        
        prompt = f"""Generate Python code to answer this investment question.

Question: "{message}"

Available variables:
- valuation: Dict with company valuation data
- dcf: Dict with DCF model state

Sample valuation keys: {list(valuation_data.keys())[:5] if valuation_data else 'Not loaded'}
Sample dcf keys: {list(dcf_state.keys())[:5] if dcf_state else 'Not loaded'}

Rules:
1. Use only standard libraries (math, statistics) + numpy/pandas if needed
2. Store final answer in 'result' variable
3. Include print() for intermediate steps
4. Handle missing data gracefully
5. Keep code under 30 lines

If no calculation is needed, return exactly: NO_CODE_NEEDED

Return ONLY the Python code, no markdown or explanation."""

        try:
            code = self._invoke_text_completion(
                prompt=prompt,
                provider=provider,
                max_tokens=800,
                temperature=0.2,
            )
            
            # Clean up code
            if code.startswith('```'):
                lines = code.split('\n')
                code = '\n'.join(lines[1:-1] if lines[-1] == '```' else lines[1:])
            
            if 'NO_CODE_NEEDED' in code:
                return None
            
            return code
            
        except Exception as e:
            print(f"Code generation error: {e}")
            return None
    
    def synthesize_response(
        self,
        original_message: str,
        rewritten_query: str,
        tool_results: List[Dict[str, Any]],
        context: Dict[str, Any],
        provider: Optional[LLMProvider] = None
    ) -> Generator[str, None, None]:
        """
        Synthesize a final response using tool results and context.
        
        Streams the response for real-time display.
        """
        provider = self._normalize_provider(provider)
        
        if not self.is_available(provider):
            yield "I'm sorry, but the AI service is currently unavailable."
            return
        
        # Format tool results for context
        tool_context = self._format_tool_results(tool_results)
        
        system_prompt = """You are a helpful investment analyst assistant. Synthesize information from multiple sources to provide a comprehensive, well-reasoned response.

Guidelines:
1. Reference specific data and sources when available
2. Be clear about uncertainty or conflicting information
3. Provide actionable insights when possible
4. Use numbers and metrics to support claims
5. Be concise but thorough"""

        user_prompt = f"""Original question: "{original_message}"
Clarified question: "{rewritten_query}"

Tool results and data:
{tool_context}

Valuation context:
Ticker: {context.get('ticker', 'Unknown')}
Company: {context.get('company_name', 'Unknown')}

Provide a comprehensive response that addresses the user's question using all available information."""

        messages = [{"role": "user", "content": user_prompt}]
        
        yield from self.stream_chat(
            messages=messages,
            system_prompt=system_prompt,
            provider=provider,
            max_tokens=2048,
        )
    
    def _format_tool_results(self, tool_results: List[Dict[str, Any]]) -> str:
        """Format tool results for inclusion in synthesis prompt."""
        if not tool_results:
            return "No additional tool data available."
        
        parts = []
        for result in tool_results:
            tool_name = result.get('tool_name', 'unknown')
            status = result.get('status', 'unknown')
            data = result.get('data', {})
            error = result.get('error')
            
            if status == 'success':
                if tool_name == 'tavily_search':
                    answer = data.get('answer', '')
                    search_results = data.get('results', [])[:3]
                    sources = "\n".join([
                        f"  - {r.get('title', 'No title')}: {r.get('content', '')[:200]}..."
                        for r in search_results
                    ])
                    parts.append(f"**Web Search Results:**\nSummary: {answer}\nSources:\n{sources}")
                    
                elif tool_name == 'python_interpreter':
                    output = data.get('output', '')
                    code_result = data.get('result')
                    parts.append(f"**Code Execution:**\nOutput: {output}\nResult: {code_result}")
                    
                elif tool_name == 'valuation_loader':
                    metrics = data.get('metrics', {})
                    parts.append(f"**Valuation Data:**\n{json.dumps(metrics, indent=2)}")
                    
                else:
                    parts.append(f"**{tool_name}:** {json.dumps(data)}")
            
            elif status == 'error':
                parts.append(f"**{tool_name} (error):** {error}")
            
            elif status == 'skipped':
                reason = data.get('reason', 'Skipped')
                parts.append(f"**{tool_name} (skipped):** {reason}")
        
        return "\n\n".join(parts)


# Singleton instance
_llm_service: Optional[LLMService] = None


def get_llm_service() -> LLMService:
    """Get or create the LLM service singleton.
    
    Provider auto-detected from env (see shared/llm_models.py):
      ANTHROPIC_API_KEY → claude
      OPENAI_API_KEY    → openai
      GROQ_API_KEY      → groq
      GEMINI_API_KEY    → gemini
    Or override with DEFAULT_LLM_PROVIDER.
    """
    global _llm_service
    if _llm_service is None:
        _llm_service = LLMService()  # auto-detects provider from env
    return _llm_service
