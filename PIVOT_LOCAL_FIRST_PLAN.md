# Local-First Community Pivot Plan (No Supabase)

## Decision (Locked)

We are pivoting to a local-first product:

- No hosted account system
- No billing / credits
- No Supabase dependency in the local MVP runtime path
- Users run it locally and bring their own API keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `TAVILY_API_KEY`, etc.)
- All persistence is local (machine-only)

This is the right move for the community product.

Important clarification:

- `No Supabase` does **not** mean `no database`
- We can still use local Postgres for the Java DCF engine's reference data tables
- The thing we are removing is hosted auth/billing/user-data infrastructure

## Cofounder Pushback (Important)

I agree with dropping Supabase, but I do **not** agree with trying to "localize everything" from the current SaaS stack.

The repo is already overbuilt for the new goal. If we port all of it, we will waste months.

Non-negotiables:

1. Do not keep `account-service` in any local MVP path.
2. Do not keep credit gates in the hot valuation endpoints.
3. Do not treat the `bullbeargpt` mock DCF engine as production valuation math.
4. Do not ship community/open-source with hardcoded secrets still in the repo.
5. Do not rebuild Supabase locally feature-for-feature.

## Status Snapshot (Current as of 2026-03-02)

Completed (implemented and re-verified locally):

- `account-service/` has been deleted from the repo
- root/prod compose files no longer wire `account-service`
- `valuation-agent` hot valuation endpoints no longer require credit/auth decorators in runtime path
- `valuation-agent` valuation persistence is local-only (`SQLite + JSON blobs`)
- `valuation-agent` audit persistence is local-only (JSON files)
- `valuation-agent` active runtime cache is local-only (`SQLite` via `data/memory/cache/local_cache.py`)
- `valuation-agent` Supabase cache module has been removed from active runtime (`supabase_cache.py` deleted)
- `valuation-agent` billing/Flexprice code has been deleted (middleware + tools + billing tests)
- `valuation-agent/routes/billing_routes.py` has been removed
- `valuation-agent` no longer depends on `supabase` or `flexprice` in its requirements files
- `docker-compose.local.yml` now provides one local stack for manual testing:
  - `postgres` (Java DCF reference data)
  - `redis`
  - `yfinance` (internal-only, no host `5000` publish)
  - `valuation-service`
  - `valuation-agent`
  - `frontend`
- local Postgres seeding in local compose includes `pgcrypto` init + reference-only seed SQL (`stockvaluation_reference_only.sql`)
- local run/test artifacts exist:
  - `env/local.env.example`
  - `LOCAL_RUNBOOK.md`
  - `scripts/local_smoke.sh`
- local smoke test passes on the current stack (`valuation-agent`, `valuation-service`, `yfinance`)
- `valuation-service` local Docker build path was repaired (compile blockers fixed)
- frontend local DCF path is unblocked:
  - valuation output route no longer requires login
  - dev frontend points directly to local Java backend (`:8081`)
  - local user ID fallback is provided without forced login redirect
- frontend DCF path auth is now local-first:
  - `AuthService` no longer contains hosted Supabase auth client logic
  - frontend env files use `authMode: 'local'`
  - frontend `@supabase/supabase-js` dependency removed
- `bullbeargpt` frontend integration is behind a feature flag (`features.legacyBullbeargpt`, default `false`)
  - legacy routes (`/dashboard`, `/notebook`, `/login` callback path, credits page) are disabled in the default local build
  - dashboard/notebook/chat entry points are hidden in the default DCF UI
- frontend home/search routing is now local-first and search-first:
  - `/` redirects to `/automated-dcf-analysis`
  - `/automated-dcf-analysis` shows the stock search page by default
  - valuation results remain at `/automated-dcf-analysis/:symbol/valuation`
- legacy public-shell links are trimmed in active UI surfaces:
  - Blog removed from active nav/footer links
  - FAQ removed from active nav/footer links
  - Privacy Policy removed from active nav/footer links
  - "Follow us" social block is commented out in the active landing footer
