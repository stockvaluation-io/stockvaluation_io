package io.stockvaluation.service;

import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CurrencyRateService {

    private static final String API_URL = "https://api.freecurrencyapi.com/v1/latest?apikey=fca_live_f8gIHiN5YO7ZKmhXluqfoKHz6nU7jgwUJc5qchBA";

    private final Map<String, Double> exchangeRates = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();

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
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(API_URL, Map.class);
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
                            System.err.println("Unexpected value type for currency: " + currency + " => " + value.getClass());
                        }
                    }
                } else {
                    System.err.println("Unexpected format for 'data' in currency API response.");
                }
            } else {
                System.err.println("Invalid response from currency API");
            }
        } catch (Exception e) {
            System.err.println("Error fetching currency rates: " + e.getMessage());
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
