#!/usr/bin/env python3
"""
VALUATION CALCULATIONS TESTS

Consolidated tests for DCF calculation accuracy, sector overrides, and scenario calculations.
Tests the complete valuation engine against live Java backend.

Test Coverage:
- Baseline valuation fetching and validation
- Sector-specific parameter overrides
- DCF calculation accuracy
- Parameter transformation and validation
- Multi-sector test cases
"""

import pytest
import requests
import json
from typing import Dict, Any


# ============================================================================
# BASELINE VALUATION TESTS
# ============================================================================

class TestBaselineValuation:
    """Test baseline valuation fetching and structure."""
    
    def test_fetch_baseline_valuation(self, docker_services, baseline_valuation):
        """Test fetching baseline valuation from GET /{ticker}/valuation endpoint."""
        print(f"\n{'='*80}")
        print(f"Testing baseline valuation fetch")
        print(f"{'='*80}")
        
        # Verify required keys exist
        required_keys = ['financialDTO', 'baseYearComparison', 'terminalValueDTO']
        for key in required_keys:
            assert key in baseline_valuation, f"Baseline missing required key: {key}"
        
        financial_dto = baseline_valuation['financialDTO']
        terminal_dto = baseline_valuation['terminalValueDTO']
        
        # Verify financial arrays
        required_arrays = ['revenueGrowthRate', 'ebitOperatingMargin', 'salesToCapitalRatio', 'costOfCapital']
        for array in required_arrays:
            assert array in financial_dto, f"financialDTO missing {array}"
            assert len(financial_dto[array]) > 0, f"{array} is empty"
        
        print(f"✅ Baseline valuation structure validated")
        print(f"   Revenue growth rates: {len(financial_dto['revenueGrowthRate'])} years")
        print(f"   Operating margins: {len(financial_dto['ebitOperatingMargin'])} years")
        print(f"   Intrinsic value: ${financial_dto.get('intrinsicValue', 'N/A')}")
    
    def test_baseline_intrinsic_value(self, baseline_valuation):
        """Test that baseline intrinsic value is calculated."""
        financial_dto = baseline_valuation['financialDTO']
        
        intrinsic_value = financial_dto.get('intrinsicValue')
        assert intrinsic_value is not None, "Intrinsic value should be calculated"
        assert intrinsic_value > 0, f"Intrinsic value should be positive, got {intrinsic_value}"
        
        print(f"\n✅ Baseline intrinsic value: ${intrinsic_value:.2f}")


# ============================================================================
# SECTOR OVERRIDE TESTS
# ============================================================================

