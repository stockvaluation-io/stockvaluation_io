import importlib

import llm.routing as routing


def _reload_routing():
    return importlib.reload(routing)


def _clear_provider_keys(monkeypatch):
    for key in (
        "OPENAI_API_KEY",
        "ANTHROPIC_API_KEY",
        "GROQ_API_KEY",
        "GEMINI_API_KEY",
        "OPENROUTER_API_KEY",
        "DEFAULT_LLM_PROVIDER",
        "AGENT_LLM_PROVIDER",
        "JUDGE_LLM_PROVIDER",
        "AGENT_LLM_MODEL",
        "JUDGE_LLM_MODEL",
        "LLM_MODEL",
    ):
        monkeypatch.delenv(key, raising=False)


def test_resolve_agent_llm_auto_selects_configured_provider(monkeypatch):
    _clear_provider_keys(monkeypatch)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    mod = _reload_routing()

    resolved = mod.resolve_agent_llm("narrative")
    assert resolved is not None
    assert resolved["provider"] == "openai"
    assert resolved["role"] == "agent"


def test_resolve_agent_llm_honors_explicit_provider(monkeypatch):
    _clear_provider_keys(monkeypatch)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
    mod = _reload_routing()

    resolved = mod.resolve_agent_llm("narrative", provider="groq")
    assert resolved is not None
    assert resolved["provider"] == "groq"


def test_segments_judge_uses_second_provider_when_available(monkeypatch):
    _clear_provider_keys(monkeypatch)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
    monkeypatch.setenv("DEFAULT_LLM_PROVIDER", "openai")
    mod = _reload_routing()

    resolved = mod.resolve_agent_llm("segments_judge")
    assert resolved is not None
    assert resolved["role"] == "judge"
    assert resolved["provider"] == "groq"


def test_segments_judge_uses_agent_provider_when_single_key(monkeypatch):
    _clear_provider_keys(monkeypatch)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    mod = _reload_routing()

    resolved = mod.resolve_agent_llm("segments_judge")
    assert resolved is not None
    assert resolved["role"] == "judge"
    assert resolved["provider"] == "openai"


def test_news_judge_uses_judge_role_provider(monkeypatch):
    _clear_provider_keys(monkeypatch)
    monkeypatch.setenv("OPENAI_API_KEY", "test-openai-key")
    monkeypatch.setenv("GROQ_API_KEY", "test-groq-key")
    monkeypatch.setenv("DEFAULT_LLM_PROVIDER", "openai")
    mod = _reload_routing()

    resolved = mod.resolve_agent_llm("news_judge")
    assert resolved is not None
    assert resolved["role"] == "judge"
    assert resolved["provider"] == "groq"
