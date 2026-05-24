# PRD: Agent-Native Removal and End-to-End Continuity

## Problem Statement

StockValuation.io now has a clear agent-native product direction: installable skills for Claude Code and Codex-style agents, a local deterministic valuation service, and a local MCP server that exposes valuation tools as structured JSON. The current repository still contains legacy product surfaces and runtime paths that can confuse users and future agents: Angular UI workflows, BullBearGPT report workflows, legacy orchestration assumptions, and historical install or smoke paths.

The user needs a disciplined removal plan that keeps the agent-native product intact while reducing the maintained product surface. Removal must not break the end-to-end path where a user's agent calls MCP tools, receives deterministic DCF JSON, and writes an educational valuation report from the installed skill pack. The plan must also preserve explicit failure handling for unsupported or misconfigured cases, including currency-conversion failures like TSM.

## Solution

Create a phased removal plan for legacy product surfaces while treating the agent-native workflow as the only supported product experience. Each phase removes or detaches one legacy dependency only after the agent-native path is verified end to end.

The supported product after removal is:

1. A local installer that installs or updates skills and MCP configuration.
2. A Docker-backed local service command surface that can start, stop, and report status for the valuation service stack.
3. A local MCP server exposing structured valuation tools.
4. A deterministic valuation service that owns valuation math.
5. A skill pack that instructs the user's agent to write educational reports from MCP JSON.

For v1, Docker Desktop or a compatible Docker Engine with Compose support is a hard runtime requirement. The installer must not install Java, Postgres, yfinance, or database seed data natively on the user's machine. Those dependencies run inside the Docker-managed local stack.

The removal plan must keep the following invariant: after each phase, a fresh agent-native setup can value MSFT through MCP and can produce a no-advice educational report without Angular UI, BullBearGPT, or a valuation-writing CLI.

## User Stories

1. As a Codex user, I want to install the StockValuation skill locally, so that my agent knows how to request and interpret valuation JSON.
2. As a Claude Code user, I want to install the same skill pack locally, so that the workflow is not tied to one agent vendor.
3. As an agent user, I want MCP configuration installed for my local agent, so that the agent can call StockValuation tools directly.
4. As an agent user, I want a local service command to start the required valuation runtime, so that I do not need to understand legacy services.
5. As an agent user, I want service status to tell me whether the valuation runtime is reachable, so that failures are actionable.
6. As an agent user, I want the MCP server to list all supported StockValuation tools, so that my agent can discover the contract.
7. As an agent user, I want `stockvaluation.health` to return structured status, so that my agent can verify readiness before valuation.
8. As an agent user, I want `stockvaluation.value_ticker` to return DCF JSON for MSFT, so that my agent can write an educational report from deterministic data.
9. As an agent user, I want `stockvaluation.recalculate` to return requested, mapped, unsupported, and effective assumptions separately, so that changed scenarios are transparent.
10. As an agent user, I want `stockvaluation.get_assumptions` to expose model assumptions, so that a report can explain the valuation drivers.
11. As an agent user, I want `stockvaluation.get_growth_anchor` to expose the Damodaran-style growth anchor, so that the report can explain growth context.
12. As an agent user, I want `stockvaluation.get_reference_data_status` to expose data-source status, so that the report can caveat data quality.
13. As an agent user, I want `stockvaluation.explain_failure` to summarize failures in plain language, so that my agent does not paste raw JSON into the report.
14. As an agent user, I want TSM-like failures to be categorized as currency-conversion or configuration failures when applicable, so that I know what to fix.
15. As an agent user, I want failures to avoid invented valuation numbers, so that unsupported companies are handled honestly.
16. As an agent user, I want the installed skill to require educational framing, so that generated reports are not financial advice.
17. As an agent user, I want the report to avoid recommendation language, so that the output does not imply buy, sell, hold, target-price, or investment advice.
18. As a maintainer, I want Angular UI references removed from the default product path, so that users do not think the UI is required.
19. As a maintainer, I want BullBearGPT references removed from the default product path, so that report generation stays inside the user's agent.
20. As a maintainer, I want legacy orchestration paths removed only after dependency checks, so that deterministic valuation still works.
21. As a maintainer, I want every removal phase to have acceptance checks, so that deletion does not silently break the product.
22. As a maintainer, I want CI to cover MCP contracts, installer behavior, recalculation validation, failure shapes, no-advice policy, and secret safety, so that the agent-native surface remains stable.
23. As a maintainer, I want local smoke checks to exercise MCP valuation for MSFT, so that release confidence matches the real product path.
24. As a maintainer, I want release checks to state dependency blockers exactly, so that live data or currency-provider problems are not confused with code regressions.
25. As a maintainer, I want legacy services either deleted, archived, or moved behind non-default profiles, so that the default repo shape matches the product.
26. As a maintainer, I want documentation to describe only the agent-native product path, so that future agents do not rebuild UI or CLI valuation surfaces.
27. As a maintainer, I want prompt dumps, real secrets, and runtime logs kept out of source control, so that evidence collection does not create security risk.
28. As a QA tester, I want Codex or Claude event logs to show only MCP calls and agent messages during valuation, so that no hidden UI or CLI valuation path is used.
29. As a QA tester, I want scenario tests for large-cap, international, mature, and high-variance tickers, so that recalculation behavior is not MSFT-only.
30. As a QA tester, I want report scans to catch recommendation language, so that policy regressions are visible.
31. As an agent user, I want the installer to clearly state that Docker is required for the local valuation runtime, so that I know the prerequisite before starting.
32. As an agent user, I want environment checks to detect Docker, Docker Compose, daemon reachability, required ports, and local secrets, so that setup failures are actionable.
33. As an agent user, I want service startup to fail clearly when Docker is unavailable, so that the system does not imply a native Java/Postgres fallback.
34. As a maintainer, I want Docker to remain the only v1 runtime path, so that support, tests, and documentation stay focused on one reproducible local stack.

