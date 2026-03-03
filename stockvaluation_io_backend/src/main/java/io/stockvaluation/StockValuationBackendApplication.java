package io.stockvaluation;

import io.stockvaluation.config.PasswordUtils;
import io.stockvaluation.domain.User;
import io.stockvaluation.enums.Role;
import io.stockvaluation.repository.UserRepository;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;
import org.springframework.cache.annotation.EnableCaching;

@EnableCaching
@SpringBootApplication
public class StockValuationBackendApplication implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Bean
    public RestTemplate restTemplate() {
        // Use BufferingClientHttpRequestFactory to avoid chunked encoding
        // This ensures Content-Length is set instead of Transfer-Encoding: chunked
        // Required for compatibility with eventlet-based Flask servers (can't handle
        // chunked encoding)
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000000); // 5 minutes
        requestFactory.setReadTimeout(3000000); // 5 minutes

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

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.count() < 1) {
            User user = new User();
            user.setFirstName("super");
            user.setLastName("admin");
            user.setUsername("super@admin.io");
            user.setContactNo("123456789");
            user.setPassword(PasswordUtils.encodePassword("12345"));
            user.setRole(Role.SUPER_ADMIN);
            userRepository.save(user);
        } else {
            System.out.println("user already created");
        }
    }
}