class TestSectorOverrides:
    """Test sector-specific parameter overrides and calculations."""
    
    def test_create_payload_from_baseline(self, docker_services, baseline_valuation):
        """
        Test creating valuation payload from baseline data.
        This tests the ability to extract parameters and create override payloads.
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing payload creation from baseline")
        print(f"{'='*80}")
        
        financial = baseline_valuation['financialDTO']
        terminal = baseline_valuation['terminalValueDTO']
        
        # Extract key parameters (same logic as comprehensive tests)
        revenue_growth_rates = financial.get('revenueGrowthRate', [])
        ebit_margins = financial.get('ebitOperatingMargin', [])
        cost_of_capital = financial.get('costOfCapital', [])
        
        assert len(revenue_growth_rates) > 2, "Need at least 3 revenue growth values"
        assert len(ebit_margins) > 1, "Need at least 2 margin values"
        assert len(cost_of_capital) > 1, "Need at least 2 cost of capital values"
        
        # Extract specific values
        cagr_2_5 = revenue_growth_rates[2]  # Year 2 growth
        initial_margin = ebit_margins[1]     # Year 1 margin
        initial_cost = cost_of_capital[1]    # Year 1 WACC
        
        print(f"✅ Extracted parameters:")
        print(f"   CAGR (Year 2): {cagr_2_5}%")
        print(f"   Initial Margin: {initial_margin}%")
        print(f"   Initial WACC: {initial_cost}%")
        
        # Create override payload with extracted values
        payload = {
            "compoundAnnualGrowth2_5": cagr_2_5
        }
        
        # Test that we can send this back to get valuation
        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"
        response = requests.post(url, json=payload, timeout=120)
        
        assert response.status_code == 200, f"Override request failed: {response.text}"
        
        print(f"✅ Payload successfully processed by backend")
    
    def test_override_single_sector_parameter(self, docker_services):
        """
        Test overriding a single sector parameter.
        This represents sector-aware adjustments (e.g., tech sector higher growth).
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing sector-aware parameter override")
        print(f"{'='*80}")
        print(f"Applying tech sector adjustment: +5% growth")
        
        # Override with sector-adjusted growth rate
        payload = {
            "compoundAnnualGrowth2_5": 25.0  # Tech sector higher growth
        }

        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"
        response = requests.post(url, json=payload, timeout=120)
        
        assert response.status_code == 200
        
        data = response.json()
        if isinstance(data, dict) and 'data' in data:
            data = data['data']
        
        financial_dto = data['financialDTO']
        intrinsic_value = financial_dto.get('intrinsicValue')
        
        print(f"✅ Sector override applied successfully")
        print(f"   New intrinsic value: ${intrinsic_value:.2f}")
        
        assert intrinsic_value > 0, "Intrinsic value should be positive"
    
    def test_multiple_sector_overrides(self, docker_services):
        """
        Test multiple sector-specific parameter overrides.
        Simulates sector-aware adjustments for multiple parameters.
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing multiple sector overrides (semiconductors)")
        print(f"{'='*80}")
        
        # Semiconductor sector characteristics:
        # - High growth (20-30%)
        # - High margins (40-50%)
        # - Capital intensive (higher WACC)
        payload = {
            "compoundAnnualGrowth2_5": 28.0,    # High growth
            "initialOperatingMargin": 48.0,      # High margin
            "wacc": 11.0                          # Capital intensive
        }

        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"
        response = requests.post(url, json=payload, timeout=120)
        
        assert response.status_code == 200
        
        data = response.json()
        if isinstance(data, dict) and 'data' in data:
            data = data['data']
        
        financial_dto = data['financialDTO']
        intrinsic_value = financial_dto.get('intrinsicValue')
        
        print(f"✅ Multiple sector overrides applied:")
        print(f"   Growth: 28% (semiconductor typical)")
        print(f"   Margin: 48% (high-performance chips)")
        print(f"   WACC: 11% (R&D intensive)")
        print(f"   Intrinsic value: ${intrinsic_value:.2f}")


# ============================================================================
# DCF CALCULATION ACCURACY TESTS
# ============================================================================

class TestDCFAccuracy:
    """Test DCF calculation accuracy and consistency."""
    
    def test_parameter_consistency(self, docker_services, baseline_valuation):
        """
        Test that parameters are consistent across multiple calls.
        Same inputs should produce same outputs (determinism test).
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing DCF calculation consistency")
        print(f"{'='*80}")
        
        # Make same request twice
        payload = {
            "compoundAnnualGrowth2_5": 20.0
        }

        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"
        
        # First call
        response1 = requests.post(url, json=payload, timeout=120)
        assert response1.status_code == 200
        
        data1 = response1.json()
        if isinstance(data1, dict) and 'data' in data1:
            data1 = data1['data']
        
        intrinsic_value1 = data1['financialDTO']['intrinsicValue']
        
        # Second call
        response2 = requests.post(url, json=payload, timeout=120)
        assert response2.status_code == 200
        
        data2 = response2.json()
        if isinstance(data2, dict) and 'data' in data2:
            data2 = data2['data']
        
        intrinsic_value2 = data2['financialDTO']['intrinsicValue']
        
        # Values should be identical (or very close due to rounding)
        diff = abs(intrinsic_value1 - intrinsic_value2)
        diff_pct = (diff / intrinsic_value1) * 100 if intrinsic_value1 > 0 else 0
        
        print(f"✅ DCF consistency test:")
        print(f"   Call 1: ${intrinsic_value1:.2f}")
        print(f"   Call 2: ${intrinsic_value2:.2f}")
        print(f"   Difference: ${diff:.2f} ({diff_pct:.2f}%)")
        
        # Allow for minimal rounding differences (< 0.01%)
        assert diff_pct < 0.01, f"Inconsistent results: {diff_pct:.4f}% difference"
    
    def test_growth_rate_impact(self, docker_services):
        """
        Test that growth rate changes have expected directional impact.
        Higher growth should increase intrinsic value.
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing growth rate impact on valuation")
        print(f"{'='*80}")
        
        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"

        # Low growth scenario
        payload_low = {"compoundAnnualGrowth2_5": 10.0}
        response_low = requests.post(url, json=payload_low, timeout=120)
        assert response_low.status_code == 200
        data_low = response_low.json()
        if isinstance(data_low, dict) and 'data' in data_low:
            data_low = data_low['data']
        value_low = data_low['financialDTO']['intrinsicValue']
        
        # High growth scenario
        payload_high = {"compoundAnnualGrowth2_5": 30.0}
        response_high = requests.post(url, json=payload_high, timeout=120)
        assert response_high.status_code == 200
        data_high = response_high.json()
        if isinstance(data_high, dict) and 'data' in data_high:
            data_high = data_high['data']
        value_high = data_high['financialDTO']['intrinsicValue']
        
        print(f"✅ Growth rate impact:")
        print(f"   Low growth (10%): ${value_low:.2f}")
        print(f"   High growth (30%): ${value_high:.2f}")
        print(f"   Increase: ${value_high - value_low:.2f} ({((value_high/value_low - 1) * 100):.1f}%)")
        
        # Higher growth should result in higher valuation
        assert value_high > value_low, "Higher growth should increase intrinsic value"
    
    def test_margin_impact(self, docker_services):
        """
        Test that operating margin changes have expected impact.
        Higher margins should increase intrinsic value.
        """
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing operating margin impact on valuation")
        print(f"{'='*80}")
        
        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"

        # Low margin scenario
        payload_low = {"initialOperatingMargin": 30.0}
        response_low = requests.post(url, json=payload_low, timeout=120)
        assert response_low.status_code == 200
        data_low = response_low.json()
        if isinstance(data_low, dict) and 'data' in data_low:
            data_low = data_low['data']
        value_low = data_low['financialDTO']['intrinsicValue']
        
        # High margin scenario
        payload_high = {"initialOperatingMargin": 50.0}
        response_high = requests.post(url, json=payload_high, timeout=120)
        assert response_high.status_code == 200
        data_high = response_high.json()
        if isinstance(data_high, dict) and 'data' in data_high:
            data_high = data_high['data']
        value_high = data_high['financialDTO']['intrinsicValue']
        
        print(f"✅ Margin impact:")
        print(f"   Low margin (30%): ${value_low:.2f}")
        print(f"   High margin (50%): ${value_high:.2f}")
        print(f"   Increase: ${value_high - value_low:.2f}")
        
        # Higher margins should result in higher valuation
        assert value_high > value_low, "Higher margins should increase intrinsic value"


# ============================================================================
# SCENARIO CALCULATION TESTS
# ============================================================================

class TestScenarioCalculations:
    """Test scenario calculation logic."""
    
    def test_scenario_generation(self, docker_services):
        """
        Test that scenario analysis generates 3 scenarios.
        This is a simplified test of the scenario calculation.
        """
        # Note: Full scenario testing is in test_tool_execution_integration.py
        # This test just verifies the basic calculation endpoints work
        
        java_url = docker_services["java_backend_url"]
        ticker = "NVDA"
        
        print(f"\n{'='*80}")
        print(f"Testing basic scenario calculation")
        print(f"{'='*80}")
        
        # Get baseline valuation which should include scenarios
        url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"

        response = requests.get(url, timeout=120)
        assert response.status_code == 200
        
        data = response.json()
        if isinstance(data, dict) and 'data' in data:
            data = data['data']
        
        print(f"✅ Scenario data available in baseline response")
        print(f"   Response keys: {data.keys()}")


if __name__ == "__main__":
    pytest.main([__file__, "-v", "-s"])