## Implementation Decisions

1. Treat the installed skill pack, local MCP server, and deterministic valuation service as the product surface.
2. Keep valuation math inside the valuation service. MCP adapts service responses but does not compute valuation.
3. Keep report writing inside the user's agent. Skills guide the report; the CLI and local services do not write valuation reports.
4. Keep the CLI constrained to installation, MCP configuration, service lifecycle, environment checks, and uninstall.
5. Preserve the seven-tool MCP contract as the public agent-facing API.
6. Return structured JSON for every MCP tool, including failures.
7. Use stable failure categories for common recoverable or explainable failures, including health, validation, service unavailable, upstream service error, insufficient data, and currency conversion.
8. Preserve explicit no-advice policy metadata in agent-facing responses.
9. Remove legacy surfaces in phases rather than one large deletion.
10. Begin with dependency inventory before removing runtime components.
11. Detach default runtime and documentation from legacy surfaces before deleting code.
12. Remove BullBearGPT from the product path before removing shared lower-level dependencies.
13. Remove Angular UI from the product path without improving or migrating the UI.
14. Assess legacy LLM orchestration separately because the valuation service may still contain configuration references.
15. Keep optional archival references clearly marked if full deletion is deferred.
16. Make agent-native smoke checks the release confidence path.
17. Keep live-data checks separate from deterministic contract tests when possible.
18. Treat QA event logs as evidence only after secret and prompt-safety review.
19. Require Docker Desktop or compatible Docker Engine with Compose for v1 runtime startup.
20. Do not install Java, Postgres, yfinance, or seed SQL as host-native dependencies in v1.
21. Limit `sv service start` to the Docker-managed agent-native stack: Postgres, yfinance, and valuation-service.
22. Make Docker availability, Docker daemon reachability, required ports, and required local environment values part of `check-env`.
23. Document native/no-Docker runtime as a possible future product phase, not part of this release.

## Removal Phases

1. Phase 0: Inventory and safeguards.

   Identify all references to Angular UI, BullBearGPT, legacy orchestration, valuation-writing CLI paths, legacy skill packs, product docs, runtime defaults, environment variables, Docker prerequisites, and CI jobs. Classify each as agent-native product, valuation-service dependency, local development support, legacy surface, or removal candidate.

2. Phase 1: Detach runtime defaults from legacy product surfaces.

   Ensure install, service lifecycle, smoke checks, and documentation lead users through the agent-native flow only. The default startup path must support MCP valuation through Docker without presenting UI, BullBearGPT, or native Java/Postgres installation as product requirements.

3. Phase 2: Remove BullBearGPT from the product path.

   Remove BullBearGPT from default docs, runtime expectations, release gates, and install assumptions. Keep archival references only if explicitly marked as non-product history.

