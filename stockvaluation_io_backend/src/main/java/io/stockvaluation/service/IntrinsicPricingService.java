package io.stockvaluation.service;

import io.stockvaluation.dto.valuationOutputDTO.IntrinsicPricingDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.util.*;

@Slf4j
@Service
public class IntrinsicPricingService {

    @Value("${intrinsic.pricing.url:http://intrinsic-pricing:5004}")
    private String intrinsicPricingBaseUrl;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Fetches intrinsic pricing data for a given ticker.
     * Returns null if the service is unavailable or returns an error.
     * 
     * @param ticker Stock ticker symbol
     * @param companyName Company name (optional, will use ticker if null)
     * @return IntrinsicPricingDTO or null if unavailable
     */
    public IntrinsicPricingDTO getIntrinsicPricing(String ticker, String companyName) {
        return getIntrinsicPricing(ticker, companyName, false);
    }
    
    /**
     * Fetches intrinsic pricing data for a given ticker.
     * Returns null if the service is unavailable or returns an error.
     * 
     * @param ticker Stock ticker symbol
     * @param companyName Company name (optional, will use ticker if null)
     * @param useV2 Whether to use the enhanced V2 endpoint (with improved R²)
     * @return IntrinsicPricingDTO or null if unavailable
     */
    public IntrinsicPricingDTO getIntrinsicPricing(String ticker, String companyName, boolean useV2) {
        String endpoint = useV2 ? "/api/intrinsic-pricing/calculate-v2" : "/api/intrinsic-pricing/calculate";
        String url = intrinsicPricingBaseUrl + endpoint;
        
        try {
            // Build request body
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("ticker", ticker);
            requestBody.put("company_name", companyName != null ? companyName : ticker);
            requestBody.put("multiples", Arrays.asList("all"));
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            log.info("Calling intrinsic pricing service {} for ticker: {} at URL: {}", (useV2 ? "V2" : "V1"), ticker, url);
            
            ResponseEntity<Map> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                request,
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Successfully received intrinsic pricing response for {}: {} peers found", ticker, 
                    response.getBody().get("peers_found"));
                return mapResponseToDTO(response.getBody());
            } else {
                log.warn("Intrinsic pricing service returned non-2xx status for {}: {}", ticker, response.getStatusCode());
                return null;
            }
            
        } catch (RestClientException e) {
            log.error("RestClientException fetching intrinsic pricing {} for {}: {} - URL: {}", 
                (useV2 ? "V2" : "V1"), ticker, e.getMessage(), url, e);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error fetching intrinsic pricing {} for {}: {} - URL: {}", 
                (useV2 ? "V2" : "V1"), ticker, e.getMessage(), url, e);
            return null;
        }
    }
    
    /**
     * Convenience method that uses ticker as company name
     */
    public IntrinsicPricingDTO getIntrinsicPricing(String ticker) {
        return getIntrinsicPricing(ticker, null);
    }
    
    /**
     * Maps the JSON response from intrinsic pricing API to IntrinsicPricingDTO
     */
    @SuppressWarnings("unchecked")
    private IntrinsicPricingDTO mapResponseToDTO(Map<String, Object> response) {
        try {
            IntrinsicPricingDTO dto = new IntrinsicPricingDTO();
            
            dto.setCompany((String) response.get("company"));
            dto.setTicker((String) response.get("ticker"));
            dto.setSector((String) response.get("sector"));
            dto.setPeersFound(response.get("peers_found") != null ? 
                ((Number) response.get("peers_found")).intValue() : null);
            dto.setLlmEnhanced((Boolean) response.get("llm_enhanced"));
            dto.setRecommendedMultiple((String) response.get("recommended_multiple"));
            dto.setRecommendationReason((String) response.get("recommendation_reason"));
            dto.setTimestamp((String) response.get("timestamp"));
            
            // Map peer list
            if (response.get("peer_list") instanceof List) {
                List<String> peerList = new ArrayList<>();
                for (Object peer : (List<?>) response.get("peer_list")) {
                    if (peer != null) {
                        peerList.add(peer.toString());
                    }
                }
                dto.setPeerList(peerList);
            }
            
            // Map multiples
            if (response.get("multiples") instanceof Map) {
                Map<String, IntrinsicPricingDTO.MultipleResultDTO> multiples = new HashMap<>();
                Map<String, Object> multiplesMap = (Map<String, Object>) response.get("multiples");
                
                for (Map.Entry<String, Object> entry : multiplesMap.entrySet()) {
                    String multipleName = entry.getKey();
                    Object multipleData = entry.getValue();
                    
                    if (multipleData instanceof Map) {
                        IntrinsicPricingDTO.MultipleResultDTO multipleDTO = mapMultipleResult((Map<String, Object>) multipleData);
                        multiples.put(multipleName, multipleDTO);
                    }
                }
                dto.setMultiples(multiples);
            }
            
            // Map sector recommendation
            if (response.get("sector_recommendation") instanceof Map) {
                Map<String, Object> sectorRec = (Map<String, Object>) response.get("sector_recommendation");
                IntrinsicPricingDTO.SectorRecommendationDTO sectorDTO = new IntrinsicPricingDTO.SectorRecommendationDTO();
                sectorDTO.setMultiple((String) sectorRec.get("multiple"));
                sectorDTO.setSector((String) sectorRec.get("sector"));
                sectorDTO.setRationale((String) sectorRec.get("rationale"));
                if (sectorRec.get("r_squared") != null) {
                    sectorDTO.setRSquared(((Number) sectorRec.get("r_squared")).doubleValue());
                }
                dto.setSectorRecommendation(sectorDTO);
            }
            
            return dto;
            
        } catch (Exception e) {
            log.error("Error mapping intrinsic pricing response to DTO: {}", e.getMessage(), e);
            return null;
        }
    }
    
    @SuppressWarnings("unchecked")
    private IntrinsicPricingDTO.MultipleResultDTO mapMultipleResult(Map<String, Object> multipleData) {
        IntrinsicPricingDTO.MultipleResultDTO dto = new IntrinsicPricingDTO.MultipleResultDTO();
        
        // Handle error case
        if (multipleData.containsKey("error")) {
            dto.setError((String) multipleData.get("error"));
            return dto;
        }
        
        // Map basic fields
        if (multipleData.get("intrinsic_value") != null) {
            dto.setIntrinsicValue(((Number) multipleData.get("intrinsic_value")).doubleValue());
        }
        if (multipleData.get("market_value") != null) {
            dto.setMarketValue(((Number) multipleData.get("market_value")).doubleValue());
        }
        if (multipleData.get("mispricing_pct") != null) {
            dto.setMispricingPct(((Number) multipleData.get("mispricing_pct")).doubleValue());
        }
        dto.setConclusion((String) multipleData.get("conclusion"));
        if (multipleData.get("n_peers_used") != null) {
            dto.setNPeersUsed(((Number) multipleData.get("n_peers_used")).intValue());
        }
        
        // Map confidence interval
        if (multipleData.get("confidence_interval") instanceof Map) {
            Map<String, Object> ci = (Map<String, Object>) multipleData.get("confidence_interval");
            IntrinsicPricingDTO.ConfidenceIntervalDTO ciDTO = new IntrinsicPricingDTO.ConfidenceIntervalDTO();
            if (ci.get("lower_bound") != null) {
                ciDTO.setLowerBound(((Number) ci.get("lower_bound")).doubleValue());
            }
            if (ci.get("upper_bound") != null) {
                ciDTO.setUpperBound(((Number) ci.get("upper_bound")).doubleValue());
            }
            if (ci.get("confidence") != null) {
                ciDTO.setConfidence(((Number) ci.get("confidence")).doubleValue());
            }
            if (ci.get("interval_width") != null) {
                ciDTO.setIntervalWidth(((Number) ci.get("interval_width")).doubleValue());
            }
            dto.setConfidenceInterval(ciDTO);
        }
        
        // Map distribution stats
        if (multipleData.get("distribution") instanceof Map) {
            Map<String, Object> dist = (Map<String, Object>) multipleData.get("distribution");
            IntrinsicPricingDTO.DistributionStatsDTO distDTO = new IntrinsicPricingDTO.DistributionStatsDTO();
            if (dist.get("median") != null) distDTO.setMedian(((Number) dist.get("median")).doubleValue());
            if (dist.get("mean") != null) distDTO.setMean(((Number) dist.get("mean")).doubleValue());
            if (dist.get("std") != null) distDTO.setStd(((Number) dist.get("std")).doubleValue());
            if (dist.get("min") != null) distDTO.setMin(((Number) dist.get("min")).doubleValue());
            if (dist.get("max") != null) distDTO.setMax(((Number) dist.get("max")).doubleValue());
            if (dist.get("percentile_25") != null) distDTO.setPercentile25(((Number) dist.get("percentile_25")).doubleValue());
            if (dist.get("percentile_75") != null) distDTO.setPercentile75(((Number) dist.get("percentile_75")).doubleValue());
            if (dist.get("percentile_10") != null) distDTO.setPercentile10(((Number) dist.get("percentile_10")).doubleValue());
            if (dist.get("percentile_90") != null) distDTO.setPercentile90(((Number) dist.get("percentile_90")).doubleValue());
            if (dist.get("target_percentile") != null) distDTO.setTargetPercentile(((Number) dist.get("target_percentile")).doubleValue());
            distDTO.setTargetPosition((String) dist.get("target_position"));
            dto.setDistribution(distDTO);
        }
        
        // Map regression details
        if (multipleData.get("regression") instanceof Map) {
            Map<String, Object> reg = (Map<String, Object>) multipleData.get("regression");
            IntrinsicPricingDTO.RegressionDetailsDTO regDTO = new IntrinsicPricingDTO.RegressionDetailsDTO();
            regDTO.setModelType((String) reg.get("model_type"));
            if (reg.get("r_squared") != null) {
                regDTO.setRSquared(((Number) reg.get("r_squared")).doubleValue());
            }
            if (reg.get("n_samples") != null) {
                regDTO.setNSamples(((Number) reg.get("n_samples")).intValue());
            }
            if (reg.get("features") instanceof List) {
                List<String> features = new ArrayList<>();
                for (Object f : (List<?>) reg.get("features")) {
                    if (f != null) features.add(f.toString());
                }
                regDTO.setFeatures(features);
            }
            if (reg.get("coefficients") instanceof Map) {
                Map<String, Double> coefficients = new HashMap<>();
                Map<String, Object> coefMap = (Map<String, Object>) reg.get("coefficients");
                for (Map.Entry<String, Object> entry : coefMap.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        coefficients.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                regDTO.setCoefficients(coefficients);
            }
            regDTO.setMarketCapWeighted((Boolean) reg.get("market_cap_weighted"));
            regDTO.setSimilarityWeighted((Boolean) reg.get("similarity_weighted"));
            
            // Map VIF scores
            if (reg.get("vif_scores") instanceof Map) {
                Map<String, Double> vifScores = new HashMap<>();
                Map<String, Object> vifMap = (Map<String, Object>) reg.get("vif_scores");
                for (Map.Entry<String, Object> entry : vifMap.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        vifScores.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                regDTO.setVifScores(vifScores);
            }
            
            // Map high VIF warning
            if (reg.get("high_vif_warning") instanceof Map) {
                Map<String, Double> highVif = new HashMap<>();
                Map<String, Object> highVifMap = (Map<String, Object>) reg.get("high_vif_warning");
                for (Map.Entry<String, Object> entry : highVifMap.entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        highVif.put(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                    }
                }
                regDTO.setHighVifWarning(highVif);
            }
            
            dto.setRegression(regDTO);
        }
        
        // Map feature importance
        if (multipleData.get("feature_importance") instanceof List) {
            List<IntrinsicPricingDTO.FeatureImportanceDTO> featureImportance = new ArrayList<>();
            List<Object> fiList = (List<Object>) multipleData.get("feature_importance");
            for (Object fiObj : fiList) {
                if (fiObj instanceof Map) {
                    Map<String, Object> fi = (Map<String, Object>) fiObj;
                    IntrinsicPricingDTO.FeatureImportanceDTO fiDTO = new IntrinsicPricingDTO.FeatureImportanceDTO();
                    fiDTO.setFeature((String) fi.get("feature"));
                    if (fi.get("coefficient") != null) {
                        fiDTO.setCoefficient(((Number) fi.get("coefficient")).doubleValue());
                    }
                    if (fi.get("abs_coefficient") != null) {
                        fiDTO.setAbsCoefficient(((Number) fi.get("abs_coefficient")).doubleValue());
                    }
                    featureImportance.add(fiDTO);
                }
            }
            dto.setFeatureImportance(featureImportance);
        }
        
        // Map coefficient validation
        if (multipleData.get("coefficient_validation") instanceof Map) {
            Map<String, IntrinsicPricingDTO.CoefficientValidationDTO> coefValidation = new HashMap<>();
            Map<String, Object> coefMap = (Map<String, Object>) multipleData.get("coefficient_validation");
            
            for (Map.Entry<String, Object> entry : coefMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    Map<String, Object> coefData = (Map<String, Object>) entry.getValue();
                    IntrinsicPricingDTO.CoefficientValidationDTO coefDTO = new IntrinsicPricingDTO.CoefficientValidationDTO();
                    
                    if (coefData.get("coefficient") != null) {
                        coefDTO.setCoefficient(((Number) coefData.get("coefficient")).doubleValue());
                    }
                    coefDTO.setExpectedSign((String) coefData.get("expected_sign"));
                    coefDTO.setActualSign((String) coefData.get("actual_sign"));
                    coefDTO.setMatchesTheory((Boolean) coefData.get("matches_theory"));
                    if (coefData.get("vif") != null) {
                        coefDTO.setVif(((Number) coefData.get("vif")).doubleValue());
                    }
                    coefDTO.setHighMulticollinearity((Boolean) coefData.get("high_multicollinearity"));
                    coefDTO.setViolationLikelyDueToMulticollinearity((Boolean) coefData.get("violation_likely_due_to_multicollinearity"));
                    
                    coefValidation.put(entry.getKey(), coefDTO);
                }
            }
            dto.setCoefficientValidation(coefValidation);
        }
        
        // Map peer comparison (if available)
        // Python API structure: {ticker, company_name, {multiple_type}_actual, feature1, feature2, ...}
        if (multipleData.get("peer_comparison") instanceof List) {
            List<IntrinsicPricingDTO.PeerComparisonDTO> peerComparison = new ArrayList<>();
            List<Object> peerList = (List<Object>) multipleData.get("peer_comparison");
            
            for (Object peerObj : peerList) {
                if (peerObj instanceof Map) {
                    Map<String, Object> peer = (Map<String, Object>) peerObj;
                    IntrinsicPricingDTO.PeerComparisonDTO peerDTO = new IntrinsicPricingDTO.PeerComparisonDTO();
                    peerDTO.setTicker((String) peer.get("ticker"));
                    peerDTO.setCompanyName((String) peer.get("company_name"));
                    
                    // Find the multiple value (key ending with "_actual")
                    Double multipleValue = null;
                    Map<String, Double> features = new HashMap<>();
                    
                    for (Map.Entry<String, Object> entry : peer.entrySet()) {
                        String key = entry.getKey();
                        Object value = entry.getValue();
                        
                        // Skip ticker and company_name
                        if ("ticker".equals(key) || "company_name".equals(key)) {
                            continue;
                        }
                        
                        // Check if this is the multiple value (ends with "_actual")
                        if (key.endsWith("_actual") && value instanceof Number) {
                            multipleValue = ((Number) value).doubleValue();
                        } else if (value instanceof Number) {
                            // All other numeric fields are features
                            features.put(key, ((Number) value).doubleValue());
                        }
                    }
                    
                    if (multipleValue != null) {
                        peerDTO.setMultipleValue(multipleValue);
                    }
                    if (!features.isEmpty()) {
                        peerDTO.setFeatures(features);
                    }
                    peerComparison.add(peerDTO);
                }
            }
            dto.setPeerComparison(peerComparison);
        }
        
        return dto;
    }
}

