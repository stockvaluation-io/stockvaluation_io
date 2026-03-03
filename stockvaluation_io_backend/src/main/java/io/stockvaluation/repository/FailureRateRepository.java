package io.stockvaluation.repository;

import io.stockvaluation.domain.FailureRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureRateRepository extends JpaRepository<FailureRate, Long> {
}
