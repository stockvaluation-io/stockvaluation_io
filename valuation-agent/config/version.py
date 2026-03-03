"""
Version constants for prompt templates and agent configurations.
"""

# Prompt version mappings for each agent
PROMPT_VERSION = {
    "analyst": "v1",
    "analyzer": "v1",
    "narrative": "v1",
    "segments": "v1",
    "segments_judge": "v1",
    "news_judge": "v1",
    # Query generator agents for dynamic Tavily queries
    "earnings_query_generator": "v1",
    "news_query_generator": "v1",
    "macro_query_generator": "v1",
    "segments_query_generator": "v1"
}