- contact/support destination is standardized to `stockvaluation.io@gmail.com` in active frontend surfaces and contact-email fallbacks
- local secret hygiene pass completed for working env files:
  - root `.env` and `yfinance/.env` sanitized (no live provider keys)
  - `.env.example` added as safe template for local setup
- DDM/legacy rationale fields removed from active valuation API contract and frontend consumption:
  - no `dcf.ddmResultDTO`
  - no `dcf.adjustmentRationales`
- news citations are now first-class in the local flow:
  - `valuation-agent` returns `news_sources`
  - frontend renders a bottom "News Sources" section with title + URL + source/category
- Google Ads integration removed from local product surface:
  - ad unit component/service deleted
  - AdSense env/config/docs removed from frontend source tree
- segment handoff is wired into recalculation path:
  - mapped segments are passed to Java recalc
  - by-sector financial series are present when segments exist (validated in smoke run)
- reference DB seed split is working:
  - full app dump -> reference-only seed extractor exists
  - local compose uses the reference-only seed
- Damodaran ETL work is in place as **private/local tooling only** (`.etl/`, gitignored)
- 2026 workbook partial overlay (workbook-covered tables) has been generated and line-by-line DB-audited
- valuation-service endpoint surface is now unified to:
  - `POST /api/v1/automated-dcf-analysis/{ticker}/valuation`
  - `GET /api/v1/automated-dcf-analysis/{ticker}/valuation`
  - legacy backend routes for `valuation-output` / `story-valuation-output` are no longer used in the active path
- valuation-agent Java client path is standardized to `/{ticker}/valuation` for baseline + recalc calls
- frontend canonical route is now `/automated-dcf-analysis/:symbol/valuation`
  - legacy frontend links for `/valuation-output` and `/story-valuation-output` now redirect to `/valuation`
- local CORS/auth behavior is aligned for no-login DCF flow:
  - `OPTIONS /**` permitted
  - GET/POST valuation route permitted
  - `/actuator/health` permitted for local health checks
- `valuation-service` local health contract is stable (`spring-boot-starter-actuator` + health endpoint)
- `CacheConfig` + Flask caching wiring have been removed from `valuation-agent` runtime path
  - cache env/dependency remnants removed (`Flask-Caching`, `CACHE_TYPE`)
- `SwaggerConfig` is not part of active valuation-service runtime path
- valuation-service config consolidation is in place:
  - single `application.properties`
  - local DB credentials injected via env (`DATASOURCE_*`)
  - provider naming uses `provider.yfinance.base-url` (not `yahoo.base.url`)
- provider abstraction now uses provider-agnostic extraction helpers for balance-sheet fields (including equity/debt/cash/share fallback keys), instead of Yahoo-only hardcoding in service logic
- FCFF-only runtime model selection is enforced and resolved at valuation Step 2, then propagated to response metadata
  - no runtime DIVIDENDS fallback behavior in active path
- override compatibility is stable for valuation-agent recalc payloads:
  - `initialOperatingMargin` alias support
  - `wacc` alias support
  - margin override now propagates to target margin when explicit target is absent
- segment data pass-through is validated in live requests:
  - agent payload segments are sent to Java recalc
  - by-sector arrays/maps are populated in `financialDTO` (revenues, margins, cost of capital)
- integration verification completed on current local stack:
  - `docker compose -f docker-compose.local.yml up -d --build` succeeds
  - `pytest -q valuation-agent/tests` passes (`31 passed`)

Important local setup lessons (now part of the plan):

- Do not expose every internal container port to host by default
- Do not require legacy `cache-proxy` path for local DCF MVP
- Frontend local dev should use Node LTS (`22`), not bleeding-edge Node versions

Still pending / next:

- remove remaining hosted-branding/telemetry remnants in frontend shell (for local community default):
  - Google Analytics tag in `frontend/src/index.dev.html`
  - stockvaluation.io canonical/SEO hardcoding in local HTML/env content
