package io.stockvaluation.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredRuntimePropertiesValidatorTest {

    @Test
    void validate_withAllRequiredProperties_doesNotThrow() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("provider.yfinance.base-url", "http://localhost:8000")
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/test")
                .withProperty("spring.datasource.username", "user")
                .withProperty("spring.datasource.password", "pass")
                .withProperty("currency.api.base-url", "https://example.com")
                .withProperty("currency.api.key", "key")
                .withProperty("default.username", "admin")
                .withProperty("default.password", "password")
                .withProperty("default.firstname", "Test")
                .withProperty("default.lastname", "User")
                .withProperty("default.contact", "test@example.com");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);

        assertDoesNotThrow(validator::validate);
    }

    @Test
    void validate_missingProperty_throws() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("provider.yfinance.base-url", "http://localhost:8000");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("spring.datasource.url"));
    }

    @Test
    void validate_unresolvedPlaceholder_throws() {
        MockEnvironment env = new MockEnvironment()
                .withProperty("provider.yfinance.base-url", "http://localhost:8000")
                .withProperty("spring.datasource.url", "${DB_URL}")
                .withProperty("spring.datasource.username", "user")
                .withProperty("spring.datasource.password", "pass")
                .withProperty("currency.api.base-url", "https://example.com")
                .withProperty("currency.api.key", "key")
                .withProperty("default.username", "admin")
                .withProperty("default.password", "password")
                .withProperty("default.firstname", "Test")
                .withProperty("default.lastname", "User")
                .withProperty("default.contact", "test@example.com");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, validator::validate);
        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("DB_URL"));
    }
}
