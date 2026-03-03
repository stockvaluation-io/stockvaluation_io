package io.stockvaluation.service;

import io.stockvaluation.config.CurrencyApiProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrencyRateService {

    private final Map<String, Double> exchangeRates = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final CurrencyApiProperties currencyApiProperties;

    /**
     * Runs at application startup
     */
    @PostConstruct
    public void init() {
        fetchExchangeRates();
    }

    /**
     * Runs twice a day: at 00:00 and 12:00
     */
    @Scheduled(cron = "0 0 0,12 * * ?")
    public void fetchExchangeRates() {
        if (!StringUtils.hasText(currencyApiProperties.getKey())) {
            log.warn("currency.api.key is not configured; skipping exchange-rate sync");
            return;
        }

        String apiUrl = UriComponentsBuilder
                .fromHttpUrl(currencyApiProperties.getBaseUrl())
                .queryParam("apikey", currencyApiProperties.getKey())
                .toUriString();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(apiUrl, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && body.containsKey("data")) {
                Object dataObj = body.get("data");

                if (dataObj instanceof Map<?, ?> dataMap) {
                    exchangeRates.clear();

                    for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                        String currency = entry.getKey().toString();
                        Object value = entry.getValue();

                        if (value instanceof Number number) {
                            exchangeRates.put(currency, number.doubleValue());
                        } else {
                            log.warn("Unexpected value type for currency {} => {}", currency, value.getClass());
                        }
                    }
                } else {
                    log.warn("Unexpected format for 'data' in currency API response");
                }
            } else {
                log.warn("Invalid response from currency API");
            }
        } catch (Exception e) {
            log.warn("Error fetching currency rates: {}", e.getMessage());
        }
    }

    /**
     * Converts price from one currency to another using USD as base.
     */
    public double convertCurrency(String currency, String financialCurrency, Double price) {
        if (currency.equalsIgnoreCase(financialCurrency)) {
            return price;
        }

        Double fromRate = exchangeRates.get(currency.toUpperCase());
        Double toRate = exchangeRates.get(financialCurrency.toUpperCase());

        if (fromRate == null || toRate == null) {
            throw new IllegalArgumentException("Currency not found: " + currency + " or " + financialCurrency);
        }

        return price * (toRate / fromRate);
    }

    public boolean isReady() {
        return !exchangeRates.isEmpty();
    }
}
