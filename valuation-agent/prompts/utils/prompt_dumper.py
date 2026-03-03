"""
Prompt dumper utility for debugging and cost optimization.
When enabled, saves agent prompts and LLM responses to disk.
"""
import os
import json
from datetime import datetime
from pathlib import Path
from typing import Dict, Any
import logging

logger = logging.getLogger(__name__)

class PromptDumper:
    """Utility to dump agent prompts for debugging and analysis."""
    
    def __init__(self):
        gemini_configured = bool(os.getenv("GEMINI_API_KEY", "").strip())
        auto_enable = gemini_configured and os.getenv("AUTO_ENABLE_GEMINI_DUMPS", "false").lower() == "true"
        self.enabled = self._read_bool("DUMP_PROMPTS", auto_enable)
        self.dump_responses_enabled = self._read_bool("DUMP_LLM_RESPONSES", self.enabled)
        self.dump_processing_enabled = self._read_bool("DUMP_PROCESSING_STEPS", self.enabled)
        # Resolve to absolute path to avoid CWD ambiguity (esp. in Docker)
        self.dump_dir = Path(os.getenv("PROMPT_DUMP_DIR", ".etl/prompt_dump")).resolve()
        
        if self.enabled or self.dump_responses_enabled or self.dump_processing_enabled:
            # Ensure parent directories are created
            self.dump_dir.mkdir(parents=True, exist_ok=True)
            logger.debug(
                "Prompt dump config enabled. prompts=%s responses=%s processing=%s dir=%s",
                self.enabled,
                self.dump_responses_enabled,
                self.dump_processing_enabled,
                self.dump_dir,
            )

    @staticmethod
    def _read_bool(env_name: str, default: bool) -> bool:
        raw = os.getenv(env_name)
        if raw is None or raw.strip() == "":
            return default
        return raw.strip().lower() in {"1", "true", "yes", "on"}
    
    def dump_prompt(
        self, 
        agent_name: str, 
        prompt: str, 
        inputs: Dict[str, Any] = None,
        metadata: Dict[str, Any] = None
    ) -> str:
        """
        Dump a prompt to disk with metadata.
        
        Args:
            agent_name: Name of the agent (e.g., "analyst", "story", "narrative")
            prompt: The full prompt string
            inputs: Optional input data used to generate the prompt
            metadata: Optional metadata (model, temperature, etc.)
            
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
                json.dump(dump_data, f, indent=2, ensure_ascii=False)
            
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
    
    def dump_processing_step(
        self,
        step_name: str,
        inputs: Dict[str, Any],
        outputs: Dict[str, Any],
        metadata: Dict[str, Any] = None
    ):
        """
        Dump a non-LLM processing step (e.g., RAG query, synthesis, transformation).
        
        Args:
            step_name: Name of the processing step (e.g., "query_rag", "valuation_synthesis")
            inputs: Input data to the processing step
            outputs: Output data from the processing step
            metadata: Optional metadata (execution time, success status, etc.)
        """
        if not self.dump_processing_enabled:
            return
        
        try:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
            
            # Create step-specific subdirectory
            step_dir = self.dump_dir / step_name
            step_dir.mkdir(parents=True, exist_ok=True)
            
            # Serialize inputs/outputs
            inputs_str = json.dumps(inputs, indent=2, ensure_ascii=False, default=str)
            outputs_str = json.dumps(outputs, indent=2, ensure_ascii=False, default=str)
            
            # Create dump data
            dump_data = {
                "step": step_name,
                "timestamp": datetime.now().isoformat(),
                "type": "processing_step",
                "inputs": inputs,
                "outputs": outputs,
                "inputs_size_chars": len(inputs_str),
                "outputs_size_chars": len(outputs_str),
                "metadata": metadata or {}
            }
            
            # Save JSON file
            json_file = step_dir / f"{step_name}_{timestamp}.json"
            with open(json_file, 'w', encoding='utf-8') as f:
                json.dump(dump_data, f, indent=2, ensure_ascii=False, default=str)
            
            # Save text file (easier to read)
            txt_file = step_dir / f"{step_name}_{timestamp}.txt"
            with open(txt_file, 'w', encoding='utf-8') as f:
                f.write(f"Step: {step_name}\n")
                f.write(f"Type: PROCESSING STEP (non-LLM)\n")
                f.write(f"Timestamp: {dump_data['timestamp']}\n")
                f.write(f"Inputs Size: {dump_data['inputs_size_chars']} chars\n")
                f.write(f"Outputs Size: {dump_data['outputs_size_chars']} chars\n")
                if metadata:
                    f.write(f"Metadata: {json.dumps(metadata, indent=2)}\n")
                f.write("\n" + "="*80 + "\n")
                f.write("INPUTS:\n")
                f.write("="*80 + "\n\n")
                f.write(inputs_str)
                f.write("\n\n" + "="*80 + "\n")
                f.write("OUTPUTS:\n")
                f.write("="*80 + "\n\n")
                f.write(outputs_str)
            
            logger.debug(f"Dumped processing step {step_name} to {json_file}")
            
        except Exception as e:
            logger.error(f"Failed to dump processing step {step_name}: {e}")

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
        if not self.dump_responses_enabled:
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
                response_str = json.dumps(response, indent=2, ensure_ascii=False)
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
                json.dump(dump_data, f, indent=2, ensure_ascii=False)
            
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
    
    def _summarize_inputs(self, inputs: Dict[str, Any]) -> Dict[str, Any]:
        """Create a summary of inputs (avoid dumping huge JSON blobs)."""
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
            char_counts = [p['prompt_length_chars'] for p in prompts_data]
            token_estimates = [p['prompt_length_tokens_estimate'] for p in prompts_data]
            
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
        
        # Cost estimates (using rough GPT-4 pricing as reference)
        report_lines.append(f"\n## Cost Estimates\n")
        report_lines.append(f"**Total estimated tokens across all calls:** {total_tokens:,.0f}\n")
        report_lines.append(f"**Estimated cost at $0.03/1K tokens (GPT-4 input):** ${(total_tokens / 1000) * 0.03:.2f}\n")
        report_lines.append(f"**Estimated cost at $0.0015/1K tokens (GPT-3.5 input):** ${(total_tokens / 1000) * 0.0015:.2f}\n")
        report_lines.append(f"**Estimated cost at Groq rates (~$0.0001/1K tokens):** ${(total_tokens / 1000) * 0.0001:.2f}\n")
        
        report_lines.append("\n## Optimization Recommendations\n")
        report_lines.append("1. **Focus on top 3 agents** - They account for the majority of token usage\n")
        report_lines.append("2. **Review system prompts** - Can instructions be more concise?\n")
        report_lines.append("3. **Reduce context** - Are you passing unnecessary data (e.g., full financials when summary would suffice)?\n")
        report_lines.append("4. **Cache static content** - System prompts, sector contexts, examples can be cached\n")
        report_lines.append("5. **Use cheaper models for simple tasks** - Consider GPT-3.5-turbo or Groq for non-critical agents\n")
        
        return "\n".join(report_lines)
    
    def save_cost_report(self, filename: str = "COST_ANALYSIS.md"):
        """Save cost analysis report to disk."""
        if not self.enabled:
            logger.warning("Prompt dumping not enabled, cannot generate cost report")
            return
        
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
