"""
Data models and type definitions for the stockvaluation.io application.
"""
from typing import Dict, Any, Optional, Annotated
from pydantic import BaseModel, Field

def merge_dicts(left: Dict[str, Any], right: Dict[str, Any]) -> Dict[str, Any]:
    """Merge two dictionaries, with right taking precedence over left."""
    result = left.copy()
    result.update(right)
    return result

def merge_strings(left: str, right: str) -> str:
    """Merge two strings, with right taking precedence over left if both are non-empty."""
    if not left:
        return right
    if not right:
        return left
    return right

class GraphState(BaseModel):
    """State structure for the LangGraph StateGraph."""
    dcf: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)
    financials: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)
    segments: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)
    ticker: Annotated[str, merge_strings] = ""
    name: Annotated[str, merge_strings] = ""
    industry: Annotated[str, merge_strings] = ""
    news: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)
    growth_skill_context: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)
    merged_result: Annotated[Dict[str, Any], merge_dicts] = Field(default_factory=dict)

class ValuationRequest(BaseModel):
    """Domain model for inbound Valuate endpoint requests."""
    ticker: str
    name: Optional[str] = None
