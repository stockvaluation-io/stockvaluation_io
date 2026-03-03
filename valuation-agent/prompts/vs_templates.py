"""
Verbalized Sampling (VS) instruction templates.

Based on "Verbalized Sampling: How to Mitigate Mode Collapse and Unlock LLM Diversity"
by Zhang et al. (2024)

VS prompts the model to verbalize probability distributions over candidate responses,
increasing diversity by 1.6-2.1x for creative tasks without sacrificing factual accuracy.
"""

def get_creative_vs_block(num_candidates: int = 4, task_specific_directions: str = "") -> str:
    """
    Generate VS instructions for creative tasks (story, analyst, debate, scenario).
    
    Uses full probability distribution format for maximum diversity gains.
    
    Args:
        num_candidates: Number of candidate responses to generate (default 4)
        task_specific_directions: Custom directions for each candidate
        
    Returns:
        VS instruction block as string
    """
    return f"""
### VERBALIZED SAMPLING MODE ACTIVE ###

Before producing your final response, you must internally generate {num_candidates} diverse candidate responses.

**CRITICAL INSTRUCTIONS:**
1. For each candidate, create a meaningfully different approach (vary style, emphasis, structure, tone)
2. Assign each candidate a probability (0.0 to 1.0) representing your confidence in its quality
3. Probabilities MUST sum to 1.0 across all candidates
4. After evaluation, select the HIGHEST-PROBABILITY candidate
5. Return ONLY the selected candidate's response in the standard JSON format (no probability metadata)

**Candidate Generation Strategy:**
{task_specific_directions}

**Internal Reasoning Process (DO NOT OUTPUT THIS):**
Think through each candidate like this:
- Candidate 1 (p=0.40): [brief description of approach]
- Candidate 2 (p=0.30): [brief description of alternative approach]
- Candidate 3 (p=0.20): [brief description of another alternative]
- Candidate 4 (p=0.10): [brief description of final alternative]
→ Selected: Candidate 1 (highest probability)

**IMPORTANT:** The diversity in candidate generation helps you avoid mode collapse and produce more creative, less stereotypical responses. However, maintain factual accuracy and safety in all candidates.

After internal candidate evaluation, return ONLY the selected candidate's JSON response.
"""


def get_analytical_vs_block(num_candidates: int = 3, task_focus: str = "") -> str:
    """
    Generate simplified VS instructions for analytical tasks (narrative, option_valuation, macro_impact).
    
    Uses simplified confidence scoring format for focused diversity improvements.
    
    Args:
        num_candidates: Number of candidate interpretations to generate (default 3)
        task_focus: Specific focus area for the task
        
    Returns:
        Simplified VS instruction block as string
    """
    return f"""
### VERBALIZED SAMPLING MODE ACTIVE (Simplified) ###

Generate {num_candidates} alternative interpretations of the data, each with a confidence score.

**Process:**
1. Create {num_candidates} different interpretations (vary emphasis, categorization, or framing)
2. Assign confidence score to each (0.0-1.0, must sum to 1.0)
3. Select interpretation with highest confidence
4. Return in standard JSON format

**Focus:** {task_focus}

**Internal Evaluation (DO NOT OUTPUT):**
- Interpretation 1 (confidence=0.50): [key differentiator]
- Interpretation 2 (confidence=0.35): [key differentiator]
- Interpretation 3 (confidence=0.15): [key differentiator]
→ Selected: Interpretation 1

This process helps avoid formulaic responses while maintaining analytical rigor.
Return ONLY the selected interpretation's JSON response.
"""


# Task-specific candidate directions

STORY_CANDIDATE_DIRECTIONS = """
- Candidate 1: Lead with market misconception, emphasize contrarian insight, bold thesis statement
- Candidate 2: Lead with numerical evidence, build to contrarian conclusion, analytical tone
- Candidate 3: Lead with rhetorical question, use extended metaphor, provocative tone
- Candidate 4: Lead with historical parallel, gradual persuasion, measured skepticism
- Candidate 5: Lead with counter-consensus data point, rapid-fire evidence, urgent tone
"""

ANALYST_CANDIDATE_DIRECTIONS = """
- Candidate 1: Emphasize growth dynamics first, technical precision, measured optimism
- Candidate 2: Lead with risk assessment, balanced skepticism, defensive framing
- Candidate 3: Focus on competitive positioning, strategic narrative, forward-looking
- Candidate 4: Highlight efficiency metrics, operational detail, data-driven tone
"""

DEBATE_CANDIDATE_DIRECTIONS = """
- Candidate 1: Bull aggressive/Bear defensive - strong conviction, sharp contrasts
- Candidate 2: Bull analytical/Bear emotional - data vs sentiment tension
- Candidate 3: Bull long-term/Bear near-term - time horizon conflict, nuanced views
"""

SCENARIO_CANDIDATE_DIRECTIONS = """
- Candidate 1: Hypothesis-centric narratives, tight causal chains (hypothesis → growth → margins → risk → efficiency), analytical tone
- Candidate 2: Risk-first approach, emphasize downside mechanisms, defensive causal reasoning
- Candidate 3: Growth-opportunity focus, optimistic causal chains, forward-looking mechanisms
- Candidate 4: Balanced probabilistic reasoning, scenario-specific sensitivity analysis, nuanced causality
"""

NARRATIVE_TASK_FOCUS = "confidence in categorization accuracy and evidence strength"

OPTION_TASK_FOCUS = "confidence in real options existence and materiality"

MACRO_TASK_FOCUS = "confidence in causal linkages and impact magnitude"

