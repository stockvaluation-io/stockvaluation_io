# PRD: Keyless Frankfurter Currency Provider

## Problem Statement

StockValuation.io v1 is a Docker-backed agent-native valuation product. The local valuation service currently depends on a commercial currency API configuration path that requires `CURRENCY_API_KEY`. That creates setup friction and causes avoidable valuation failures for international tickers when the key is missing, invalid, or unavailable.

TSM is the current visible failure case: market price currency and financial statement currency differ, and conversion can fail before the DCF model can complete. The product should keep that failure explicit when conversion data is truly unavailable, but it should not require users to provision a paid or account-bound currency key for normal local valuation.

## Solution

Replace the current key-required currency API path with a single keyless provider: Frankfurter.

Frankfurter is the only supported v1 currency-rate source. The service will fetch rates from the public Frankfurter API, normalize them into the existing currency conversion model, and keep conversion math inside `valuation-service`.

Reference docs:

- Frankfurter public API and v2 docs: https://frankfurter.dev/
- Frankfurter v2 docs: https://frankfurter.dev/docs/

Key properties from the provider docs:

- The public API is hosted at `https://api.frankfurter.dev`.
- The API requires no key.
- The project is open source and can be self-hosted later if needed.
- v2 supports pair rates such as `/v2/rate/USD/TWD`.
- v2 supports latest rates such as `/v2/rates?base=USD`.
- Frankfurter does not perform conversion as a dedicated endpoint; callers fetch a rate and calculate the amount locally.

## Goals

1. Remove `CURRENCY_API_KEY` as a required local setup value.
2. Use Frankfurter as the only v1 currency API provider.
3. Keep currency conversion inside `valuation-service`.
4. Keep MCP tools structured and agent-readable.
5. Make TSM succeed when Frankfurter returns `USD/TWD`.
6. Preserve explicit `currency_conversion_failed` failures when Frankfurter is unavailable or a required currency is not returned.
7. Keep report writing in the user agent through installed skills.
8. Maintain Docker-only v1 runtime.

## Non-Goals

1. Do not add a second commercial currency provider.
2. Do not keep CurrencyBeacon as a fallback.
3. Do not require any currency API key.
4. Do not move conversion math into MCP, CLI, skills, yfinance, or the user's agent.
5. Do not add a native no-Docker runtime.
6. Do not generate investment recommendations.
7. Do not hide missing currency data by inventing rates.

## User Stories

1. As a local agent-native user, I want setup to work without a currency API key, so that Docker service startup is simpler.
2. As a user valuing international tickers, I want supported currency conversions to work automatically, so that companies like TSM are not blocked by local key setup.
3. As a user, I want failed currency conversion to be explained clearly, so that my agent does not paste raw service JSON into a report.
4. As a maintainer, I want one currency provider path, so that tests, docs, and support stay focused.
5. As a maintainer, I want deterministic contract tests for Frankfurter response mapping, so that provider-shape changes are caught.
6. As a QA tester, I want TSM covered in the MCP QA path, so that the known failure case cannot regress silently.

## Functional Requirements

1. `valuation-service` must fetch currency rates from Frankfurter.
2. The default base URL must be `https://api.frankfurter.dev/v2`.
3. `CURRENCY_API_KEY` must not be required by:
   - `.env.example`
   - `docker-compose.local.yml`
   - `stockvaluation_agent_native.service_control.REQUIRED_ENV_KEYS`
   - `valuation-service` runtime property validation
   - README/setup docs
4. `CurrencyRateService` must parse Frankfurter v2 responses into a USD-base internal map, preserving the current `convertCurrency(from, to, price)` call contract.
5. `CurrencyRateService` must include `USD = 1.0` in loaded rates.
6. If the full latest-rates endpoint is used, the service must support at least:
   - USD
   - EUR
   - TWD
   - INR
   - SEK
   - GBP
   - JPY
   - CNY
   - HKD
7. If the provider response is missing a needed currency, conversion must fail explicitly with a stable message.
8. Startup must not fail only because Frankfurter is temporarily unreachable; the first conversion needing a missing rate may fail with `currency_conversion_failed`.
9. MCP `stockvaluation.value_ticker` must preserve structured failure output for currency failures.
10. `stockvaluation.explain_failure` must classify currency provider failures as `currency_conversion_failed`.
11. Skills must continue instructing the agent not to manually convert currency or invent valuation numbers.

## Proposed Design

### Configuration

Use a single provider setting:

```properties
currency.provider.base-url=${CURRENCY_PROVIDER_BASE_URL:https://api.frankfurter.dev/v2}
```

Preferred implementation shape:

- Rename or replace `CurrencyApiProperties` with `CurrencyProviderProperties`.
- Keep backward-compatible property names only if needed for a short migration, but do not document them as v1 setup.
- Remove `currency.api.key`.

