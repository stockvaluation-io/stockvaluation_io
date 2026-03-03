# V1/V2 Intrinsic Pricing Integration

**Date:** December 2, 2025  
**Status:** ✅ Implemented

## Overview

The `AutomatedDCFAnalysisController` now supports fetching both V1 and V2 intrinsic pricing results based on a feature flag. This allows for side-by-side comparison of the original and enhanced R² versions.

## Feature Flag Configuration

### Application Properties

Add to `application.properties`:

```properties
# Enable V1/V2 comparison mode
intrinsic.pricing.enable.v1.v2.comparison=${INTRINSIC_PRICING_ENABLE_V1_V2_COMPARISON:false}
```

### Environment Variable

Set via environment variable:

```bash
INTRINSIC_PRICING_ENABLE_V1_V2_COMPARISON=true
```

## Behavior

### When Feature Flag is Enabled (`true`)

- **Always fetches both V1 and V2** regardless of the `useIntrinsicPricingV2` request parameter
- V1 results stored in `valuationOutputDTO.intrinsicPricingDTO`
- V2 results stored in `valuationOutputDTO.intrinsicPricingV2DTO`
- Both results available for comparison in the frontend

### When Feature Flag is Disabled (`false`) - Default

- Uses the `useIntrinsicPricingV2` request parameter to determine which version to fetch
- If `useIntrinsicPricingV2=true`: Fetches V2 only, stores in `intrinsicPricingDTO`
- If `useIntrinsicPricingV2=false`: Fetches V1 only, stores in `intrinsicPricingDTO`
- Only one version is fetched (backward compatible)

## API Usage

### GET Endpoint

```
GET /api/v1/automated-dcf-analysis/{ticker}/story-valuation-output?addIntrinsicPricing=true&useIntrinsicPricingV2=false
```

**Parameters:**
- `addIntrinsicPricing`: Enable intrinsic pricing (default: `true`)
- `useIntrinsicPricingV2`: Use V2 endpoint (default: `false`)
  - **Note:** When feature flag is enabled, this parameter is ignored and both versions are fetched

## DTO Structure

### ValuationOutputDTO

```java
// V1 results (always populated when addIntrinsicPricing=true)
private IntrinsicPricingDTO intrinsicPricingDTO;

// V2 results (only populated when feature flag is enabled)
private IntrinsicPricingDTO intrinsicPricingV2DTO;
```

## Use Cases

### 1. Comparison Mode (Feature Flag Enabled)

**Use Case:** Compare V1 vs V2 performance side-by-side

**Configuration:**
```properties
intrinsic.pricing.enable.v1.v2.comparison=true
```

**Result:**
- Both `intrinsicPricingDTO` (V1) and `intrinsicPricingV2DTO` (V2) are populated
- Frontend can display both versions for comparison
- Useful for A/B testing and validation

### 2. Single Version Mode (Feature Flag Disabled) - Default

**Use Case:** Use either V1 or V2 based on request parameter

**Configuration:**
```properties
intrinsic.pricing.enable.v1.v2.comparison=false
```

**Result:**
- Only `intrinsicPricingDTO` is populated
- Version depends on `useIntrinsicPricingV2` parameter
- Backward compatible with existing behavior

## Example Response

### With Feature Flag Enabled

```json
{
  "intrinsicPricingDTO": {
    "company": "Apple",
    "ticker": "AAPL",
    "peersFound": 22,
    "recommendedMultiple": "PE",
    "multiples": { ... },
    "version": "v1"
  },
  "intrinsicPricingV2DTO": {
    "company": "Apple",
    "ticker": "AAPL",
    "peersFound": 22,
    "recommendedMultiple": "PBV",
    "multiples": { ... },
    "version": "v2",
    "improvements": {
      "feature_engineering": true,
      "advanced_models": true,
      "cross_validation": true,
      "feature_selection": true
    }
  }
}
```

### With Feature Flag Disabled

```json
{
  "intrinsicPricingDTO": {
    "company": "Apple",
    "ticker": "AAPL",
    "peersFound": 22,
    "recommendedMultiple": "PE",
    "multiples": { ... }
  },
  "intrinsicPricingV2DTO": null
}
```

## Performance Considerations

- **Feature Flag Enabled:** Makes 2 API calls (V1 + V2), ~4-6 minutes total
- **Feature Flag Disabled:** Makes 1 API call (V1 or V2), ~2-3 minutes

## Logging

When feature flag is enabled, logs show:
```
V1/V2 comparison enabled - fetching both versions for AAPL
Intrinsic pricing V1 added for AAPL: 22 peers found, recommended multiple: PE
Intrinsic pricing V2 added for AAPL: 22 peers found, recommended multiple: PBV
```

## Migration Path

1. **Phase 1 (Current):** Feature flag disabled by default, V1/V2 selectable via parameter
2. **Phase 2:** Enable feature flag for testing/validation
3. **Phase 3:** Analyze comparison results
4. **Phase 4:** Make V2 default if validated
5. **Phase 5:** Remove feature flag and V1 endpoint (optional)

## Frontend Integration

The frontend can check for both DTOs:

```typescript
if (results.intrinsicPricingV2DTO) {
  // Comparison mode - show both V1 and V2
  displayComparison(results.intrinsicPricingDTO, results.intrinsicPricingV2DTO);
} else {
  // Single version mode - show only available version
  displayIntrinsicPricing(results.intrinsicPricingDTO);
}
```

