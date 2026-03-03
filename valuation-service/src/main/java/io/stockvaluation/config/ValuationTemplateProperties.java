package io.stockvaluation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "valuation.template")
public class ValuationTemplateProperties {

    private double expectedInflation = 0.03;
    private double expectedRealGrowth = 0.02;
    private double stableGrowthSpread = 0.0101;
    private double highGrowthSpread = 0.06;
    private double defaultDebtRatio = 0.04;
    private double defaultGrowthRate = 0.10;
    private double sustainableAdvantageSpread = 0.06;
    private int defaultProjectionYears = 10;
    private int threeStageProjectionYears = 15;
    private double defaultNormalizedMargin = 15.0;
}
