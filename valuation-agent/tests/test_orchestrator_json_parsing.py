import math

from orchestration import orchestrator as orchestrator_module
from orchestration.orchestrator import AgentOrchestrator


class _FakeClient:
    def invoke(self, _messages):
        return object()


def test_run_narrative_agent_parses_code_fenced_json(monkeypatch):
    orchestrator = AgentOrchestrator()

    monkeypatch.setattr(
        orchestrator,
        "_dump_llm_response",
        lambda **_kwargs: """```json
{
  "valuation_drivers": ["pricing power"],
  "summary_hypothesis": "First sentence. Second sentence. Third sentence.",
  "tone": "balanced"
}
```""",
    )

    result = orchestrator._run_narrative_agent(
        {"max_sentences": 2},
        _FakeClient(),
        "prompt",
    )

    assert result.get("tone") == "balanced"
    assert result.get("summary_hypothesis") == "First sentence. Second sentence."


def test_run_segments_agent_parses_code_fenced_json(monkeypatch):
    orchestrator = AgentOrchestrator()

    monkeypatch.setattr(orchestrator_module, "get_cached_segments", lambda *_args, **_kwargs: None)
    monkeypatch.setattr(orchestrator_module, "set_cached_segments", lambda *_args, **_kwargs: None)
    monkeypatch.setattr(orchestrator, "_should_run_segments_judge", lambda *_args, **_kwargs: False)
    monkeypatch.setattr(
        orchestrator,
        "_dump_llm_response",
        lambda **_kwargs: """```json
{
  "segments": [
    {
      "name": "iPhone",
      "revenue_share": 0.7,
      "operating_margin": 0.32,
      "sector": "consumer-electronics"
    },
    {
      "name": "Services",
      "revenue_share": 0.3,
      "operating_margin": 0.55,
      "sector": "software-application"
    }
  ]
}
```""",
    )

    import services.news_service as news_service_module

    monkeypatch.setattr(
        news_service_module,
        "get_company_segments",
        lambda _company: {"raw": "segment-data"},
    )

    result = orchestrator._run_segments_agent(
        {
            "name": "Apple Inc",
            "industry": "consumer-electronics",
            "description": "Consumer electronics company",
        },
        _FakeClient(),
        "<<SEGMENT_DATA>>\n<<DESCRIPTION>>",
    )

    assert isinstance(result.get("segments"), list)
    assert result["segments"]
    total_share = sum(float(seg.get("revenue_share", 0.0)) for seg in result["segments"])
    assert math.isclose(total_share, 1.0, rel_tol=0.0, abs_tol=0.01)
