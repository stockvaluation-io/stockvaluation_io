package io.stockvaluation.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorsConfigTest {

    @Test
    void addCorsMappings_withStandardOrigins() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "http://example.com, http://example.org");
        ReflectionTestUtils.setField(config, "allowAll", false);

        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping(anyString())).thenReturn(registration);
        when(registration.allowedOrigins(any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOrigins("http://example.com", "http://example.org");
        verify(registration).allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        verify(registration).allowedHeaders("Content-Type", "Authorization", "X-Requested-With");
    }

    @Test
    void addCorsMappings_withWildcard_andAllowAllFalse_usesDefaultLocalhost() {
        CorsConfig config = new CorsConfig();
        // If it's "*" and allowAll is false, it replaces it with default arrays
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "*");
        ReflectionTestUtils.setField(config, "allowAll", false);

        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping(anyString())).thenReturn(registration);
        when(registration.allowedOrigins(any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOrigins(
                "http://localhost:3000",
                "http://127.0.0.1:3000");
    }

    @Test
    void addCorsMappings_withWildcard_andAllowAllTrue_usesWildcard() {
        CorsConfig config = new CorsConfig();
        // If it's "*" and allowAll is true, it retains "*"
        ReflectionTestUtils.setField(config, "allowedOriginsRaw", "*");
        ReflectionTestUtils.setField(config, "allowAll", true);

        CorsRegistry registry = mock(CorsRegistry.class);
        CorsRegistration registration = mock(CorsRegistration.class);
        when(registry.addMapping(anyString())).thenReturn(registration);
        when(registration.allowedOrigins(any(String[].class))).thenReturn(registration);
        when(registration.allowedMethods(any(String[].class))).thenReturn(registration);

        config.addCorsMappings(registry);

        verify(registry).addMapping("/**");
        verify(registration).allowedOrigins("*");
    }
}
