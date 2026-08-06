# Local fixes (not upstream)

Updated: 2026-08-06T23:10:34+08:00

1. `valuation-service/src/main/java/io/stockvaluation/service/ValuationOutputService.java`
   - normalizeSectorKey (null map key / researched 403)
   - toPercentUnit + CoC normalization / guardrails
   - terminal growth rf cap normalize
2. `valuation-service/src/main/java/io/stockvaluation/service/ValuationWorkflowServiceImpl.java`
   - curated segment fallback (MSFT/GOOGL/AMZN)
3. `.env`: SEC_USER_AGENT set (do not commit secrets)
4. optional: `docker-compose.logging-override.yml`

Rebuild:
```bash
cd ~/Apps/stockvaluation_io
docker compose -f docker-compose.local.yml build valuation-service
docker compose -f docker-compose.local.yml up -d valuation-service
```