- decide final cleanup scope for inactive DDM code artifacts in Java (classes/tests remain, though runtime output path is removed)
- decide and execute final response-contract simplification for legacy `story` payload (if we never render it)
- frontend dashboard/notebook surfaces still carry legacy auth assumptions in code, but are disabled by feature flag
- `bullbeargpt` is still heavily Supabase/account-service shaped (quarantine or local rewrite plan needed)
- finish remaining Phase 0 security rotation for keys exposed historically in git history
- local community release polish (no-login/no-billing wording sweep, docs, clean setup path)
- final data patch phase (audited 2026 refresh) is still deferred by design

## What The Codebase Already Has (Useful)

These are real assets we should preserve:

- `valuation-agent` already has a strong ticker-first orchestration flow in `/api-s/valuate`:
  - baseline Java DCF
  - agent judgement
  - Java recalc
  - narrative generation
  - persistence hooks
- `valuation-service` is the deterministic DCF engine (keep as math authority)
- `yfinance` service works as local market/fundamental data helper
- `bullbeargpt` has useful notebook/chat UX pieces
- `bullbeargpt` already has local-friendly in-memory session storage fallback
- `valuation-agent` valuation + audit persistence can now run fully local (`SQLite + JSON`)
- Industry/sector context data exists already in `valuation-agent/tools/tool_definitions.py`
- Damodaran-style prompting/workflows already exist in `valuation-agent/agents/prompts/*`

## What Is Clearly SaaS/Legacy (Cut or De-Prioritize)

- `account-service/` (auth + billing)
- hosted billing/credit concepts in old docs/config/comments (runtime billing code is already removed from `valuation-agent`)
- hosted frontend login/dashboard/credits flows
- Supabase-backed thesis/history/dashboard features (phase later, local-only replacement)
- `pgadmin` and SaaS root compose wiring (`docker-compose.yml`) as the default runtime path

## Current Architecture Reality (Why We Must Still Be Surgical)

Completed decoupling (local DCF path):

- `valuation-agent/app.py` no longer wraps `/api-s/analyze` and `/api-s/valuate` with credit middleware
- `valuation-agent/app.py` no longer protects `/api-s/valuation/<id>` with auth middleware in local runtime path
- local user identity is injected directly in endpoint flow (`X-Local-User` / `X-User-ID` / fallback)
- valuation and audit persistence no longer require Supabase
- `valuation-agent` active cache no longer requires Supabase (local SQLite cache module now used)
- hosted billing middleware/tooling has been removed from `valuation-agent`

Remaining SaaS coupling (outside the current local DCF hot path):

- frontend auth/dashboard flows still depend on Supabase assumptions (`@supabase/supabase-js`, auth service, dashboard routes)
- `bullbeargpt` notebook/dashboard/thesis paths still depend on Supabase/account-service assumptions
- root `docker-compose.yml` is still SaaS-shaped and should not be the default local runtime
- legacy docs still contain hosted/Supabase references in places (cleanup pass, not runtime blocker)

Also:

- `bullbeargpt/services/dcf_engine.py` is explicitly a **mock** DCF engine (prototype path)

## Product Strategy (Local-First, Transparent, Deterministic)

### Product Promise

"A local valuation workstation for Damodaran-style analysis. Bring your own keys. Deterministic DCF math. Transparent assumptions and audit trail."

### Core Principle

- LLMs assist with research, summarization, narrative, and assumption critique
- Deterministic code calculates valuation outputs

For us today, that means:

- Keep Java `valuation-service` as the math engine
- Use `valuation-agent` to orchestrate evidence + judgement + narrative

## Target Local Architecture (MVP)

### Keep (MVP Core)

- `valuation-service` (Java DCF)
- `postgres` (local reference DB for Java DCF tables)
- `yfinance` (Python data service)
- `valuation-agent` (orchestration + local persistence)
- `redis` (keep for current services unless we deliberately remove usage)
- Minimal UI/CLI runner (can be a thin local web UI; `bullbeargpt` integration is phase 2)

### Remove from MVP runtime

- `account-service`
- `pgadmin`
- billing webhooks / credit ledger

### Optional (Phase 2)

- `bullbeargpt` notebook/chat UI backed by local storage only

## Local Persistence Plan (No Supabase)

We need to separate two kinds of persistence:

