package io.stockvaluation.repository;

import io.stockvaluation.domain.CostOfCapital;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CostOfCapitalRepository extends JpaRepository<CostOfCapital, Long> {

    @Query("SELECT c FROM CostOfCapital c WHERE c.region = :region")
    Optional<CostOfCapital> findCostOfCapitalByRegion(@Param("region") String region);

    @Query(value = "Select * from cost_of_capital where region =:region", nativeQuery = true)
    Optional<CostOfCapital> findByRegion(String region);
}
