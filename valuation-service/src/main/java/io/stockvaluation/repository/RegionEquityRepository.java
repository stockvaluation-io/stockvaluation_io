package io.stockvaluation.repository;

import io.stockvaluation.domain.RegionEquity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RegionEquityRepository extends JpaRepository<RegionEquity, Long> {
}
