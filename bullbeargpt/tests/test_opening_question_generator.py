from services.opening_question_generator import generate_opening_question


def test_opening_question_extracts_growth_and_wacc_from_non_default_paths():
    valuation_data = {
        "companyDTO": {
            "estimatedValuePerShare": 192.31,
            "price": 59.74,
            "terminalCostOfCapital": 8.5,
        },
        "dcf_analysis": {
            "proposed_assumptions": {
                "revenue_cagr": 4.2,
            },
        },
        "java_valuation_output": {
            "currency": "EUR",
            "stockCurrency": "USD",
        },
    }

    question = generate_opening_question(
        ticker="EVVTY",
        valuation_data=valuation_data,
        company_name="Evolution AB (publ)",
    )

    assert "Revenue growth: 4.20%" in question
    assert "Discount rate (WACC): 8.50%" in question


def test_opening_question_flags_currency_mismatch_when_present():
    valuation_data = {
        "companyDTO": {
            "estimatedValuePerShare": 150.0,
            "price": 100.0,
        },
        "dcf_analysis": {
            "assumptions": {
                "revenue_growth_rate": 6.0,
                "wacc": 9.0,
            },
        },
        "java_valuation_output": {
            "currency": "EUR",
            "stockCurrency": "USD",
        },
    }

    question = generate_opening_question("TEST", valuation_data)

    assert "**Currency note:** Fair value is in EUR while current market price is in USD." in question
    assert "current price of 100.00 USD" in question
    assert "valued TEST at **150.00 EUR**" in question
