from api.app import StockValuationApp
from domain.processing.helpers import preprocess_financials_json


def _app() -> StockValuationApp:
    return StockValuationApp.__new__(StockValuationApp)


def test_map_adjustments_to_java_overrides_supports_sector_overrides():
    app = _app()
    overrides, meta = app._map_adjustments_to_java_overrides(
        adjustments=[
            {"parameter": "revenue_cagr", "new_value": 6.2, "unit": "percent", "rationale": "baseline uplift"}
        ],
        sector_adjustments=[
            {
                "sector": "software-application",
                "parameter": "operating_margin",
                "value": 2.0,
                "unit": "percent",
                "adjustment_type": "relative_additive",
                "timeframe": "years_1_to_5",
            },
            {
                "sector": "semiconductors",
                "parameter": "sales_to_capital",
                "value": 4.4,
                "unit": "x",
                "adjustment_type": "absolute",
                "timeframe": "both",
            },
        ],
        mapped_segments=[
            {"sector": "software-application"},
            {"sector": "semiconductors"},
        ],
    )

    assert overrides["compoundAnnualGrowth2_5"] == 6.2
    assert "sectorOverrides" in overrides
    assert len(overrides["sectorOverrides"]) == 2
    assert overrides["sectorOverrides"][0]["sectorName"] == "software-application"
    assert overrides["sectorOverrides"][0]["parameterType"] == "operating_margin"
    assert overrides["sectorOverrides"][0]["adjustmentType"] == "relative_additive"
    # Sector overrides pass sales_to_capital in plain x units.
    assert overrides["sectorOverrides"][1]["value"] == 4.4
    assert meta["sector_count"] == 2
    assert len(meta["sector_mapped"]) == 2


def test_map_adjustments_to_java_overrides_skips_unknown_sector():
    app = _app()
    overrides, meta = app._map_adjustments_to_java_overrides(
        adjustments=[],
        sector_adjustments=[
            {
                "sector": "unknown-sector",
                "parameter": "operating_margin",
                "value": 1.0,
                "adjustment_type": "relative_additive",
            }
        ],
        mapped_segments=[{"sector": "software-application"}],
    )

    assert "sectorOverrides" not in overrides
    assert len(meta["sector_unmapped"]) == 1
    assert meta["sector_unmapped"][0]["reason"] == "unknown_sector"


def test_map_adjustments_to_java_overrides_normalizes_legacy_sector_sales_to_capital():
    app = _app()
    overrides, _meta = app._map_adjustments_to_java_overrides(
        adjustments=[],
        sector_adjustments=[
            {
                "sector": "information-technology-services",
                "parameter": "sales_to_capital",
                "value": 320.0,
                "unit": "x",
                "adjustment_type": "absolute",
                "timeframe": "years_1_to_5",
            }
        ],
        mapped_segments=[{"sector": "information-technology-services"}],
    )

    assert overrides["sectorOverrides"][0]["value"] == 3.2


def test_assumption_transparency_does_not_use_terminal_growth_as_risk_free():
    app = _app()
    transparency = app._build_assumption_transparency(
        dcf={
            "financialDTO": {"costOfCapital": [8.2, 8.1, 8.0]},
            "terminalValueDTO": {"growthRate": 3.84, "costOfCapital": 8.07},
        },
        adjustments=[],
        java_overrides={},
        mapped_segments=[],
    )

    assert transparency["discountRate"]["riskFreeRate"] is None
    assert transparency["discountRate"]["terminalCostOfCapital"] == 8.07


def test_assumption_transparency_preserves_market_implied_expectations_from_java():
    app = _app()
    transparency = app._build_assumption_transparency(
        dcf={
            "assumptionTransparency": {
                "marketImpliedExpectations": {
                    "marketPrice": 100.0,
                    "modelIntrinsicValue": 90.0,
                    "metrics": [{"key": "revenue_cagr", "solved": True}],
                }
            },
            "financialDTO": {"costOfCapital": [8.0, 8.1, 8.2]},
            "terminalValueDTO": {"costOfCapital": 8.2},
        },
        adjustments=[],
        java_overrides={},
        mapped_segments=[],
    )

    assert transparency["marketImpliedExpectations"]["marketPrice"] == 100.0
    assert transparency["marketImpliedExpectations"]["metrics"][0]["key"] == "revenue_cagr"