1. Reference valuation data for Java DCF (Damodaran-derived tables, mappings)
2. User-generated local artifacts (valuations, notebook sessions, audits, exports)

Do not use raw JSON files for everything. That will become a maintenance problem quickly.

Recommended local storage design:

- `Postgres` (local) for Java DCF reference data
- `SQLite` for app metadata/index/querying (user-side local product state)
- JSON files for large blobs/artifacts (valuation payloads, audit logs, prompt dumps)

### Local data layout

```text
local_data/
  app.db
  valuations/
    <valuation_id>.json
  audit_runs/
    <run_id>.json
  notebook/
    sessions/
    exports/
  cache/
    tavily/
    news/
```

### SQLite tables (minimal)

- `valuations`
  - `id`
  - `ticker`
  - `company_name`
  - `valuation_date`
  - `fair_value`
  - `current_price`
  - `upside_percentage`
  - `blob_path`
  - `created_at`
- `valuation_runs`
  - `id`
  - `ticker`
  - `company_name`
  - `status`
  - `started_at`
  - `completed_at`
  - `blob_path`
- `valuation_run_steps` (optional in phase 1; can start file-only)
- `notebook_sessions` (phase 2)
- `notebook_cells` (phase 2)
- `theses` (phase 2)

## Phased Execution Plan

### Phase 0: Repo Hygiene / Security (In Progress - substantial cleanup done, still blocker before community release)

Goal:
- Make the repo safe to share

Actions:
- Remove hardcoded secrets from compose/Dockerfiles/frontend env files (partially done; continue targeted scan)
- Rotate exposed keys (still required; git history exposure matters even after file cleanup)
- Replace with local examples (`env/local.env.example`) (done)
- Add `.gitignore` coverage for local artifacts (`local_data/`, prompt dumps, caches, `.etl/`) (done)

Pushback:
- We should not delay this. Publishing with embedded keys is a hard no.

Verify:
- `rg` finds no live API keys/secrets in tracked config files we ship in local flow
- local app still boots with env vars
- keys exposed historically have been rotated (manual operational step)

### Phase 1: Make `valuation-agent` run locally without auth/billing (Completed)

Goal:
- `POST /api-s/valuate` works with no `Authorization` header and no `account-service`

Actions:
- Remove `require_credits` decorators from `/api-s/analyze` and `/api-s/valuate` runtime path
- Remove `require_auth` from `/api-s/valuation/<valuation_id>` runtime path
- Keep endpoint logic single-path and inject local identity directly in endpoint code (`X-Local-User` / `X-User-ID` / default local user)
- Delete billing/credit middleware and billing route/tooling from `valuation-agent` (done; no local-mode bypass shim approach)

Important:
- Do not fork endpoint logic into separate local/hosted branches if avoidable
- Prefer deleting middleware from the local path over adding `LOCAL_MODE` bypasses

Verify:
- `curl` to `/api-s/valuate` returns 200 locally with only ticker payload
- `curl` to `/api-s/valuation/<id>` returns record locally (after a valuation is run)
- `account-service` is not running

### Phase 2: Replace Supabase persistence with local persistence in `valuation-agent` (Completed for valuation + audit)

Goal:
- Valuation results and audit runs save/read locally with no Supabase

Actions:
- Replace Supabase-backed/dual-mode persistence with local-only implementations for:
  - `valuation_persistence` (`SQLite + JSON blobs`)
  - `valuation_audit_persistence` (JSON run files)
- Keep current response shape unchanged where possible
- Save canonical valuation payload (`java_valuation_output`, agent outputs, narrative, applied overrides, metadata)
- Add simple local retrieval by `valuation_id`

Design rule:
- Preserve API contract first, optimize storage format second

Verify:
- Run same ticker twice and confirm:
  - valuation records are saved locally
  - retrieval by returned `valuation_id` works
- App boots with no `SUPABASE_URL` / `SUPABASE_KEY`

### Phase 3: Create a clean local runtime compose profile (Completed - operational local E2E stack)

Goal:
- One-command local startup for community users

