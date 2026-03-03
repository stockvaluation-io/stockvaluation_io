"""
Prompt registry for dynamically loading prompt templates by version and agent name.
"""
import importlib
import logging
from typing import Callable, Dict, Any, Optional
from .version import PROMPT_VERSION

logger = logging.getLogger(__name__)

class PromptRegistry:
    """Registry for managing prompt templates with versioning."""
    
    def __init__(self):
        self._cache = {}
        self._version_mappings = PROMPT_VERSION.copy()
    
    def load_prompt(self, agent: str, version: str = None) -> Optional[Callable]:
        """
        Dynamically load prompt template by agent name and version.
        
        Args:
            agent: Agent name (e.g., 'valuation_assessment')
            version: Version string (e.g., 'v1'). If None, uses default from version config
            
        Returns:
            Callable function that takes inputs dict and returns prompt string
        """
        try:
            # Use default version if not specified
            if version is None:
                version = self._version_mappings.get(agent, "v1")
            
            # Check cache first
            cache_key = f"{agent}_{version}"
            if cache_key in self._cache:
                logger.debug(f"Cache hit for prompt: {cache_key}")
                return self._cache[cache_key]
            
            # Build module path
            module_path = f"prompts.agents.{agent}_{version}"
            
            logger.debug(f"Loading prompt module: {module_path}")
            
            # Import the module
            module = importlib.import_module(module_path)
            
            # Get the get_prompt function
            if not hasattr(module, 'get_prompt'):
                logger.error(f"Module {module_path} missing get_prompt function")
                return None
                
            prompt_func = getattr(module, 'get_prompt')
            
            # Cache the function
            self._cache[cache_key] = prompt_func
            
            logger.debug(f"Successfully loaded prompt for {agent} version {version}")
            return prompt_func
            
        except ImportError as e:
            logger.error(f"Failed to import prompt module {module_path}: {str(e)}")
            return None
        except Exception as e:
            logger.error(f"Error loading prompt for {agent} version {version}: {str(e)}")
            return None
    
    def get_prompt(self, agent: str, inputs: Dict[str, Any], version: str = None) -> Optional[str]:
        """
        Get prompt string for an agent with given inputs.
        
        Args:
            agent: Agent name
            inputs: Input dictionary for the prompt
            version: Version string (optional)
            
        Returns:
            Formatted prompt string or None if error
        """
        try:
            prompt_func = self.load_prompt(agent, version)
            if not prompt_func:
                return None
                
            return prompt_func(inputs)
            
        except Exception as e:
            logger.error(f"Error getting prompt for {agent}: {str(e)}")
            return None
    
    def list_available_agents(self) -> list:
        """
        List all available agents from version mappings.
        
        Returns:
            List of agent names
        """
        return list(self._version_mappings.keys())
    
    def get_agent_version(self, agent: str) -> str:
        """
        Get the default version for an agent.
        
        Args:
            agent: Agent name
            
        Returns:
            Version string
        """
        return self._version_mappings.get(agent, "v1")
    
    def clear_cache(self):
        """Clear the prompt cache."""
        self._cache.clear()
        logger.debug("Prompt cache cleared")
    
    def validate_all_prompts(self) -> Dict[str, bool]:
        """
        Validate that all prompt modules can be loaded.
        
        Returns:
            Dictionary mapping agent names to validation status
        """
        results = {}
        
        for agent in self.list_available_agents():
            try:
                prompt_func = self.load_prompt(agent)
                results[agent] = prompt_func is not None
            except Exception as e:
                logger.error(f"Validation failed for {agent}: {str(e)}")
                results[agent] = False
                
        return results

# Global registry instance
prompt_registry = PromptRegistry()

# Convenience functions
def load_prompt(agent: str, version: str = None) -> Optional[Callable]:
    """Load prompt function for an agent."""
    return prompt_registry.load_prompt(agent, version)

def get_prompt(agent: str, inputs: Dict[str, Any], version: str = None) -> Optional[str]:
    """Get formatted prompt string for an agent."""
    return prompt_registry.get_prompt(agent, inputs, version)
