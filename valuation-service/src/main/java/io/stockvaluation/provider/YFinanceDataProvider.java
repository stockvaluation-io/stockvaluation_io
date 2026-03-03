package io.stockvaluation.provider;

import io.stockvaluation.config.YFinanceProviderProperties;
import io.stockvaluation.dto.CompanyDataDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default raw financial data provider backed by the internal yfinance service.
 */
@Service
@Primary
@Slf4j
@RequiredArgsConstructor
public class YFinanceDataProvider implements DataProvider {

    private final RestTemplate restTemplate;
    private final YFinanceProviderProperties yFinanceProviderProperties;

    @Override
    public CompanyDataDTO getCompanyData(String ticker) {
        throw new DataProviderException(
                getProviderName(),
                ticker,
                "getCompanyData is not implemented yet; CommonService still assembles CompanyDataDTO");
    }

    @Override
    public Map<String, Object> getCompanyInfo(String ticker) {
        return getMap(endpoint("/info", ticker, null), ticker, "info");
    }

    @Override
    public Map<String, Map<String, Object>> getIncomeStatement(String ticker) {
        return getIncomeStatement(ticker, "yearly");
    }

    @Override
    public Map<String, Map<String, Object>> getIncomeStatement(String ticker, String freq) {
        return toTimeSeriesMap(getMap(
            endpoint("/income-stmt", ticker, freq),
            ticker,
            "income-stmt(" + normalizeFreq(freq) + ")"));
    }

    @Override
    public Map<String, Map<String, Object>> getBalanceSheet(String ticker) {
        return getBalanceSheet(ticker, "yearly");
    }

    @Override
    public Map<String, Map<String, Object>> getBalanceSheet(String ticker, String freq) {
        return toTimeSeriesMap(getMap(
            endpoint("/balance-sheet", ticker, freq),
            ticker,
            "balance-sheet(" + normalizeFreq(freq) + ")"));
    }

    @Override
    public Map<String, Object> getRevenueEstimate(String ticker) {
        return getRevenueEstimate(ticker, "yearly");
    }

    @Override
    public Map<String, Object> getRevenueEstimate(String ticker, String freq) {
        return getMap(endpoint("/revenue-estimate", ticker, freq), ticker, "revenue-estimate");
    }

    @Override
    public List<Map<String, Object>> getDividendHistory(String ticker) {
        Map<String, Object> raw = getDividendData(ticker);
        Object historyObj = raw.get("dividendHistory");
        List<Map<String, Object>> rows = new ArrayList<>();
        if (historyObj instanceof Map<?, ?> historyMap) {
            for (Map.Entry<?, ?> e : historyMap.entrySet()) {
                Map<String, Object> row = new HashMap<>();
                row.put("date", String.valueOf(e.getKey()));
                row.put("amount", e.getValue());
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public Map<String, Object> getDividendData(String ticker) {
        return getMap(endpoint("/dividends", ticker, null), ticker, "dividends");
    }

    @Override
    public String getProviderName() {
        return "yfinance-http";
    }

    private String endpoint(String path, String ticker, String freq) {
        StringBuilder url = new StringBuilder();
        url.append(yFinanceProviderProperties.getBaseUrl());
        if (!path.startsWith("/")) {
            url.append("/");
        }
        url.append(path);
        url.append("?ticker=").append(ticker);
        if (freq != null && !freq.isBlank()) {
            url.append("&freq=").append(normalizeFreq(freq));
        }
        return url.toString();
    }

    private String normalizeFreq(String freq) {
        if (freq == null || freq.isBlank()) {
            return "yearly";
        }
        return freq.trim().toLowerCase();
    }

    private Map<String, Object> getMap(String url, String ticker, String operation) {
        try {
            Map<String, Object> body = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    }).getBody();
            return body != null ? body : new HashMap<>();
        } catch (Exception e) {
            log.error("Data provider call failed (op={}, ticker={}, url={}): {}", operation, ticker, url, e.getMessage());
            throw new DataProviderException(getProviderName(), ticker, "Failed " + operation, e);
        }
    }

    private Map<String, Map<String, Object>> toTimeSeriesMap(Map<String, Object> raw) {
        Map<String, Map<String, Object>> typed = new HashMap<>();
        if (raw == null) {
            return typed;
        }

        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (entry.getValue() instanceof Map<?, ?> nested) {
                Map<String, Object> point = new HashMap<>();
                for (Map.Entry<?, ?> nestedEntry : nested.entrySet()) {
                    point.put(String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
                }
                typed.put(entry.getKey(), point);
            }
        }
        return typed;
    }
}
