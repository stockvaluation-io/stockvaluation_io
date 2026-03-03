from llm.client_manager import LLMClientManager


def test_sanitize_openai_compat_model_kwargs_for_gemini():
    raw = {
        "response_format": {"type": "json_object"},
        "frequency_penalty": 0.0,
        "presence_penalty": 0.0,
        "logit_bias": {"1": 1},
    }

    sanitized = LLMClientManager._sanitize_openai_compat_model_kwargs("gemini", raw)

    assert "frequency_penalty" not in sanitized
    assert "presence_penalty" not in sanitized
    assert "logit_bias" not in sanitized
    assert sanitized.get("response_format") == {"type": "json_object"}


def test_sanitize_openai_compat_model_kwargs_for_openai_keeps_fields():
    raw = {
        "response_format": {"type": "json_object"},
        "frequency_penalty": 0.0,
        "presence_penalty": 0.0,
    }

    sanitized = LLMClientManager._sanitize_openai_compat_model_kwargs("openai", raw)

    assert sanitized == raw
