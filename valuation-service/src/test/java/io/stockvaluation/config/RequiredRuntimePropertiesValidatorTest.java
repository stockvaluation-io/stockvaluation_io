package io.stockvaluation.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequiredRuntimePropertiesValidatorTest {

    @Test
    void validate_allPropertiesPresent_doesNotThrow() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("provider.yfinance.base-url", "http://example.com");
        env.setProperty("spring.datasource.url", "jdbc:h2:mem:test");
        env.setProperty("spring.datasource.username", "sa");
        env.setProperty("spring.datasource.password", "pass");
        env.setProperty("currency.provider.base-url", "https://api.frankfurter.dev/v2");
        env.setProperty("default.username", "user");
        env.setProperty("default.password", "pass");
        env.setProperty("default.firstname", "John");
        env.setProperty("default.lastname", "Doe");
        env.setProperty("default.contact", "contact");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void validate_doesNotRequireCurrencyApiKey() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("provider.yfinance.base-url", "http://example.com");
        env.setProperty("spring.datasource.url", "jdbc:h2:mem:test");
        env.setProperty("spring.datasource.username", "sa");
        env.setProperty("spring.datasource.password", "pass");
        env.setProperty("currency.provider.base-url", "https://api.frankfurter.dev/v2");
        env.setProperty("default.username", "user");
        env.setProperty("default.password", "pass");
        env.setProperty("default.firstname", "John");
        env.setProperty("default.lastname", "Doe");
        env.setProperty("default.contact", "contact");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void validate_missingProperty_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment(); // Empty
        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);

        IllegalStateException ex = assertThrows(IllegalStateException.class, validator::validate);
        assertTrue(ex.getMessage().contains("Missing required runtime configuration:"));
    }

    @Test
    void validate_unresolvedPlaceholder_throwsIllegalStateException() {
        MockEnvironment env = new MockEnvironment();
        // Give valid values to all EXCEPT one
        env.setProperty("provider.yfinance.base-url", "http://example.com");
        env.setProperty("spring.datasource.url", "jdbc:h2:mem:test");
        env.setProperty("spring.datasource.username", "sa");
        env.setProperty("spring.datasource.password", "${unresolved.password}"); // unresolved
        env.setProperty("currency.provider.base-url", "https://api.frankfurter.dev/v2");
        env.setProperty("default.username", "user");
        env.setProperty("default.password", "pass");
        env.setProperty("default.firstname", "John");
        env.setProperty("default.lastname", "Doe");
        env.setProperty("default.contact", "contact");

        RequiredRuntimePropertiesValidator validator = new RequiredRuntimePropertiesValidator(env);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, validator::validate);
        assertTrue(ex.getMessage().contains("unresolved.password"));
    }
}
