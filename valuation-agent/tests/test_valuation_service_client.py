import pytest

from services.valuation_service_client import ValuationServiceClient


class _FakeResponse:
    def __init__(self, status_code=200, payload=None):
        self.status_code = status_code
        self._payload = payload or {"data": {"ok": True}}

    def json(self):
        return self._payload


def test_recalculate_valuation_uses_ticker_valuation_path_and_preserves_segments(monkeypatch):
    captured = {}

    def _fake_post(url, params=None, json=None, headers=None, timeout=None):
        captured["url"] = url
        captured["params"] = params
        captured["json"] = json
        captured["headers"] = headers
        captured["timeout"] = timeout
        return _FakeResponse()

    monkeypatch.setattr("services.valuation_service_client.requests.post", _fake_post)

    client = ValuationServiceClient(base_url="http://localhost:8081/api/v1/automated-dcf-analysis", timeout=11)
    overrides = {
        "compoundAnnualGrowth2_5": 18.5,
        "segments": {
            "segments": [
                {
                    "sector": "software-infrastructure",
                    "industry": "Software (System & Application)",
                    "components": ["Cloud", "Security"],
                    "mappingScore": 0.93,
                    "revenueShare": 0.7,
                    "operatingMargin": 0.32,
                }
            ]
        },
    }

    result = client.recalculate_valuation("MSFT", overrides)

    assert result == {"ok": True}
    assert captured["url"] == "http://localhost:8081/api/v1/automated-dcf-analysis/MSFT/valuation"
    assert captured["params"] is None
    assert captured["json"] == overrides
    assert captured["headers"]["Content-Type"] == "application/json"
    assert "dify_test" not in captured["headers"]
    assert captured["timeout"] == 11
