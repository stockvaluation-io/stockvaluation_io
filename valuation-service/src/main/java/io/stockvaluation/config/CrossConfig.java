package io.stockvaluation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CrossConfig implements WebMvcConfigurer {

    @Value("${angular.app.url}")
    private String angularUrl;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
       registry.addMapping("/**")
                .allowedOrigins("https://stockvaluation.io")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
        /*registry.addMapping("/**").allowedMethods("*");*/
    }
}
