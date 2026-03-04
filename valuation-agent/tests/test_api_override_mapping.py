from api.app import StockValuationApp


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
    # Java stores sales_to_capital in percent-style units (4.4x -> 440.0)
    assert overrides["sectorOverrides"][1]["value"] == 440.0
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
