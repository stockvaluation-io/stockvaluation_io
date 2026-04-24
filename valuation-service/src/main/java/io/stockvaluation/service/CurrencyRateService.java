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
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CurrencyRateService {

    private static final String ECB_DAILY_RATES_URL =
            "https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml";

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
            log.warn("currency.api.key is not configured; using ECB exchange-rate fallback");
            fetchEcbExchangeRates();
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
                    Map<String, Double> loadedRates = new HashMap<>();

                    for (Map.Entry<?, ?> entry : dataMap.entrySet()) {
                        String currency = entry.getKey().toString();
                        Object value = entry.getValue();

                        if (value instanceof Number number) {
                            loadedRates.put(currency.toUpperCase(Locale.ROOT), number.doubleValue());
                        } else {
                            log.warn("Unexpected value type for currency {} => {}", currency, value.getClass());
                        }
                    }
                    replaceExchangeRatesIfUsable(loadedRates);
                } else {
                    log.warn("Unexpected format for 'data' in currency API response");
                    fetchEcbExchangeRates();
                }
            } else {
                log.warn("Invalid response from currency API");
                fetchEcbExchangeRates();
            }
        } catch (Exception e) {
            log.warn("Error fetching currency rates: {}", e.getMessage());
            fetchEcbExchangeRates();
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
            fetchEcbExchangeRates();
            fromRate = exchangeRates.get(currency.toUpperCase(Locale.ROOT));
            toRate = exchangeRates.get(financialCurrency.toUpperCase(Locale.ROOT));
        }

        if (fromRate == null || toRate == null) {
            throw new IllegalArgumentException("Currency not found: " + currency + " or " + financialCurrency);
        }

        return price * (toRate / fromRate);
    }

    public boolean isReady() {
        return !exchangeRates.isEmpty();
    }

    private void fetchEcbExchangeRates() {
        try {
            String xml = restTemplate.getForObject(ECB_DAILY_RATES_URL, String.class);
            if (!StringUtils.hasText(xml)) {
                log.warn("ECB exchange-rate fallback returned an empty response");
                return;
            }

            Map<String, Double> loadedRates = parseEcbRates(xml);
            replaceExchangeRatesIfUsable(loadedRates);
        } catch (Exception e) {
            log.warn("Error fetching ECB exchange-rate fallback: {}", e.getMessage());
        }
    }

    private Map<String, Double> parseEcbRates(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);

        Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        NodeList cubes = document.getElementsByTagName("Cube");

        Map<String, Double> ecbRatesPerEuro = new HashMap<>();
        for (int i = 0; i < cubes.getLength(); i++) {
            var node = cubes.item(i);
            var attributes = node.getAttributes();
            if (attributes == null || attributes.getNamedItem("currency") == null
                    || attributes.getNamedItem("rate") == null) {
                continue;
            }
            String currency = attributes.getNamedItem("currency").getNodeValue().toUpperCase(Locale.ROOT);
            double rate = Double.parseDouble(attributes.getNamedItem("rate").getNodeValue());
            ecbRatesPerEuro.put(currency, rate);
        }

        Double usdPerEuro = ecbRatesPerEuro.get("USD");
        if (usdPerEuro == null || usdPerEuro <= 0) {
            throw new IllegalStateException("ECB fallback did not include USD rate");
        }

        Map<String, Double> usdBaseRates = new HashMap<>();
        usdBaseRates.put("USD", 1.0);
        usdBaseRates.put("EUR", 1.0 / usdPerEuro);
        for (Map.Entry<String, Double> entry : ecbRatesPerEuro.entrySet()) {
            usdBaseRates.put(entry.getKey(), entry.getValue() / usdPerEuro);
        }
        return usdBaseRates;
    }

    private void replaceExchangeRatesIfUsable(Map<String, Double> loadedRates) {
        if (loadedRates.isEmpty() || !loadedRates.containsKey("USD")) {
            log.warn("Exchange-rate payload did not include usable USD-based rates");
            return;
        }
        exchangeRates.clear();
        exchangeRates.putAll(loadedRates);
    }
}
