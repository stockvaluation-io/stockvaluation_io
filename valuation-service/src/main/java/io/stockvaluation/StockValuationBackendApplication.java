package io.stockvaluation;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

@EnableScheduling
@SpringBootApplication
public class StockValuationBackendApplication {

    @Bean
    public RestTemplate restTemplate() {
        // Use BufferingClientHttpRequestFactory to avoid chunked encoding
        // This ensures Content-Length is set instead of Transfer-Encoding: chunked
        // Required for compatibility with eventlet-based Flask servers (can't handle
        // chunked encoding)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(300000); // 30 seconds
        requestFactory.setReadTimeout(3000000); // 300 seconds (5 minutes for LLM animation generation)

        // BufferingClientHttpRequestFactory buffers the request body to calculate
        // Content-Length
        BufferingClientHttpRequestFactory bufferingFactory = new BufferingClientHttpRequestFactory(requestFactory);

        RestTemplate restTemplate = new RestTemplate(bufferingFactory);

        // Configure ObjectMapper to allow NaN values (Python jsonify returns NaN for
        // float('nan'))
        // This is needed for ML DCF Forecast service which may return NaN in JSON
        JsonFactory jsonFactory = JsonFactory.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS)
                .build();
        ObjectMapper objectMapper = new ObjectMapper(jsonFactory);

        // Replace default Jackson converter with one that allows NaN
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        // Remove default converters and add our custom one
        restTemplate.getMessageConverters().removeIf(c -> c instanceof MappingJackson2HttpMessageConverter);
        restTemplate.getMessageConverters().add(converter);

        return restTemplate;
    }

    public static void main(String[] args) {
        SpringApplication.run(StockValuationBackendApplication.class, args);
    }
}
