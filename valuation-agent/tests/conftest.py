"""
Centralized pytest fixtures for stockvaluation.io tests.

This file contains fixtures for valuation and billing tests.
Chat-related fixtures have been moved to bullbeargpt/tests/conftest.py.
"""

import pytest
import time
import os
import requests
from pathlib import Path
from typing import Dict, Any

# ============================================================================
# PATH FIXTURES
# ============================================================================

@pytest.fixture(scope="session")
def tests_dir():
    """Return the tests directory path."""
    return Path(__file__).parent


# ============================================================================
# SECURITY FIXTURES
# ============================================================================

@pytest.fixture
def prompt_guard():
    """Initialize prompt guard for security testing."""
    from security.prompt_guard import PromptGuard
    return PromptGuard(max_messages_per_minute=5, max_message_length=1000)


# ============================================================================
# DOCKER SERVICE FIXTURES
# ============================================================================

@pytest.fixture(scope="session")
def docker_backend_java_url():
    """Get Java backend URL from environment or use default."""
    return (
        os.getenv("VALUATION_SERVICE_BASE_URL")
        or "http://localhost:8081"
    )


@pytest.fixture(scope="session")
def docker_backend_python_url():
    """Get Python backend URL from environment or use default."""
    return os.getenv("PYTHON_BACKEND_URL", "http://localhost:5001")


@pytest.fixture(scope="session")
def docker_services(docker_backend_java_url, docker_backend_python_url):
    """
    Wait for Docker services to be healthy before running tests.
    This fixture ensures all required services are available.
    """
    max_retries = 30
    retry_delay = 2
    
    services = {
        "Java Backend": f"{docker_backend_java_url}/actuator/health",
        "Python Backend": f"{docker_backend_python_url}/health"
    }
    
    print("\n" + "="*80)
    print("Waiting for Docker services to be healthy...")
    print("="*80)
    
    for service_name, health_url in services.items():
        print(f"\nChecking {service_name} at {health_url}...")
        
        for attempt in range(max_retries):
            try:
                response = requests.get(health_url, timeout=5)
                if response.status_code == 200:
                    print(f"✅ {service_name} is healthy!")
                    break
            except requests.exceptions.RequestException as e:
                if attempt < max_retries - 1:
                    print(f"⏳ Attempt {attempt + 1}/{max_retries}: {service_name} not ready, retrying in {retry_delay}s...")
                    time.sleep(retry_delay)
                else:
                    pytest.fail(f"❌ {service_name} failed to become healthy after {max_retries} attempts: {e}")
        else:
            pytest.fail(f"❌ {service_name} is not healthy at {health_url}")
    
    print("\n" + "="*80)
    print("✅ All Docker services are healthy and ready!")
    print("="*80 + "\n")
    
    return {
        "java_backend_url": docker_backend_java_url,
        "python_backend_url": docker_backend_python_url
    }


@pytest.fixture(scope="session")
def baseline_valuation(docker_services):
    """
    Fetch baseline valuation from Java backend for test comparisons.
    This provides real DCF data from the live service.
    """
    java_url = docker_services["java_backend_url"]
    ticker = "NVDA"
    
    # Use GET valuation endpoint
    url = f"{java_url}/api/v1/automated-dcf-analysis/{ticker}/valuation"
    params = {"dify_test": "true"}
    
    print(f"\n🔄 Fetching baseline valuation for {ticker}...")
    
    try:
        response = requests.get(url, params=params, timeout=120)
        response.raise_for_status()
        
        data = response.json()
        
        # Extract from wrapper if needed
        if isinstance(data, dict) and 'data' in data:
            data = data['data']
        
        print(f"✅ Baseline valuation fetched successfully")
        return data
        
    except Exception as e:
        pytest.skip(f"Could not fetch baseline valuation: {e}")


# ============================================================================
# HELPER FIXTURES
# ============================================================================

@pytest.fixture
def wait_for_stability():
    """Helper fixture to wait for system stability between tests."""
    def _wait(seconds=1):
        time.sleep(seconds)
    return _wait
