package io.stockvaluation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "valuation.synthetic-rating")
public class SyntheticRatingProperties {

    private long largeCapThreshold = 5_000_000_000L;
    private String defaultCountry = "United States";
    private double interestCoverageCeiling = 100000;
    private double interestCoverageFloor = -100000;
}
