from prompts.utils.prompt_dumper import PromptDumper


def test_prompt_dumper_does_not_auto_enable_with_gemini_key_by_default(monkeypatch, tmp_path):
    monkeypatch.setenv("FLASK_ENV", "development")
    monkeypatch.delenv("DUMP_PROMPTS", raising=False)
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setenv("PROMPT_DUMP_DIR", str(tmp_path))

    dumper = PromptDumper()

    assert dumper.enabled is False
    assert dumper.dump_responses_enabled is False


def test_prompt_dumper_auto_enable_requires_explicit_flag(monkeypatch, tmp_path):
    monkeypatch.setenv("FLASK_ENV", "development")
    monkeypatch.delenv("DUMP_PROMPTS", raising=False)
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setenv("AUTO_ENABLE_GEMINI_DUMPS", "true")
    monkeypatch.setenv("PROMPT_DUMP_DIR", str(tmp_path))

    dumper = PromptDumper()

    assert dumper.enabled is True
