from prompts.utils.prompt_dumper import PromptDumper


def test_prompt_dumper_auto_enables_with_gemini_key(monkeypatch, tmp_path):
    monkeypatch.delenv("DUMP_PROMPTS", raising=False)
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setenv("PROMPT_DUMP_DIR", str(tmp_path))

    dumper = PromptDumper()

    assert dumper.enabled is True
    assert dumper.dump_responses_enabled is True


def test_prompt_dumper_explicit_false_overrides_auto_enable(monkeypatch, tmp_path):
    monkeypatch.setenv("DUMP_PROMPTS", "false")
    monkeypatch.setenv("GEMINI_API_KEY", "test-key")
    monkeypatch.setenv("PROMPT_DUMP_DIR", str(tmp_path))

    dumper = PromptDumper()

    assert dumper.enabled is False
