package io.stockvaluation.repository;

import io.stockvaluation.domain.RiskFreeRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RiskFreeRateRepository extends JpaRepository<RiskFreeRate, Long> {

    @Query("SELECT r.riskfreeRate FROM RiskFreeRate r WHERE r.currencyCode = :currencyCode")
    Optional<Double> findRiskFreeRateByCurrency(String currencyCode);
}
