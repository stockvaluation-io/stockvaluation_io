package io.stockvaluation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${cors.allowed-origins:http://localhost:4200,http://127.0.0.1:4200,http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOriginsRaw;

    @Value("${cors.allow-all:false}")
    private boolean allowAll;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);

        if (allowedOrigins.length == 1 && "*".equals(allowedOrigins[0]) && !allowAll) {
            allowedOrigins = new String[]{
                    "http://localhost:4200",
                    "http://127.0.0.1:4200",
                    "http://localhost:3000",
                    "http://127.0.0.1:3000"
            };
        }

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-Requested-With");
    }
}
