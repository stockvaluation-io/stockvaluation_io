package io.stockvaluation.config;


import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI baseOpenAPI() {

        return new OpenAPI()
                .info(new Info().title("Stock Valuation Application: Backend ").
                        version("1.0.0").
                        description("The API provides a platform to query build Stock Valuation"));
    }
}