4. Phase 3: Remove Angular UI from the product path.

   Remove Angular UI references from default docs, runtime expectations, release gates, and install assumptions. Do not improve the UI while removing it from the product path.

5. Phase 4: Remove legacy LLM orchestration if not required.

   Verify whether legacy orchestration is required by deterministic valuation. If not required, remove it from the product path and prune LLM-provider setup that is unrelated to agent-native valuation.

6. Phase 5: Prune legacy skills, installers, and documentation.

   Remove instructions that tell agents to use UI, BullBearGPT, report APIs, or valuation-writing CLI commands. Preserve only the skill-led MCP report workflow.

7. Phase 6: Harden CI and release gates.

   Make MCP contract tests, installer tests, policy tests, service contract checks, smoke checks, and release checks the required confidence path for the product.

## End-to-End Continuity Requirement

After every removal phase, the following end-to-end checks must pass before continuing:

1. A fresh setup can install or update the StockValuation skill pack.
2. A fresh setup can install or update MCP configuration for the user's agent.
3. `check-env` clearly detects Docker availability, Docker Compose support, Docker daemon reachability, required ports, and required local environment values.
4. If Docker is missing or stopped, service startup fails with a concise actionable error and does not imply a native fallback.
5. With Docker available, the local service lifecycle starts and reports status for the valuation runtime.
6. `stockvaluation.health` returns structured readiness status.
7. MCP tool discovery includes all seven required StockValuation tools.
8. `stockvaluation.value_ticker` returns structured DCF JSON for MSFT.
9. `stockvaluation.recalculate` returns transparent requested, mapped, unsupported, and effective assumptions.
10. At least one explicit failure path returns a stable failure category and concise explanation.
11. The installed skill causes the agent to write an educational report from MCP JSON.
12. Automated report scanning finds no recommendation-language regression.
13. Agent event logs show no Angular UI, BullBearGPT, native valuation runtime, or valuation-writing CLI path.

## Testing Decisions

1. Tests should validate externally observable contracts rather than private implementation details.
2. MCP contract tests should assert tool names, schemas, structured success responses, and structured failure responses.
3. Installer tests should assert that supported commands install skills and MCP config while unsupported valuation commands are absent.
4. Service contract tests should assert the valuation service response shape required by MCP.
5. Recalculation tests should assert validation, assumption mapping, unsupported inputs, and effective assumption output.
6. Failure-shape tests should assert stable failure categories and concise human-readable explanations.
7. No-advice policy tests should assert policy metadata and scan generated report text for prohibited recommendation language.
8. Secret-safety tests should assert that installers and logs do not print real secrets or unsafe environment values.
9. Smoke tests should call the MCP server through the same path agents use.
10. Release checks should include MSFT MCP valuation and should report live-data or environment blockers exactly.
11. TSM-style currency conversion failures should remain regression-tested so the failure does not fall back to a generic service error.
12. International and scenario recalculation cases should be retained to avoid an MSFT-only product gate.
13. Docker prerequisite tests should cover missing Docker binary, stopped daemon, missing Compose support, occupied ports, and missing required environment values.
14. Tests should assert that v1 documentation and CLI behavior do not advertise or attempt a native Java/Postgres runtime.

## Out of Scope

1. Building or improving the Angular UI.
2. Using BullBearGPT as the product surface.
3. Adding `sv value` or any valuation-writing CLI command.
4. Moving valuation math into the MCP server, CLI, or skill pack.
5. Generating investment recommendations.
6. Replacing the valuation model.
7. Hiding data-quality, currency, or unsupported-company failures behind generic success output.
8. Requiring users to run legacy services for the agent-native valuation path.
9. Supporting a no-Docker native runtime in v1.
10. Installing Java, Postgres, yfinance, or seed SQL directly on the host machine.

## Further Notes

The removal program is only complete when the simplified product can still run end to end from a clean local setup with Docker available: install skills, install MCP config, validate Docker and environment prerequisites, start the Docker-backed local valuation runtime, call MCP tools, receive MSFT DCF JSON, recalculate scenarios, handle explicit failures, and produce an educational no-advice report through the user's agent.

The most important risk is accidental dependency removal. Live market data, currency conversion, and non-US ticker coverage are inherently less stable than contract tests, so the release process should distinguish code regressions from external data or configuration blockers.