Actions:
- Add `docker-compose.local.yml` (done; keep root compose for now)
- Include:
  - `postgres` (reference DB for Java DCF only)
  - `valuation-service`
  - `valuation-agent`
  - `yfinance`
  - `redis` (currently required by selected local service path)
  - `frontend` (manual local UI testing)
- Remove `account-service`, `pgadmin` (done in local compose runtime path)
- Seed local Postgres on first boot with:
  - `pgcrypto` extension init
  - reference-only seed SQL (`stockvaluation_reference_only.sql`)
- Do not expose unnecessary internal ports on host (example: `yfinance` host `5000` mapping removed)
- Add `env/local.env.example` with minimal keys:
  - at least one LLM provider key
  - optional Tavily key
- Add local runbook + smoke script for repeatable verification (done)

Verify:
- Fresh clone + env file setup + `docker compose -f docker-compose.local.yml up`
- `scripts/local_smoke.sh` passes
- No host port conflict on `5000` because `yfinance` is internal-only
- Frontend is reachable at `http://localhost:4200` for manual ticker flow checks

### Phase 3.5: Stabilize Java DCF Reference Data as a Versioned Local Seed (Phase 1 complete: reference-only split)

Goal:
- Treat Damodaran-derived DB data as a versioned local reference dataset, not an opaque dump
- Keep the current (older) reference data usable for local MVP stabilization while we defer the audited 2026 data patch to the end

