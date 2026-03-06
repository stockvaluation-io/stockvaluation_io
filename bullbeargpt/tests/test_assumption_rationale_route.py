from app import create_app
from services.valuation_client import ValuationClient


def test_assumption_rationale_reads_nested_valuation_payload(monkeypatch):
    def _fake_get(_self, _valuation_id):
        return {
            "valuation_data": {
                "java_valuation_output": {
                    "assumptionTransparency": {
                        "operatingAssumptions": {"revenueGrowthRateYears2To5": 5.5},
                        "growthAnchor": {
                            "entityDisplay": "Software (Internet)",
                            "p25": 0.08,
                            "p50": 0.12,
                            "p75": 0.16,
                            "confidenceScore": 0.9,
                        },
                    }
                }
            }
        }

    monkeypatch.setattr(ValuationClient, "get_valuation_by_id", _fake_get)
    app = create_app()
    client = app.test_client()

    response = client.get("/bullbeargpt/api/notebook/assumption-rationale/test-valuation-id")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["assumption"] == "revenue_cagr"
    assert payload["selected_value"] == 0.055
    assert payload["damodaran_entity"] == "Software (Internet)"
    assert payload["confidence"] == "HIGH"


def test_assumption_rationale_alias_endpoint(monkeypatch):
    def _fake_get(_self, _valuation_id):
        return {
            "valuation_data": {
                "java_valuation_output": {
                    "assumptionTransparency": {
                        "operatingAssumptions": {"revenueGrowthRateYears2To5": 4.2},
                        "growthAnchor": {
                            "entityDisplay": "Computers/Peripherals",
                            "p25": 0.03,
                            "p50": 0.04,
                            "p75": 0.05,
                            "confidenceScore": 0.6,
                        },
                    }
                }
            }
        }

    monkeypatch.setattr(ValuationClient, "get_valuation_by_id", _fake_get)
    app = create_app()
    client = app.test_client()

    response = client.get("/api-s/valuation/test-valuation-id/assumption-rationale")
    assert response.status_code == 200
    payload = response.get_json()
    assert payload["assumption"] == "revenue_cagr"
    assert payload["selected_value"] == 0.042
