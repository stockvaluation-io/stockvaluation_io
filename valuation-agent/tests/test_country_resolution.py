from domain.processing.helpers import infer_country_for_macro, preprocess_financials_json


def test_infer_country_for_macro_prefers_explicit_country():
    assert infer_country_for_macro(
        country="India",
        currency="USD",
        ticker="AAPL",
    ) == "India"


def test_infer_country_for_macro_falls_back_to_currency():
    assert infer_country_for_macro(
        country=None,
        currency="USD",
        ticker="AAPL",
    ) == "United States"


def test_preprocess_financials_sets_country_when_missing_in_raw():
    payload = {
        "basic_info_data_dto": {
            "ticker": "AAPL",
            "company_name": "Apple Inc",
            "currency": "USD",
            "industry_us": "consumer-electronics",
            "industry_global": "consumer-electronics",
        },
        "financial_data_dto": {
            "stock_price": 100.0,
            "industry": "consumer-electronics",
        },
    }

    processed = preprocess_financials_json(payload)
    profile = processed.get("profile", {})

    assert profile.get("country") == "United States"
