package io.stockvaluation.service;

import io.stockvaluation.config.CurrencyProviderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CurrencyProviderProperties currencyProviderProperties;

    @Test
    void fetchExchangeRatesLoadsFrankfurterRates() {
        when(currencyProviderProperties.getBaseUrl()).thenReturn("https://api.frankfurter.dev/v2");
        when(restTemplate.getForEntity(eq("https://api.frankfurter.dev/v2/rates?base=USD"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "TWD", "rate", 31.515),
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "EUR", "rate", 0.89),
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "INR", "rate", 85.14),
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "SEK", "rate", 9.61)
                )));

        CurrencyRateService service = new CurrencyRateService(restTemplate, currencyProviderProperties);
        service.fetchExchangeRates();

        assertTrue(service.isReady());
        assertEquals(315.15, service.convertCurrency("USD", "TWD", 10.0), 1e-9);
        assertEquals(10.0, service.convertCurrency("TWD", "USD", 315.15), 1e-9);
    }

    @Test
    void fetchExchangeRatesKeepsPreviousRatesWhenRefreshFails() {
        when(currencyProviderProperties.getBaseUrl()).thenReturn("https://api.frankfurter.dev/v2");
        when(restTemplate.getForEntity(eq("https://api.frankfurter.dev/v2/rates?base=USD"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "TWD", "rate", 31.515)
                )))
                .thenThrow(new RuntimeException("frankfurter down"));

        CurrencyRateService service = new CurrencyRateService(restTemplate, currencyProviderProperties);
        service.fetchExchangeRates();
        service.fetchExchangeRates();

        assertTrue(service.isReady());
        assertEquals(315.15, service.convertCurrency("USD", "TWD", 10.0), 1e-9);
    }

    @Test
    void fetchExchangeRatesKeepsPreviousRatesWhenRefreshPayloadHasNoQuotes() {
        when(currencyProviderProperties.getBaseUrl()).thenReturn("https://api.frankfurter.dev/v2");
        when(restTemplate.getForEntity(eq("https://api.frankfurter.dev/v2/rates?base=USD"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "TWD", "rate", 31.515)
                )))
                .thenReturn(ResponseEntity.ok(List.of()));

        CurrencyRateService service = new CurrencyRateService(restTemplate, currencyProviderProperties);
        service.fetchExchangeRates();
        service.fetchExchangeRates();

        assertTrue(service.isReady());
        assertEquals(315.15, service.convertCurrency("USD", "TWD", 10.0), 1e-9);
    }

    @Test
    void convertCurrencyThrowsWhenFrankfurterMissingCurrency() {
        when(currencyProviderProperties.getBaseUrl()).thenReturn("https://api.frankfurter.dev/v2");
        when(restTemplate.getForEntity(eq("https://api.frankfurter.dev/v2/rates?base=USD"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "EUR", "rate", 0.89)
                )));

        CurrencyRateService service = new CurrencyRateService(restTemplate, currencyProviderProperties);
        service.fetchExchangeRates();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.convertCurrency("USD", "TWD", 10.0));
        assertTrue(ex.getMessage().contains("Missing currency rate"));
        assertTrue(ex.getMessage().contains("TWD"));
    }

    @Test
    void initLoadsRatesFromFrankfurterRatesEndpoint() {
        when(currencyProviderProperties.getBaseUrl()).thenReturn("https://api.frankfurter.dev/v2");
        when(restTemplate.getForEntity(eq("https://api.frankfurter.dev/v2/rates?base=USD"), eq(List.class)))
                .thenReturn(ResponseEntity.ok(List.of(
                        Map.of("date", "2026-05-22", "base", "USD", "quote", "SEK", "rate", 9.61)
                )));

        CurrencyRateService service = new CurrencyRateService(restTemplate, currencyProviderProperties);
        service.init();

        verify(restTemplate).getForEntity("https://api.frankfurter.dev/v2/rates?base=USD", List.class);
        assertEquals(96.1, service.convertCurrency("USD", "SEK", 10.0), 1e-9);
    }
}
