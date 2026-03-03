package io.stockvaluation.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RequiredRuntimePropertiesValidator {

    private final Environment environment;

    @PostConstruct
    void validate() {
        List<String> requiredKeys = List.of(
                "provider.yfinance.base-url",
                "spring.datasource.url",
                "spring.datasource.username",
                "spring.datasource.password",
                "currency.api.base-url",
                "currency.api.key",
                "default.username",
                "default.password",
                "default.firstname",
                "default.lastname",
                "default.contact");

        for (String key : requiredKeys) {
            String value = environment.getProperty(key);
            if (!StringUtils.hasText(value) || value.contains("${")) {
                throw new IllegalStateException("Missing required runtime configuration: " + key);
            }
        }
    }
}