Expected `.env.example` values:

```text
CURRENCY_PROVIDER_BASE_URL=https://api.frankfurter.dev/v2
```

No currency key appears in `.env.example`.

### Fetch Strategy

Preferred primary request:

```text
GET {baseUrl}/rates?base=USD
```

Expected v2 shape:

```json
[
  {
    "date": "2026-05-22",
    "base": "USD",
    "quote": "TWD",
    "rate": 31.515
  }
]
```

Mapping:

```text
exchangeRates["USD"] = 1.0
exchangeRates[row.quote] = row.rate
```

Conversion stays:

```text
amountInTarget = amount * targetRate / sourceRate
```

For `USD -> TWD`, `sourceRate = 1.0`, `targetRate = 31.515`.

For `TWD -> USD`, `sourceRate = 31.515`, `targetRate = 1.0`.

### Fallback Behavior

There should be no second provider.

Allowed fallback:

- Keep previously loaded in-memory rates when a scheduled refresh fails.
- If no usable rates exist and conversion is requested, throw an explicit currency conversion exception.

Not allowed:

- Falling back to a paid provider.
- Falling back to an API key.
- Inventing hardcoded rates.
- Silently treating unmatched currencies as equal.

## TDD Implementation Plan

### Phase 1: Contract Tests First

Add or update valuation-service tests before implementation:

1. `CurrencyRateServiceTest.fetchExchangeRatesLoadsFrankfurterRates`
   - Mock `GET https://api.frankfurter.dev/v2/rates?base=USD`.
   - Return array rows for `TWD`, `EUR`, `INR`, and `SEK`.
   - Assert `isReady()`.
   - Assert `convertCurrency("USD", "TWD", 10.0) == 315.15`.
   - Assert `convertCurrency("TWD", "USD", 315.15) == 10.0`.

2. `CurrencyRateServiceTest.fetchExchangeRatesKeepsPreviousRatesWhenRefreshFails`
   - Load a valid Frankfurter response.
   - Make the next fetch fail.
   - Assert old rates still work.

3. `CurrencyRateServiceTest.convertCurrencyThrowsWhenFrankfurterMissingCurrency`
   - Load only `USD` and `EUR`.
   - Convert `USD -> TWD`.
   - Assert explicit exception message mentions missing currency.

4. `RequiredRuntimePropertiesValidatorTest.doesNotRequireCurrencyApiKey`
   - Do not set `currency.api.key`.
   - Set Frankfurter base URL.
   - Assert no throw.

5. Agent-native tests:
   - `check-env` must not require `CURRENCY_API_KEY`.
   - `.env.example` must not contain `CURRENCY_API_KEY`.
   - MCP failure classification still recognizes currency conversion failures.

Expected first run: red.

### Phase 2: Implement Frankfurter Provider

Smallest implementation:

1. Replace `CurrencyApiProperties` with Frankfurter-oriented config.
2. Change `CurrencyRateService.fetchExchangeRates()` to call Frankfurter only.
3. Parse v2 list responses.
4. Keep `convertCurrency` contract unchanged.
5. Remove key checks and key query parameters.
6. Keep secure XML parser code only if ECB support remains in unused history; preferred v1 implementation removes ECB fallback from active path.

Expected second run: green for focused tests.

### Phase 3: Env And Docker Cleanup

Update:

- `.env.example`
- `docker-compose.local.yml`
- `valuation-service/src/main/resources/application.properties`
- `RequiredRuntimePropertiesValidator`
- `stockvaluation_agent_native/service_control.py`
- README
- skill troubleshooting reference if it mentions `CURRENCY_API_KEY`

Expected tests:

```bash
python3.11 -m pytest tests/agent_native -q
cd valuation-service && mvn -B -ntp test
```

### Phase 4: MCP And Live QA

Run through MCP only:

```bash
./scripts/local_smoke.sh --agent-native --ticker MSFT
python3.11 scripts/agent_native_qa.py --output-dir /tmp/stockvaluation-frankfurter-qa
```

Required assertions:

1. `stockvaluation.health` returns `ok: true`.
2. `stockvaluation.value_ticker` returns DCF JSON for MSFT.
3. `stockvaluation.value_ticker` returns DCF JSON for TSM if Frankfurter returns `USD/TWD`.
4. If TSM still fails, failure must be categorized and explained as either:
   - `currency_conversion_failed`, if Frankfurter did not return the required pair or service failed.
   - another explicit non-currency data failure only if conversion succeeded and the later valuation path failed.
5. No report draft contains:
   - buy
   - sell
   - hold
   - target price
   - should invest
   - recommendation

## Full QA Plan

### Unit And Contract

```bash
python3.11 -m pytest tests/agent_native -q
cd valuation-service && mvn -B -ntp test
```

Must cover:

