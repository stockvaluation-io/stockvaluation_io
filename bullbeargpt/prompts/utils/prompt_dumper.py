"""
Prompt dumper utility for debugging and cost optimization.
When DUMP_PROMPTS environment variable is set, saves all prompts and responses to disk.
"""
import os
import json
from datetime import datetime
from pathlib import Path
from typing import Dict, Any, List, Optional
import logging

logger = logging.getLogger(__name__)


class PromptDumper:
    """Utility to dump prompts and responses for debugging and analysis."""
    
    def __init__(self):
        self.enabled = os.getenv("DUMP_PROMPTS", "false").lower() == "true"
        in_production = os.getenv("FLASK_ENV", "production").strip().lower() == "production"
        allow_in_production = os.getenv("ALLOW_PROMPT_DUMPS_IN_PRODUCTION", "false").lower() in {"1", "true", "yes", "on"}
        if in_production and not allow_in_production:
            self.enabled = False
        # Resolve to absolute path to avoid CWD ambiguity (esp. in Docker)
        self.dump_dir = Path(os.getenv("PROMPT_DUMP_DIR", "prompt_dump")).resolve()
        
        if self.enabled:
            # Ensure parent directories are created
            self.dump_dir.mkdir(parents=True, exist_ok=True)
            logger.info(f"Prompt dumping enabled. Directory: {self.dump_dir}")
    
    def dump_prompt(
        self, 
        agent_name: str, 
        prompt: str, 
        inputs: Dict[str, Any] = None,
        metadata: Dict[str, Any] = None
    ) -> Optional[str]:
        """
        Dump a prompt to disk with metadata.
        
        Args:
            agent_name: Name of the agent/method (e.g., "stream_chat", "classify_intent")
            prompt: The full prompt string (system + user combined)
            inputs: Optional input data used to generate the prompt
            metadata: Optional metadata (model, temperature, provider, etc.)
            
        Returns:
            timestamp: Timestamp string for pairing with response dump
        """
        if not self.enabled:
            return None
        
        try:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # Create agent-specific subdirectory
            agent_dir = self.dump_dir / agent_name
            agent_dir.mkdir(parents=True, exist_ok=True)
            
            # Create dump data
            dump_data = {
                "agent": agent_name,
                "timestamp": datetime.now().isoformat(),
                "type": "prompt",
                "prompt": prompt,
                "prompt_length_chars": len(prompt),
                "prompt_length_tokens_estimate": len(prompt) // 4,  # Rough estimate
                "inputs_summary": self._summarize_inputs(inputs) if inputs else None,
                "metadata": metadata or {}
            }
            
            # Save JSON file
            json_file = agent_dir / f"{agent_name}_{timestamp}_prompt.json"
            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(dump_data, f, indent=2, ensure_ascii=False, default=str)
            
            # Save text file (easier to read)
            txt_file = agent_dir / f"{agent_name}_{timestamp}_prompt.txt"
            with open(txt_file, 'w', encoding='utf-8') as f:
                f.write(f"Agent: {agent_name}\n")
                f.write(f"Type: PROMPT\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write(f"Prompt Length: {dump_data['prompt_length_chars']} chars (~{dump_data['prompt_length_tokens_estimate']} tokens)\n")
                if metadata:
                    f.write(f"Metadata: {json.dumps(metadata, indent=2)}\n")
                f.write("\n" + "="*80 + "\n")
                f.write("PROMPT:\n")
                f.write("="*80 + "\n\n")
                f.write(prompt)
            
            logger.debug(f"Dumped prompt for {agent_name} to {json_file}")
            
            return timestamp  # Return for pairing with response
            
        except Exception as e:
            logger.error(f"Failed to dump prompt for {agent_name}: {e}")
            return None
    
    def dump_response(
        self,
        agent_name: str,
        response: Any,
        timestamp: str = None,
        metadata: Dict[str, Any] = None
    ):
        """
        Dump LLM response paired with a prompt.
        
        Args:
            agent_name: Name of the agent
            response: The LLM response (string or dict)
            timestamp: Optional timestamp from dump_prompt() for pairing
            metadata: Optional metadata (model, latency, tokens used, etc.)
        """
        if not self.enabled:
            return
        
        try:
            # Use provided timestamp or generate new one
            if not timestamp:
                timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # Create agent-specific subdirectory
            agent_dir = self.dump_dir / agent_name
            agent_dir.mkdir(parents=True, exist_ok=True)
            
            # Convert response to string if it's a dict
            if isinstance(response, dict):
                response_str = json.dumps(response, indent=2, ensure_ascii=False, default=str)
                response_data = response
            else:
                response_str = str(response)
                try:
                    response_data = json.loads(response_str)
                except:
                    response_data = {"raw_response": response_str}
            
            # Create dump data
            dump_data = {
                "agent": agent_name,
                "timestamp": datetime.now().isoformat(),
                "type": "response",
                "paired_with_prompt": f"{agent_name}_{timestamp}_prompt.json",
                "response": response_data,
                "response_length_chars": len(response_str),
                "response_length_tokens_estimate": len(response_str) // 4,
                "metadata": metadata or {}
            }
            
            # Save JSON file
            json_file = agent_dir / f"{agent_name}_{timestamp}_response.json"
            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(dump_data, f, indent=2, ensure_ascii=False, default=str)
            
            # Save text file (easier to read)
            txt_file = agent_dir / f"{agent_name}_{timestamp}_response.txt"
            with open(txt_file, 'w', encoding='utf-8') as f:
                f.write(f"Agent: {agent_name}\n")
                f.write(f"Type: RESPONSE\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write(f"Paired With: {dump_data['paired_with_prompt']}\n")
                f.write(f"Response Length: {dump_data['response_length_chars']} chars (~{dump_data['response_length_tokens_estimate']} tokens)\n")
                if metadata:
                    f.write(f"Metadata: {json.dumps(metadata, indent=2)}\n")
                f.write("\n" + "="*80 + "\n")
                f.write("RESPONSE:\n")
                f.write("="*80 + "\n\n")
                f.write(response_str)
            
            logger.debug(f"Dumped response for {agent_name} to {json_file}")
            
            # Create combined file for easy evaluation
            combined_file = agent_dir / f"{agent_name}_{timestamp}_combined.txt"
            with open(combined_file, 'w', encoding='utf-8') as f:
                f.write("="*80 + "\n")
                f.write(f"AGENT: {agent_name}\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write("="*80 + "\n\n")
                
                # Read prompt file if exists
                prompt_file = agent_dir / f"{agent_name}_{timestamp}_prompt.txt"
                if prompt_file.exists():
                    with open(prompt_file, 'r', encoding='utf-8') as pf:
                        f.write(pf.read())
                        f.write("\n\n")
                
                f.write("="*80 + "\n")
                f.write("RESPONSE:\n")
                f.write("="*80 + "\n\n")
                f.write(response_str)
            
        except Exception as e:
            logger.error(f"Failed to dump response for {agent_name}: {e}")
    
    def dump_chat_turn(
        self,
        agent_name: str,
        system_prompt: str,
        user_message: str,
        assistant_response: str,
        context: Dict[str, Any] = None,
        metadata: Dict[str, Any] = None
    ) -> Optional[str]:
        """
        Dump a complete chat turn (system, user, assistant) in one call.
        
        This is a convenience method that combines dump_prompt and dump_response
        with structured formatting for chat-based interactions.
        
        Args:
            agent_name: Name of the agent/method
            system_prompt: The system prompt
            user_message: The user's message
            assistant_response: The LLM's response
            context: Optional context data (valuation data, DCF state, etc.)
            metadata: Optional metadata (model, provider, etc.)
            
        Returns:
            timestamp: Timestamp string for the dump
        """
        if not self.enabled:
            return None
        
        try:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # Create agent-specific subdirectory
            agent_dir = self.dump_dir / agent_name
            agent_dir.mkdir(parents=True, exist_ok=True)
            
            # Build combined prompt for character counting
            full_prompt = f"SYSTEM:\n{system_prompt}\n\nUSER:\n{user_message}"
            if context:
                context_str = json.dumps(context, indent=2, ensure_ascii=False, default=str)
                full_prompt += f"\n\nCONTEXT:\n{context_str}"
            
            # Create dump data
            dump_data = {
                "agent": agent_name,
                "timestamp": datetime.now().isoformat(),
                "type": "chat_turn",
                "system_prompt": system_prompt,
                "user_message": user_message,
                "context_summary": self._summarize_inputs(context) if context else None,
                "assistant_response": assistant_response,
                "prompt_length_chars": len(full_prompt),
                "prompt_length_tokens_estimate": len(full_prompt) // 4,
                "response_length_chars": len(assistant_response),
                "response_length_tokens_estimate": len(assistant_response) // 4,
                "metadata": metadata or {}
            }
            
            # Save JSON file
            json_file = agent_dir / f"{agent_name}_{timestamp}_chat_turn.json"
            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(dump_data, f, indent=2, ensure_ascii=False, default=str)
            
            # Save text file (easier to read)
            txt_file = agent_dir / f"{agent_name}_{timestamp}_chat_turn.txt"
            with open(txt_file, 'w', encoding='utf-8') as f:
                f.write(f"Agent: {agent_name}\n")
                f.write(f"Type: CHAT TURN\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write(f"Prompt Length: {dump_data['prompt_length_chars']} chars (~{dump_data['prompt_length_tokens_estimate']} tokens)\n")
                f.write(f"Response Length: {dump_data['response_length_chars']} chars (~{dump_data['response_length_tokens_estimate']} tokens)\n")
                if metadata:
                    f.write(f"Metadata: {json.dumps(metadata, indent=2)}\n")
                
                f.write("\n" + "="*80 + "\n")
                f.write("SYSTEM PROMPT:\n")
                f.write("="*80 + "\n\n")
                f.write(system_prompt)
                
                if context:
                    f.write("\n\n" + "="*80 + "\n")
                    f.write("CONTEXT:\n")
                    f.write("="*80 + "\n\n")
                    f.write(json.dumps(context, indent=2, ensure_ascii=False, default=str))
                
                f.write("\n\n" + "="*80 + "\n")
                f.write("USER MESSAGE:\n")
                f.write("="*80 + "\n\n")
                f.write(user_message)
                
                f.write("\n\n" + "="*80 + "\n")
                f.write("ASSISTANT RESPONSE:\n")
                f.write("="*80 + "\n\n")
                f.write(assistant_response)
            
            logger.debug(f"Dumped chat turn for {agent_name} to {json_file}")
            
            return timestamp
            
        except Exception as e:
            logger.error(f"Failed to dump chat turn for {agent_name}: {e}")
            return None
    
    def dump_messages(
        self,
        agent_name: str,
        messages: List[Dict[str, Any]],
        system_prompt: str = "",
        response: str = "",
        metadata: Dict[str, Any] = None
    ) -> Optional[str]:
        """
        Dump a conversation with multiple messages.
        
        Args:
            agent_name: Name of the agent/method
            messages: List of message dicts with 'role' and 'content'
            system_prompt: The system prompt
            response: The final LLM response
            metadata: Optional metadata
            
        Returns:
            timestamp: Timestamp string for the dump
        """
        if not self.enabled:
            return None
        
        try:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # Create agent-specific subdirectory
            agent_dir = self.dump_dir / agent_name
            agent_dir.mkdir(parents=True, exist_ok=True)
            
            # Calculate total prompt length
            total_prompt = system_prompt + "\n".join([m.get('content', '') for m in messages])
            
            # Create dump data
            dump_data = {
                "agent": agent_name,
                "timestamp": datetime.now().isoformat(),
                "type": "conversation",
                "system_prompt": system_prompt,
                "messages": messages,
                "response": response,
                "message_count": len(messages),
                "prompt_length_chars": len(total_prompt),
                "prompt_length_tokens_estimate": len(total_prompt) // 4,
                "response_length_chars": len(response),
                "response_length_tokens_estimate": len(response) // 4,
                "metadata": metadata or {}
            }
            
            # Save JSON file
            json_file = agent_dir / f"{agent_name}_{timestamp}_conversation.json"
            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(dump_data, f, indent=2, ensure_ascii=False, default=str)
            
            # Save text file (easier to read)
            txt_file = agent_dir / f"{agent_name}_{timestamp}_conversation.txt"
            with open(txt_file, 'w', encoding='utf-8') as f:
                f.write(f"Agent: {agent_name}\n")
                f.write(f"Type: CONVERSATION\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write(f"Message Count: {len(messages)}\n")
                f.write(f"Prompt Length: {dump_data['prompt_length_chars']} chars (~{dump_data['prompt_length_tokens_estimate']} tokens)\n")
                f.write(f"Response Length: {dump_data['response_length_chars']} chars (~{dump_data['response_length_tokens_estimate']} tokens)\n")
                if metadata:
                    f.write(f"Metadata: {json.dumps(metadata, indent=2)}\n")
                
                f.write("\n" + "="*80 + "\n")
                f.write("SYSTEM PROMPT:\n")
                f.write("="*80 + "\n\n")
                f.write(system_prompt)
                
                f.write("\n\n" + "="*80 + "\n")
                f.write("MESSAGES:\n")
                f.write("="*80 + "\n\n")
                for i, msg in enumerate(messages):
                    role = msg.get('role', 'unknown').upper()
                    content = msg.get('content', '')
                    f.write(f"--- {role} [{i+1}] ---\n")
                    f.write(content)
                    f.write("\n\n")
                
                f.write("="*80 + "\n")
                f.write("ASSISTANT RESPONSE:\n")
                f.write("="*80 + "\n\n")
                f.write(response)
            
            logger.debug(f"Dumped conversation for {agent_name} to {json_file}")
            
            return timestamp
            
        except Exception as e:
            logger.error(f"Failed to dump conversation for {agent_name}: {e}")
            return None
    
    def _summarize_inputs(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """Create a summary of inputs (avoid dumping huge JSON blobs)."""
        if not inputs:
            return {}
        
        summary = {}
        
        for key, value in inputs.items():
            if isinstance(value, str):
                summary[key] = f"{len(value)} chars" if len(value) > 200 else value
            elif isinstance(value, dict):
                summary[key] = f"dict with {len(value)} keys"
            elif isinstance(value, list):
                summary[key] = f"list with {len(value)} items"
            else:
                summary[key] = str(type(value).__name__)
        
        return summary
    
    def generate_cost_report(self) -> str:
        """
        Generate a cost analysis report from dumped prompts.
        
        Returns:
            Markdown formatted report
        """
        if not self.enabled or not self.dump_dir.exists():
            return "Prompt dumping not enabled or no dumps found."
        
        report_lines = [
            "# Prompt Cost Analysis Report",
            f"\nGenerated: {datetime.now().isoformat()}",
            f"\nDump Directory: {self.dump_dir}",
            "\n## Agent Prompt Statistics\n"
        ]
        
        # Analyze each agent directory
        agent_stats = {}
        
        for agent_dir in self.dump_dir.iterdir():
            if not agent_dir.is_dir():
                continue
            
            agent_name = agent_dir.name
            json_files = list(agent_dir.glob("*.json"))
            
            if not json_files:
                continue
            
            # Load and analyze prompts
            prompts_data = []
            for json_file in json_files:
                try:
                    with open(json_file, 'r', encoding='utf-8') as f:
                        data = json.load(f)
                        prompts_data.append(data)
                except Exception as e:
                    logger.error(f"Failed to read {json_file}: {e}")
            
            if not prompts_data:
                continue
            
            # Calculate statistics
            char_counts = [p.get('prompt_length_chars', 0) for p in prompts_data]
            token_estimates = [p.get('prompt_length_tokens_estimate', 0) for p in prompts_data]
            
            if not char_counts:
                continue
            
            agent_stats[agent_name] = {
                'count': len(prompts_data),
                'avg_chars': sum(char_counts) / len(char_counts),
                'max_chars': max(char_counts),
                'min_chars': min(char_counts),
                'avg_tokens': sum(token_estimates) / len(token_estimates),
                'max_tokens': max(token_estimates),
                'min_tokens': min(token_estimates),
            }
        
        # Sort by average tokens (highest first)
        sorted_agents = sorted(
            agent_stats.items(), 
            key=lambda x: x[1]['avg_tokens'], 
            reverse=True
        )
        
        # Format table
        report_lines.append("| Agent | Calls | Avg Tokens | Max Tokens | Min Tokens | Avg Chars |")
        report_lines.append("|-------|-------|------------|------------|------------|-----------|")
        
        total_tokens = 0
        for agent_name, stats in sorted_agents:
            report_lines.append(
                f"| {agent_name:20s} | "
                f"{stats['count']:5d} | "
                f"{stats['avg_tokens']:10.0f} | "
                f"{stats['max_tokens']:10.0f} | "
                f"{stats['min_tokens']:10.0f} | "
                f"{stats['avg_chars']:9.0f} |"
            )
            total_tokens += stats['avg_tokens'] * stats['count']
        
        # Cost estimates
        report_lines.append(f"\n## Cost Estimates\n")
        report_lines.append(f"**Total estimated tokens across all calls:** {total_tokens:,.0f}\n")
        report_lines.append(f"**Estimated cost at Claude Sonnet 4 rates (~$3/1M input):** ${(total_tokens / 1000000) * 3:.4f}\n")
        report_lines.append(f"**Estimated cost at GPT-4o-mini rates (~$0.15/1M input):** ${(total_tokens / 1000000) * 0.15:.4f}\n")
        
        return "\n".join(report_lines)
    
    def save_cost_report(self, filename: str = "COST_ANALYSIS.md"):
        """Save cost analysis report to disk."""
        if not self.enabled:
            logger.warning("Prompt dumping not enabled, cannot generate cost report")
            return None
        
        report = self.generate_cost_report()
        report_path = self.dump_dir / filename
        
        with open(report_path, 'w', encoding='utf-8') as f:
            f.write(report)
        
        return report_path


# Global singleton instance
_prompt_dumper = None


def get_prompt_dumper() -> PromptDumper:
    """Get the global PromptDumper instance."""
    global _prompt_dumper
    if _prompt_dumper is None:
        _prompt_dumper = PromptDumper()
    return _prompt_dumper
