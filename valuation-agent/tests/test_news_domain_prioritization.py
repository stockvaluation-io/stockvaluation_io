import sys
import types

from services import news_service


def _install_fake_tavily(monkeypatch, search_impl):
    fake_module = types.SimpleNamespace(TavilyClient=lambda: types.SimpleNamespace(search=search_impl))
    monkeypatch.setitem(sys.modules, "tavily", fake_module)


def test_earnings_search_prefers_company_domains(monkeypatch):
    company_domains = ["example.com", "investor.example.com"]
    calls = []

    monkeypatch.setattr(news_service, "_get_dynamic_query", lambda **kwargs: None)
    monkeypatch.setattr(news_service, "extract_company_domains", lambda *args, **kwargs: company_domains)

    def fake_search(**kwargs):
        domains = kwargs.get("include_domains") or []
        calls.append(domains)
        if domains == company_domains:
            return {
                "answer": "Found official filing",
                "results": [
                    {
                        "title": "Q4 Earnings Filing",
                        "url": "https://investor.example.com/q4",
                        "content": "Official investor relations earnings release.",
                        "score": 0.9,
                    }
                ],
            }
        return {"results": []}

    _install_fake_tavily(monkeypatch, fake_search)

    output = news_service.get_latest_earning_report(
        name="Example Corp",
        max_results=3,
        ticker="EXMPL",
        company_url="https://example.com",
    )

    assert calls, "Expected at least one Tavily call"
    assert calls[0] == company_domains
    assert "Q4 Earnings Filing" in output


def test_company_news_search_prefers_company_domains(monkeypatch):
    company_domains = ["example.com", "investor.example.com"]
    calls = []

    monkeypatch.setattr(news_service, "_get_dynamic_query", lambda **kwargs: None)
    monkeypatch.setattr(news_service, "extract_company_domains", lambda *args, **kwargs: company_domains)

    def fake_search(**kwargs):
        domains = kwargs.get("include_domains") or []
        calls.append(domains)
        if domains == company_domains:
            return {
                "results": [
                    {
                        "title": "Official Expansion Update",
                        "url": "https://example.com/news/expansion",
                        "content": "Company announced a major expansion from its corporate newsroom.",
                        "score": 0.95,
                    }
                ],
                "answer": "",
            }
        return {"results": []}

    _install_fake_tavily(monkeypatch, fake_search)

    output = news_service.get_latest_company_news(
        name="Example Corp",
        max_results=3,
        ticker="EXMPL",
        company_url="https://example.com",
    )

    assert calls, "Expected at least one Tavily call"
    assert calls[0] == company_domains
    assert "Official Expansion Update" in output
