package io.stockvaluation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.stockvaluation.dto.GrowthAnchorDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service that loads and serves historical growth anchor data derived from
 * Damodaran's "Historical Growth Rate in Earnings" dataset.
 *
 * Data source: growth_skill_snapshots_combined.json (produced by ETL-4).
 *
 * Read-only service. DCF math is NOT modified. Anchor data is used for:
 * - assumption transparency panel
 * - agent prompt enrichment (growth_skill)
 * - chat explainability
 */
@Slf4j
@Service
public class GrowthAnchorService {

    private static final String SNAPSHOTS_RESOURCE = "/data/growth_skill_snapshots_combined.json";
    private static final String FEATURES_RESOURCE = "/data/historical_growth_industry_features.json";
    private static final String INDUSTRY_MAPPING_RESOURCE = "/data/industry_entity_mapping.json";

    /** Entity-keyed lookup: entity -> region -> GrowthAnchorDTO */
    private final Map<String, Map<String, GrowthAnchorDTO>> anchorsByEntity = new ConcurrentHashMap<>();

    /** All loaded DTOs. */
    private final List<GrowthAnchorDTO> allAnchors = new ArrayList<>();
    /** Yahoo normalized industry/sector -> Damodaran entity key. */
    private final Map<String, String> yahooToEntity = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        try {
            loadFeatures();
            loadIndustryMapping();
            log.info("[GrowthAnchor] Loaded {} anchor records across {} entities",
                    allAnchors.size(), anchorsByEntity.size());
        } catch (Exception e) {
            log.warn("[GrowthAnchor] Could not load growth anchor data: {}. " +
                    "Valuation engine will continue without growth anchors.", e.getMessage());
        }
    }

    /**
     * Load features from the classpath resource.
     * Falls back gracefully if file is not present.
     */
    private void loadFeatures() {
        InputStream is = getClass().getResourceAsStream(FEATURES_RESOURCE);
        if (is == null) {
            log.info("[GrowthAnchor] Features resource not found at {}. Skipping.", FEATURES_RESOURCE);
            return;
        }

        try {
            List<Map<String, Object>> records = objectMapper.readValue(is, new TypeReference<>() {
            });
            for (Map<String, Object> rec : records) {
                GrowthAnchorDTO dto = mapToDTO(rec);
                if (dto.getEntity() != null && !dto.getEntity().isBlank()) {
                    allAnchors.add(dto);
                    Map<String, GrowthAnchorDTO> regionMap = anchorsByEntity
                            .computeIfAbsent(dto.getEntity().toLowerCase(), k -> new ConcurrentHashMap<>());
                    String regionKey = dto.getRegion() != null ? dto.getRegion().toLowerCase() : "global";
                    GrowthAnchorDTO existing = regionMap.get(regionKey);
                    if (isNewer(dto, existing)) {
                        regionMap.put(regionKey, dto);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[GrowthAnchor] Failed to parse features resource: {}", e.getMessage());
        }
    }

    private void loadIndustryMapping() {
        InputStream is = getClass().getResourceAsStream(INDUSTRY_MAPPING_RESOURCE);
        if (is == null) {
            log.info("[GrowthAnchor] Industry mapping not found at {}. Skipping.", INDUSTRY_MAPPING_RESOURCE);
            return;
        }
        try {
            List<Map<String, Object>> records = objectMapper.readValue(is, new TypeReference<>() {
            });
            for (Map<String, Object> rec : records) {
                String entity = normalizeEntity(str(rec.get("damodaran_entity")));
                if (entity == null || entity.isBlank()) {
                    continue;
                }
                String yahooIndustry = normalizeKey(str(rec.get("yahoo_industry")));
                if (yahooIndustry != null && !yahooIndustry.isBlank()) {
                    yahooToEntity.put(yahooIndustry, entity);
                }
                String yahooSector = normalizeKey(str(rec.get("yahoo_sector")));
                if (yahooSector != null && !yahooSector.isBlank()) {
                    yahooToEntity.putIfAbsent(yahooSector, entity);
                }
            }
            log.info("[GrowthAnchor] Loaded {} yahoo->entity mapping keys", yahooToEntity.size());
        } catch (IOException e) {
            log.warn("[GrowthAnchor] Failed to parse industry mapping resource: {}", e.getMessage());
        }
    }

    /**
     * Look up growth anchor for a specific Damodaran entity and region.
     *
     * @param entity Damodaran entity key (e.g. "softwareinternet")
     * @param region Region (e.g. "United States"). If null, picks first available.
     * @return Optional containing the DTO if found
     */
    public Optional<GrowthAnchorDTO> getAnchor(String entity, String region) {
        if (entity == null || entity.isBlank()) {
            return Optional.empty();
        }
        Map<String, GrowthAnchorDTO> regionMap = anchorsByEntity.get(entity.toLowerCase());
        if (regionMap == null || regionMap.isEmpty()) {
            return Optional.empty();
        }
        if (region != null && !region.isBlank()) {
            GrowthAnchorDTO dto = regionMap.get(region.toLowerCase());
            if (dto != null) {
                return Optional.of(dto);
            }
        }
        // Fallback: return the first (or "United States" if available)
        GrowthAnchorDTO us = regionMap.get("united states");
        if (us != null)
            return Optional.of(us);

        return regionMap.values().stream().findFirst();
    }

    /**
     * Look up growth anchor using Yahoo Finance industry via the mapping.
     * Falls back gracefully if no mapping exists.
     *
     * @param yahooIndustry Yahoo Finance industry string
     * @param region        Region hint (nullable)
     * @return Optional containing the DTO if found
     */
    public Optional<GrowthAnchorDTO> getAnchorByYahooIndustry(String yahooIndustry, String region) {
        if (yahooIndustry == null || yahooIndustry.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeKey(yahooIndustry);
        String mappedEntity = yahooToEntity.get(normalized);
        if (mappedEntity != null && !mappedEntity.isBlank()) {
            Optional<GrowthAnchorDTO> mapped = getAnchor(mappedEntity, region);
            if (mapped.isPresent()) {
                return mapped;
            }
        }
        return getAnchor(normalizeEntity(yahooIndustry), region);
    }

    /**
     * Get all available entities.
     */
    public Set<String> getAvailableEntities() {
        return Collections.unmodifiableSet(anchorsByEntity.keySet());
    }

    /**
     * Get all anchors for a specific region.
     */
    public List<GrowthAnchorDTO> getAnchorsByRegion(String region) {
        if (region == null || region.isBlank()) {
            return Collections.unmodifiableList(allAnchors);
        }
        String regionLower = region.toLowerCase();
        return allAnchors.stream()
                .filter(a -> regionLower.equals(
                        a.getRegion() != null ? a.getRegion().toLowerCase() : ""))
                .collect(Collectors.toList());
    }

    /**
     * Check if anchor data is loaded and available.
     */
    public boolean isAvailable() {
        return !allAnchors.isEmpty();
    }

    private GrowthAnchorDTO mapToDTO(Map<String, Object> rec) {
        return GrowthAnchorDTO.builder()
                .entity(str(rec.get("entity")))
                .entityDisplay(str(rec.get("entity_display")))
                .region(str(rec.get("region")))
                .year(intVal(rec.get("year")))
                .numberOfFirms(dbl(rec.get("number_of_firms")))
                .roe(dbl(rec.get("roe")))
                .equityReinvestmentRate(dbl(rec.get("equity_reinvestment_rate")))
                .fundamentalGrowth(dbl(rec.get("fundamental_growth")))
                .historicalGrowthProxy(dbl(rec.get("historical_growth_proxy")))
                .expectedGrowthProxy(dbl(rec.get("expected_growth_proxy")))
                .confidenceScore(dbl(rec.get("confidence_score")))
                .p10(dbl(rec.get("p10")))
                .p25(dbl(rec.get("p25")))
                .p50(dbl(rec.get("p50")))
                .p75(dbl(rec.get("p75")))
                .p90(dbl(rec.get("p90")))
                .salesGrowthHistorical(dbl(rec.get("sales_growth_historical")))
                .cagrNetIncome5y(dbl(rec.get("cagr_net_income_5y")))
                .expectedGrowth(dbl(rec.get("expected_growth")))
                .expectedGrowthEps5y(dbl(rec.get("expected_growth_eps_5y")))
                .salesGrowthExpected(dbl(rec.get("sales_growth_expected")))
                .deRatio(dbl(rec.get("d_e_ratio")))
                .taxRate(dbl(rec.get("tax_rate")))
                .leveredBeta(dbl(rec.get("levered_beta")))
                .stdDevInStock(dbl(rec.get("std_dev_in_stock")))
                .build();
    }

    private static String str(Object val) {
        return val != null ? val.toString() : null;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private static String normalizeEntity(String value) {
        return normalizeKey(value);
    }

    private static Double dbl(Object val) {
        if (val == null)
            return null;
        if (val instanceof Number)
            return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer intVal(Object val) {
        if (val == null)
            return null;
        if (val instanceof Number)
            return ((Number) val).intValue();
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isNewer(GrowthAnchorDTO candidate, GrowthAnchorDTO existing) {
        if (candidate == null) {
            return false;
        }
        if (existing == null) {
            return true;
        }
        Integer candidateYear = candidate.getYear();
        Integer existingYear = existing.getYear();
        if (candidateYear == null) {
            return false;
        }
        if (existingYear == null) {
            return true;
        }
        return candidateYear >= existingYear;
    }
}
