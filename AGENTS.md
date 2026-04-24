# StockValuation.io Agent Guide

## Project Snapshot

- StockValuation.io is a local-first multi-service valuation workspace. Keep explanations plain, grounded, and consistent with the product's educational-use framing. Do not present output as financial advice.
- The canonical local runtime is `docker-compose.local.yml`. Treat it, `README.md`, and `.env.example` as the source of truth for service names, ports, and required environment variables.
- Match CI/runtime toolchains when reproducing or validating work: Node `22` for `frontend/`, Java `21` for `valuation-service/`, and Python `3.11` for `valuation-agent/`, `bullbeargpt/`, and `yfinance/`.

## Service Map

- `frontend/`: Angular 19 UI served on `http://localhost:4200`.
- `valuation-service/`: Spring Boot valuation engine on `http://localhost:8081`.
- `valuation-agent/`: Flask orchestration and research API on `http://localhost:5001`.
- `bullbeargpt/`: Flask notebook/chat API on `http://localhost:5002`.
- `yfinance/`: Flask market-data facade. In the main compose stack it is internal-only and is not published on a host port.
- `shared/llm_models.py`: shared Python model/provider helpers used across Python services.
- `docker/local/postgres/`: local database init and seed SQL.
- `local_data/`: runtime-generated local state. Treat it as data, not source.

## Working Boundaries

- Prefer service-scoped edits. Touch only the service that owns the behavior unless the change is explicitly cross-service.
- If you change API shapes, auth headers, shared env vars, or valuation payload fields, verify every caller. The usual blast radius is `frontend/`, `valuation-agent/`, `bullbeargpt/`, and `docker-compose.local.yml`.
- Do not edit generated or local-runtime artifacts unless the user explicitly asks. This includes `frontend/node_modules/`, `frontend/dist/`, `frontend/.angular/`, `valuation-service/target/`, `**/__pycache__/`, `.pytest_cache/`, `.etl/`, `local_data/`, `*/prompt_dump_from_container/`, and `valuation-agent/venv/`.
- Never commit or print real secrets from `.env`, service-local `.env` files, prompt dumps, or runtime-generated local data.
- Local defaults are meant for development on one machine. Do not silently loosen auth, CORS, or secret requirements in the name of convenience.
- `yfinance` health checks in the main stack run inside Docker. Do not assume `localhost:5000` exists unless the user started that service separately.

## Verification Matrix

- Use the repo's actual CI paths as the baseline. The authoritative workflow is `.github/workflows/services-tests-main.yml`.
- Frontend changes: run `cd frontend && npm ci && npm run test -- --watch=false --browsers=ChromeHeadlessNoSandbox`. Add `npm run build` when changing routing, assets, or production build behavior.
- `valuation-service/` changes: run `cd valuation-service && mvn -B -ntp test`.
- `valuation-agent/` changes: run `cd valuation-agent && pip install -r requirements-dev.txt && pytest`.
- `bullbeargpt/` changes: run `cd bullbeargpt && pip install -r requirements.txt pytest && pytest -q tests`.
- `yfinance/` changes: run `cd yfinance && pip install -r requirements.txt pytest && pytest -q tests` when tests exist.
- Cross-service or contract changes: run `./scripts/local_smoke.sh`. If valuation math, baselines, or release confidence are in scope, also run `./scripts/local_release_check.sh`.
- If a required tool or dependency is missing, say exactly what you could not run and why.

## Repo-Specific Notes

- `scripts/bootstrap_local_secrets.sh` and `.env.example` are the safe starting points for local env setup. Do not overwrite a user's existing `.env` without being asked.
- `scripts/local_smoke.sh` is the fastest end-to-end health check for the composed stack. It verifies `valuation-agent`, `yfinance`, and `valuation-service`, and it can optionally exercise full orchestration with `--full`.
- `scripts/local_release_check.sh` adds valuation drift regression on top of the smoke test. Use it when a change may affect numerical outputs or release readiness.
- The app depends heavily on Yahoo Finance data quality and only has limited historical coverage. Be cautious about "fixes" that hide missing upstream data or unsupported-company failures.

## Karpathy Guidelines

Behavioral guidelines to reduce common LLM coding mistakes, derived from Andrej Karpathy's observations on LLM coding pitfalls.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports, variables, functions, and assets that your change made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" -> "Write tests for invalid inputs, then make them pass"
- "Fix the bug" -> "Write a test that reproduces it, then make it pass"
- "Refactor X" -> "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:

```text
1. [Step] -> verify: [check]
2. [Step] -> verify: [check]
3. [Step] -> verify: [check]
```

Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

## Mistake Log

- Add one short line here only when a real repo-specific mistake happens and future agents should avoid repeating it.
