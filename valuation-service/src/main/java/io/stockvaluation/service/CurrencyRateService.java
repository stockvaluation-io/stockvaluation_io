package io.stockvaluation.service;

import io.stockvaluation.config.CurrencyProviderProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrencyRateService {

    private final Map<String, Double> exchangeRates = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate;
    private final CurrencyProviderProperties currencyProviderProperties;

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
        String apiUrl = UriComponentsBuilder
                .fromHttpUrl(currencyProviderProperties.getBaseUrl())
                .path("/rates")
                .queryParam("base", "USD")
                .toUriString();
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(apiUrl, List.class);
            List<?> body = response.getBody();

            if (body != null) {
                replaceExchangeRatesIfUsable(parseFrankfurterRates(body));
            } else {
                log.warn("Invalid response from currency provider");
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

        Double fromRate = exchangeRates.get(currency.toUpperCase(Locale.ROOT));
        Double toRate = exchangeRates.get(financialCurrency.toUpperCase(Locale.ROOT));

        if (fromRate == null || toRate == null) {
            throw new IllegalArgumentException(
                    "Missing currency rate for " + missingCurrencies(currency, financialCurrency)
                            + " in Frankfurter USD base rates");
        }

        return price * (toRate / fromRate);
    }

    public boolean isReady() {
        return !exchangeRates.isEmpty();
    }

    private Map<String, Double> parseFrankfurterRates(List<?> rows) {
        Map<String, Double> loadedRates = new HashMap<>();
        loadedRates.put("USD", 1.0);

        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> rowMap)) {
                log.warn("Unexpected row type in currency provider response: {}", row == null ? "null" : row.getClass());
                continue;
            }

            Object base = rowMap.get("base");
            Object quote = rowMap.get("quote");
            Object rate = rowMap.get("rate");
            if (!"USD".equalsIgnoreCase(String.valueOf(base))
                    || !(quote instanceof String quoteCurrency)
                    || !StringUtils.hasText(quoteCurrency)
                    || !(rate instanceof Number number)
                    || number.doubleValue() <= 0) {
                log.warn("Skipping unusable currency provider row: {}", rowMap);
                continue;
            }
            loadedRates.put(quoteCurrency.toUpperCase(Locale.ROOT), number.doubleValue());
        }
        return loadedRates;
    }

    private String missingCurrencies(String currency, String financialCurrency) {
        List<String> missing = new ArrayList<>();
        if (!exchangeRates.containsKey(currency.toUpperCase(Locale.ROOT))) {
            missing.add(currency.toUpperCase(Locale.ROOT));
        }
        if (!exchangeRates.containsKey(financialCurrency.toUpperCase(Locale.ROOT))) {
            missing.add(financialCurrency.toUpperCase(Locale.ROOT));
        }
        return String.join(" or ", missing);
    }

    private void replaceExchangeRatesIfUsable(Map<String, Double> loadedRates) {
        if (loadedRates.size() <= 1 || !loadedRates.containsKey("USD")) {
            log.warn("Exchange-rate payload did not include usable USD-based rates");
            return;
        }
        exchangeRates.clear();
        exchangeRates.putAll(loadedRates);
    }
}