def test_assumption_transparency_uses_effective_final_values_when_segment_weighted():
    app = _app()
    transparency = app._build_assumption_transparency(
        dcf={
            "financialDTO": {
                "revenueGrowthRate": [None, 8.0, 6.0, 6.0, 6.0, 6.0, 5.0],
                "ebitOperatingMargin": [30.0, 30.0, 24.0, 24.0, 24.0, 24.0, 24.0],
                "salesToCapitalRatio": [None, 2.0, 2.0, 2.0, 2.0, 2.0, 1.8, 1.8, 1.8, 1.8, 1.8],
                "costOfCapital": [9.1, 9.0, 8.9],
            },
            "terminalValueDTO": {"costOfCapital": 8.9},
        },
        adjustments=[],
        java_overrides={
            "compoundAnnualGrowth2_5": 12.5,
            "targetPreTaxOperatingMargin": 36.0,
            "salesToCapitalYears1To5": 360.0,
            "salesToCapitalYears6To10": 360.0,
            "sectorOverrides": [{"sectorName": "software-application", "parameterType": "operating_margin"}],
        },
        mapped_segments=[{"sector": "software-application"}, {"sector": "consumer-electronics"}],
    )

    # Effective values come from final FCFF arrays, not requested override values.
    assert transparency["operatingAssumptions"]["revenueGrowthRateYears2To5"] == 6.0
    assert transparency["operatingAssumptions"]["targetOperatingMargin"] == 24.0
    assert transparency["operatingAssumptions"]["salesToCapitalYears1To5"] == 2.0
    assert "requested 12.50%" in (transparency["operatingAssumptions"]["revenueGrowthSource"] or "")
    assert "sector override(s) included" in (transparency["operatingAssumptions"]["operatingMarginSource"] or "")


def test_assumption_transparency_includes_cost_of_capital_rationale_by_default():
    app = _app()
    transparency = app._build_assumption_transparency(
        dcf={
            "financialDTO": {"costOfCapital": [9.2, 9.1, 9.0]},
            "terminalValueDTO": {"costOfCapital": 9.0},
            "assumptionTransparency": {
                "discountRate": {"riskFreeRate": 4.1}
            },
        },
        adjustments=[],
        java_overrides={},
        mapped_segments=[],
    )

    rationale = (transparency.get("adjustmentRationales") or {}).get("costOfCapital")
    assert rationale is not None
    assert "Initial cost of capital is" in rationale
    assert "Risk-free anchor is" in rationale


def test_build_growth_anchor_prefers_java_growth_skill_context():
    app = _app()
    anchor = app._build_growth_anchor(
        {
            "growthSkillContext": {
                "entity": "softwareinternet",
                "entityDisplay": "Software (Internet)",
                "region": "United States",
                "year": 2026,
                "p25": 0.08,
                "p50": 0.12,
                "p75": 0.16,
                "confidenceScore": 0.9,
            }
        }
    )

    assert anchor is not None
    assert anchor["entity"] == "softwareinternet"
    assert anchor["source"] == "valuation-service growthSkillContext"


def test_build_raw_financials_produces_non_empty_preprocessed_payload():
    app = _app()
    raw_dcf = app._deep_snake_case(
        {
            "companyName": "Apple Inc",
            "industryUs": "consumer-electronics",
            "industryGlobal": "consumer-electronics",
            "currency": "USD",
            "baseYearComparison": {
                "revenue": 435_617_000_000.0,
                "operatingIncome": 139_397_440_000.0,
                "revenueGrowthCompany": 1.88,
            },
            "companyDTO": {
                "debt": 90_509_000_000.0,
                "cash": 66_907_000_000.0,
                "minorityInterests": 0.0,
                "price": 264.72,
                "numberOfShares": 14_697_926_000.0,
            },
            "financialDTO": {
                "costOfCapital": [9.24, 8.93],
                "taxRate": [16.0, 25.0],
                "investedCapital": [111_792_000_000.0, 117_755_065_476.5],
                "revenues": [435_617_000_000.0, 464_149_913_500.0],
                "ebitOperatingIncome": [139_397_440_000.0, 148_527_972_320.0],
                "salesToCapitalRatio": [None, 2.6],
                "stockPrice": 264.72,
                "noOfShareOutstanding": 14_697_926_000.0,
            },
        }
    )

    raw_financials = app._build_raw_financials_from_java_output(
        ticker="AAPL",
        company_name="Apple Inc",
        raw_dcf=raw_dcf,
    )
    preprocessed = preprocess_financials_json(raw_financials)

    assert preprocessed["company"] == "Apple Inc"
    assert preprocessed["ticker"] == "AAPL"
    assert preprocessed["valuation"]["market_cap"] is not None
    assert preprocessed["profitability"]["revenue_ttm"] is not None
    assert preprocessed["profile"]["industry"] == "consumer-electronics"
