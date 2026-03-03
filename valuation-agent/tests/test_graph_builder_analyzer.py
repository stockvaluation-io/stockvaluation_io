from domain.models.valuation import GraphState
from orchestration.graph_builder import GraphBuilder


class _FakeOrchestrator:
    def __init__(self, result):
        self.result = result
        self.calls = []

    def run_agent(self, agent_name, inputs):
        self.calls.append((agent_name, inputs))
        return self.result


def _state():
    return GraphState(
        dcf={"derived": {"revenue_cagr_pct": 10.0, "margin_end_pct": 20.0}, "sales_to_capital_ratio": [1.2, 1.1, 1.0]},
        financials={"profile": {"industry": "Technology"}},
        segments={"segments": [{"sector": "software-application", "industry": "Software", "revenue_share": 0.8}]},
        ticker="TEST",
        name="Test Co",
        industry="Technology",
        news={"tone": "neutral", "valuation_drivers": {}},
        merged_result={},
    )


def test_analyzer_wrapper_uses_llm_analyzer_agent():
    orchestrator = _FakeOrchestrator(
        {
            "dcf_analysis": {
                "dcf_adjustment_instructions": [
                    {"parameter": "revenue_cagr", "new_value": 11.0, "unit": "percent", "rationale": "Improved demand."}
                ]
            },
            "recommendations": {"confidence_level": "medium"},
            "analyzer_metadata": {"version": "analyzer_v1"},
        }
    )
    graph_builder = GraphBuilder(orchestrator)

    result = graph_builder._analyzer_agent_wrapper(_state())

    assert orchestrator.calls
    assert orchestrator.calls[0][0] == "analyzer"
    assert "merged_result" in result
    assert result["merged_result"]["dcf_analysis"]["dcf_adjustment_instructions"][0]["parameter"] == "revenue_cagr"


def test_analyzer_wrapper_falls_back_when_llm_fails():
    orchestrator = _FakeOrchestrator({"error": "llm failed"})
    graph_builder = GraphBuilder(orchestrator)

    result = graph_builder._analyzer_agent_wrapper(_state())

    assert orchestrator.calls
    assert "merged_result" in result
    assert "dcf_analysis" in result["merged_result"]


def test_analyst_wrapper_adds_narrative_sections():
    orchestrator = _FakeOrchestrator(
        {
            "title": "Test Co Valuation",
            "growth": {"title": "Growth", "narrative": "Growth is supported by product expansion."},
            "margins": {"title": "Margins", "narrative": "Margins remain resilient due to scale."},
            "investment_efficiency": {"title": "Investment Efficiency", "narrative": "Capital allocation remains disciplined."},
            "risks": {"title": "Risks", "narrative": "Competition and macro demand are key risks."},
            "key_takeaways": {"title": "Key Takeaways", "narrative": "Quality business, but valuation sensitivity remains high."},
        }
    )
    graph_builder = GraphBuilder(orchestrator)
    state = _state()
    state.merged_result = {"dcf_analysis": {"dcf_adjustment_instructions": []}}

    result = graph_builder._analyst_agent_wrapper(state)

    assert orchestrator.calls
    assert orchestrator.calls[0][0] == "analyst"
    assert "merged_result" in result
    assert result["merged_result"]["growth"]["narrative"] == "Growth is supported by product expansion."
    assert "dcf_analysis" in result["merged_result"]


def test_merger_prefers_analyst_sections_when_available():
    graph_builder = GraphBuilder(_FakeOrchestrator({}))
    state = _state()
    state.news = {
        "valuation_drivers": {"growth": "News-only growth text"},
        "summary_hypothesis": "News-only takeaway",
        "tone": "neutral",
    }
    state.merged_result = {
        "growth": {"title": "Growth", "narrative": "Analyst growth text"},
        "key_takeaways": {"title": "Key Takeaways", "narrative": "Analyst takeaway"},
        "dcf_analysis": {"dcf_adjustment_instructions": []},
    }

    result = graph_builder._merger_agent_wrapper(state)
    merged = result["merged_result"]

    assert merged["growth"]["narrative"] == "Analyst growth text"
    assert merged["key_takeaways"]["narrative"] == "Analyst takeaway"
    assert "dcf_analysis" in merged
