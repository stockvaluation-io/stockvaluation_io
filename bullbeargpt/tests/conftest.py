"""
Minimal test fixtures for current bullbeargpt layout.
"""
import os
import sys
from pathlib import Path
import pytest


TESTS_DIR = Path(__file__).resolve().parent
BULLBEAR_ROOT = TESTS_DIR.parent
REPO_ROOT = BULLBEAR_ROOT.parent
for path in (str(BULLBEAR_ROOT), str(REPO_ROOT)):
    if path not in sys.path:
        sys.path.insert(0, path)

# Keep production config strict, but ensure tests always have a local secret.
os.environ.setdefault("SECRET_KEY", "test-bullbeargpt-secret")


@pytest.fixture(scope="session")
def valuation_service_base_url() -> str:
    return (
        os.getenv("VALUATION_SERVICE_BASE_URL")
        or "http://localhost:8081"
    )


@pytest.fixture(scope="session")
def valuation_service_api_url(valuation_service_base_url: str) -> str:
    return f"{valuation_service_base_url}/api/v1/automated-dcf-analysis"
