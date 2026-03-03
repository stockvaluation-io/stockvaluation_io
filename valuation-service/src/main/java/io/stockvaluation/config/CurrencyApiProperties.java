package io.stockvaluation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "currency.api")
public class CurrencyApiProperties {

    private String baseUrl;
    private String key;
}
