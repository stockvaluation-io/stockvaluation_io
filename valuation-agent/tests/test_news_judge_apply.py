from orchestration.orchestrator import AgentOrchestrator


def test_apply_news_judge_result_uses_cleaned_payload():
    result = AgentOrchestrator._apply_news_judge_result(
        earnings_news="raw earnings",
        general_news="raw company",
        macro_news="raw macro",
        judge_result={
            "cleaned_news": {
                "earnings": "clean earnings",
                "company_news": "clean company",
                "macro": "clean macro",
            }
        },
    )

    assert result == ("clean earnings", "clean company", "clean macro")


def test_apply_news_judge_result_falls_back_on_error():
    result = AgentOrchestrator._apply_news_judge_result(
        earnings_news="raw earnings",
        general_news="raw company",
        macro_news="raw macro",
        judge_result={"error": "llm failed"},
    )

    assert result == ("raw earnings", "raw company", "raw macro")


def test_apply_news_judge_result_falls_back_when_cleaned_fields_blank():
    result = AgentOrchestrator._apply_news_judge_result(
        earnings_news="raw earnings",
        general_news="raw company",
        macro_news="raw macro",
        judge_result={
            "cleaned_news": {
                "earnings": "",
                "company_news": "  ",
                "macro": "clean macro",
            }
        },
    )

    assert result == ("raw earnings", "raw company", "clean macro")
