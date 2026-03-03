package io.stockvaluation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.stockvaluation.dto.ValuationOutputDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Service for generating Manim-based DCF valuation animations.
 * Calls the Python valuation-animation service to render videos.
 */
@Service
@Slf4j
public class ValuationAnimationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${valuation.animation.service.url:http://valuation-animation:5001}")
    private String animationServiceUrl;

    @Value("${valuation.animation.enabled:true}")
    private boolean animationEnabled;

    /**
     * Generate a DCF animation video from valuation data.
     *
     * @param valuationOutput The valuation output containing financial data
     * @return Base64-encoded MP4 video string, or null if generation fails
     */
    public String generateAnimation(ValuationOutputDTO valuationOutput) {
        if (!animationEnabled) {
            log.debug("Animation generation is disabled");
            return null;
        }

        if (valuationOutput == null || valuationOutput.getFinancialDTO() == null) {
            log.warn("Cannot generate animation: missing valuation data");
            return null;
        }

        try {
            String url = animationServiceUrl + "/generate-animation";

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Convert valuation output to JSON
            String jsonBody = objectMapper.writeValueAsString(valuationOutput);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);

            log.info("Calling animation service for {}", valuationOutput.getCompanyName());

            // Call animation service with timeout
            @SuppressWarnings("unchecked")
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    (Class<Map<String, Object>>) (Class<?>) Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object videoBase64 = response.getBody().get("video_base64");
                if (videoBase64 != null) {
                    log.info("Successfully generated animation for {}", valuationOutput.getCompanyName());
                    return videoBase64.toString();
                }
            }

            log.warn("Animation service returned empty response for {}", valuationOutput.getCompanyName());
            return null;

        } catch (Exception e) {
            log.error("Failed to generate animation for {}: {}",
                    valuationOutput.getCompanyName(), e.getMessage());
            return null;
        }
    }

    /**
     * Check if the animation service is available.
     */
    public boolean isServiceAvailable() {
        try {
            String url = animationServiceUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.debug("Animation service not available: {}", e.getMessage());
            return false;
        }
    }
}