Current reality:
- `stockvaluation_db_dump.sql` contains the needed reference tables, but also unrelated app tables
- It is stale (last year's data)
- It is not the right long-term source of truth for annual updates
- `region_equity` exists in the seed but is not on the current Java DCF calculation path (admin import/read only)

Actions:
- Split "reference seed" from "app dump" (done - extractor + generated seed)
- Keep only Java DCF reference tables in a dedicated seed artifact (example names):
  - `industry_averages_us`
  - `industry_averages_global`
  - `input_stat_distribution`
  - `sector_mapping`
  - `risk_free_rate`
  - `cost_of_capital`
  - `large_bond_spread`
  - `small_bond_spread`
  - `bond_rating`
  - `failure_rate`
  - `country_equity`
  - `region_equity`
  - `rdconvertor`
- Add a version manifest:
  - source file name (`fcffsimpleginzu2026.xls`)
  - source file hash
  - extraction date
  - mapping version
  - notes
  - (deferred until Phase 3.6 final refresh)

Verify:
- Java valuation-service boots and computes valuations from the reference-only seed (done on local stack)
- Fresh-volume local Postgres init works with the reference-only seed (done)
- Reference-only seed extractor is reproducible from the current full dump (done)
- Full annual recreation from source + mapping pipeline remains Phase 3.6 (deferred)

### Phase 3.6: Damodaran Annual Refresh / Data Patch Pipeline (`fcffsimpleginzu*.xls`) (Deferred - Last; private ETL scaffold exists)

Goal:
- Rebuild the local Postgres reference dataset each year from Damodaran's updated workbook, with reproducible mapping to Yahoo sector/industry taxonomy
- Do this only after the local-first runtime/UI flow is stable and usable (current DCF can run on older reference data for now)

Design (recommended):
- Do not hand-edit SQL dumps
- Do not make the Excel file itself the runtime dependency
- Build a repeatable ETL pipeline

Pipeline stages:
- `source`:
  - store raw workbook under a versioned path (unmodified)
- `extract`:
  - parse relevant sheets from `fcffsimpleginzu2026.xls` into canonical CSV/JSON
- `transform`:
  - map Damodaran industry names to Java schema + Yahoo taxonomy
  - preserve `sector_mapping` as an explicit curated mapping layer
- `validate`:
  - row counts, duplicate keys, null checks, numeric range checks
  - "unmapped industry" diff report
- `load`:
  - load into local Postgres staging tables
  - promote/replace target reference tables transactionally
- `seed export`:
  - generate a fresh reference-only SQL seed file for local installs

Private tooling boundary (locked):
- ETL code and ETL artifacts stay under `.etl/` and are gitignored
- Open-source repo should contain the resulting public seed artifacts and documentation, not the private workbook ETL tooling

Separate-source sub-pipelines (not from `fcffsimpleginzu*.xls`):
- `risk_free_rate`:
  - source sovereign bond yields separately (for example, a bonds API under user-provided credentials if licensing/terms allow local BYOK usage)
  - maintain explicit currency-code <-> bond symbol/country mapping
  - compute `riskfree_rate` deterministically from `govt_bond_rate` and `default_spread`
  - keep an explicit rounding rule (current seed has some `0.01` differences from raw subtraction)
- `sector_mapping`:
  - treat as curated product logic (Damodaran names -> Yahoo taxonomy), reviewed manually each year
- `region_equity`:
  - currently not used in active DCF path; defer refresh or remove later in cleanup after confirming no downstream dependency

Notes from current code:
- Java already has upload endpoints for multiple reference datasets (`processIndustryUS`, `processIndustryGlo`, `processSectorMapping`, `processRiskFree`, `processCostOfCapital`, etc.)
- Those loaders expect JSON arrays (multipart upload), not Damodaran Excel directly
- That means our ETL should output table-specific JSON/CSV and then either:
  - call Java import endpoints, or
  - load directly into Postgres with SQL scripts
- Workbook ETL coverage is currently partial (workbook-covered tables only); `risk_free_rate` and curated `sector_mapping` remain the critical completion items
- We already generated and audited a partial 2026 overlay for workbook-covered tables (private/local proof of pipeline integrity)

Pushback:
- The mapping from Damodaran names to Yahoo names is product IP / core logic. We should treat it as a first-class maintained asset, not an incidental SQL table.
- This is a garbage-in/garbage-out risk area. Every DB patch must be auditable line-by-line, not just "ETL completed" logs.

Verify:
- Run a scripted 2026 refresh and generate:
  - reference seed SQL
  - mapping diff report
  - validation report
- Generate and keep a DB patch audit report proving:
  - overlay SQL rows == canonical extracted rows
  - live DB rows after patch == overlay SQL rows
- Run smoke valuations on known tickers and compare outputs vs prior seed for sanity

### Phase 4: Local UI Strategy (Do not overcommit)

Goal:
- Provide usable local UX without dragging full SaaS frontend complexity

Recommendation:
- MVP UI should be thin and local-focused
- Do not reuse the full Angular SaaS frontend as-is for MVP (too much auth/dashboard/billing coupling)

Pragmatic update (already done as a transitional step):

- We patched the existing Angular app enough to test local DCF flow now:
  - compose includes frontend service for one-command local manual E2E
  - valuation output route is unguarded
  - dev frontend points to local Java backend (`:8081`)
  - local user fallback avoids forced login redirects in dev
  - DDM/adjustment-rationale UI remnants are removed from active DCF rendering
  - News Sources citations are visible in results
  - Google Ads components/integration are removed from local runtime path
- This is a test-enablement bridge, not the final product UX direction

Local frontend setup guardrails:

- Use Node LTS (`22`) for local frontend work
- Do not require the legacy `cache-proxy` path for local DCF MVP
- Keep local DCF validation path simple before touching notebook/dashboard features

Options:
- A minimal local web UI over `valuation-agent` (fastest path)
- A small CLI first (`valuate <ticker>`) + JSON/Markdown export
- `bullbeargpt` notebook as phase 2 once local storage/auth mode is patched

Verify:
- User can run valuation, inspect assumptions, and export results without login

### Phase 5: Local notebook/chat (optional but strategic)

Goal:
- Bring back the best parts of `bullbeargpt` without hosted auth

Actions:
- Replace account-service token validation in `bullbeargpt/services/auth_service.py` with a local identity/optional-auth path (no hosted billing/auth dependency)
- Keep auth-required routes only for optional "profile" features, or relax them in the local build/runtime path
- Replace Supabase-backed notebook/thesis persistence with local storage backend
- Ensure notebook uses real valuation payloads from `valuation-agent`, not the mock DCF path unless explicitly "sandbox mode"

Critical pushback:
- If we keep the mock DCF engine path visible, users will not trust results
- Label it clearly as sandbox/demo or remove it from community build

Verify:
- Create notebook session from local valuation ID
- Continue conversation
- Save thesis locally

### Phase 6: Skills & Data Packaging (Damodaran/Industry)

Goal:
- Turn existing assets into explicit, maintainable local skills

Actions:
- Extract "skills" into a versioned local format (YAML/JSON + prompt + metadata)
  - methodology skills (Damodaran principles/checklists)
  - sector/industry context skills
  - report-writing styles
- Package curated local datasets from `db_backup/` with:
  - provenance
  - refresh script
  - version tag
  - attribution

Pushback:
- Do not blindly package third-party data without reviewing licensing/redistribution terms

Verify:
- Skills load from local filesystem
- A valuation run can list which skills/data versions were used

## Keep / Cut / Rebuild Matrix

### Keep now

- `valuation-agent` ticker-first `valuate` flow
- `valuation-service` deterministic DCF engine
- local `postgres` for Java reference tables
- `yfinance` service
- existing agent prompt system + model routing (with cleanup)
- industry/sector context assets

### Cut now (runtime)

- `account-service`
- credit/billing middleware on valuation endpoints
- billing routes/webhooks
- `pgadmin` in default local runtime
- hosted auth/user DB assumptions

### Rebuild later (local-first)

- notebook/thesis persistence backend
- local UI shell (minimal and explicit)

## Success Criteria (Verifiable)

### MVP success (technical)

1. Local stack runs without Supabase and account-service, with local Postgres for Java reference data.
2. User can submit ticker and get valuation result + narrative.
3. Output includes deterministic DCF source (`valuation-service`) and applied overrides.
4. Valuation can be reopened locally by `valuation_id`.
5. All user/session artifacts are stored on local disk.

### MVP success (product)

1. Setup requires only one LLM key (Tavily optional).
2. No login screen appears anywhere in default local flow.
3. No credit/billing language appears in UI/API responses.
4. Results are inspectable and reproducible.

## Immediate Implementation Order (Updated)

This is the shortest path from current state to a usable community release:

1. Keep `bullbeargpt`/legacy frontend integration disabled by default (`features.legacyBullbeargpt = false`) for community builds (locked, already done)
2. Finish remaining Phase 0 security cleanup + rotate any historically exposed keys
3. Remove remaining hosted telemetry/branding remnants from local frontend shell (GA tag + canonical/SEO hardcoding)
4. Community test/fix loop on local runtime + frontend setup (fresh-machine reproducibility)
5. Decide and execute final API-contract cleanup for inactive legacy fields (`story` payload and dormant DDM code artifacts)
6. Optional: ship a thinner local UI/CLI wrapper for valuation (if Angular SaaS shell remains too noisy)
7. Last: Phase 3.6 audited data patch / annual refresh
   - workbook-covered tables from `fcffsimpleginzu*.xls`
   - separate-source `risk_free_rate`
   - curated `sector_mapping`

## Notes From Current Code (Why this plan is grounded)

- `valuation-agent` already has the right core product behavior in `valuate_endpoint` (ticker-first orchestration)
- Java DCF integration is already isolated behind `ValuationServiceClient`
- `valuation-agent` valuation + audit persistence have already been rewritten to local-first storage
- `valuation-agent` active runtime cache has been migrated to local SQLite (`local_cache.py`); no Supabase dependency remains in that service
- `bullbeargpt` notebook routes are mostly optional-auth already, but thesis/history endpoints still require auth
- `bullbeargpt` and the frontend dashboard/auth surfaces still carry most of the remaining Supabase/account-service coupling
- frontend DCF path auth has been simplified to local-only behavior (no Supabase SDK dependency)
- frontend legacy workspace integration is now feature-flagged off by default (`legacyBullbeargpt`)
- active valuation payload path no longer emits `ddmResultDTO` or `adjustmentRationales`
- `valuation-agent` now provides explicit `news_sources` and frontend renders citations
- Google Ads runtime integration has been removed from frontend source path
- The existing Angular frontend is heavily SaaS/auth-oriented and should not define MVP scope