- Frankfurter parse contract.
- Missing provider response.
- Missing currency.
- No key in env requirements.
- Secret safety.
- MCP currency failure shape.

### Installer And Runtime

```bash
./scripts/test_install.sh
python3.11 -m stockvaluation_agent_native.cli check-env
python3.11 -m stockvaluation_agent_native.cli service start
python3.11 -m stockvaluation_agent_native.cli service status
```

Expected:

- No `CURRENCY_API_KEY` requirement.
- Service start manages only `postgres`, `yfinance`, `valuation-service`.
- Status shows only agent-native services.

### Smoke

```bash
./scripts/local_smoke.sh --agent-native --ticker MSFT
./scripts/local_smoke.sh --agent-native --ticker TSM
```

Expected:

- MSFT succeeds.
- TSM succeeds if Yahoo Finance and valuation inputs are available and Frankfurter returns TWD.
- If TSM fails, the failure is explicit and not due to missing local API key.

### 20 Ticker QA Set

Run through MCP/Codex-style stdio path only:

```text
MSFT, AAPL, GOOGL, AMZN, NVDA, META, TSLA, UBER, KO, PG, JNJ, LLY, GILD, ASML, SAP.DE, INFY, BABA, TSM, RIVN, SNAP
```

Use:

```bash
python3.11 scripts/agent_native_qa.py --output-dir /tmp/stockvaluation-frankfurter-qa
```

Expected:

- No UI.
- No BullBearGPT.
- No `sv value`.
- No native runtime path.
- TSM is no longer blocked by missing `CURRENCY_API_KEY`.
- Any remaining failures are categorized and explainable.

### Scenario/Recalculation QA Set

Run:

```text
MSFT, NVDA, KO, ASML, RIVN
```

Expected:

- `stockvaluation.recalculate` returns structured JSON.
- `assumptions.requested` is preserved.
- `assumptions.mapped` includes only governed valuation-service fields.
- `assumptions.unsupported` is empty for supported scenario inputs.
- `assumptions.effective` is returned from valuation-service output.

### Release

```bash
./scripts/local_release_check.sh
```

If drift fails:

1. Determine whether it is code regression, live-data drift, or baseline drift.
2. Do not update baseline unless:
   - unit tests are green,
   - smoke is green,
   - QA confirms Frankfurter conversion works,
   - drift is explained by live market/currency data or intended provider migration.

## Acceptance Criteria

1. `CURRENCY_API_KEY` is absent from required setup.
2. `check-env` does not require or print `CURRENCY_API_KEY`.
3. `docker-compose.local.yml` starts `valuation-service` without `CURRENCY_API_KEY`.
4. Frankfurter is the only documented currency source.
5. `CurrencyRateService` loads Frankfurter rates and converts `USD <-> TWD`.
6. TSM is validated through MCP after the change.
7. Missing currency data produces explicit `currency_conversion_failed`.
8. MSFT still returns DCF JSON through MCP.
9. Scenario recalculation still works for the five-ticker scenario set.
10. Reports generated from MCP JSON pass no-advice scan.
11. All required verification commands pass or report an exact external blocker.

## Files Expected To Change

- `.env.example`
- `README.md`
- `docker-compose.local.yml`
- `stockvaluation_agent_native/service_control.py`
- `stockvaluation_agent_native/mcp_tools.py`
- `stockvaluation_agent_native/skills/stockvaluation-io/references/troubleshooting.md`
- `valuation-service/src/main/resources/application.properties`
- `valuation-service/src/main/java/io/stockvaluation/config/CurrencyApiProperties.java`
- `valuation-service/src/main/java/io/stockvaluation/config/RequiredRuntimePropertiesValidator.java`
- `valuation-service/src/main/java/io/stockvaluation/service/CurrencyRateService.java`
- `valuation-service/src/test/java/io/stockvaluation/config/RequiredRuntimePropertiesValidatorTest.java`
- `valuation-service/src/test/java/io/stockvaluation/service/CurrencyRateServiceTest.java`
- `tests/agent_native/test_installer_cli.py`
- `tests/agent_native/test_policy_and_security.py`
- `tests/agent_native/test_mcp_contracts.py`

## Risks

1. Frankfurter is an external live-data dependency; the public service may be temporarily unavailable.
2. Some Yahoo Finance tickers may still return inconsistent or missing currency metadata.
3. Provider migration can alter current valuations for international tickers because conversion rates may differ from the previous source.
4. Release drift may fail after the provider switch due expected rate-source differences.

## Mitigations

1. Keep last successfully loaded rates in memory during scheduled refresh failures.
2. Make currency failure messages explicit and structured.
3. Add TSM to live MCP QA.
4. Treat drift after migration as expected baseline drift only after tests and QA prove conversion correctness.
5. Keep the provider base URL configurable for self-hosted Frankfurter later, without adding another provider.
