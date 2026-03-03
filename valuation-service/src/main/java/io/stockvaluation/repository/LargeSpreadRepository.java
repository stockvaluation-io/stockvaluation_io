package io.stockvaluation.repository;

import io.stockvaluation.domain.LargeBondSpread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface LargeSpreadRepository extends JpaRepository<LargeBondSpread, Long> {
    @Query(value = "SELECT * FROM large_bond_spread WHERE coverage_ratio_min <= :interestCoverageRatio AND :interestCoverageRatio <= coverage_ratio_max", nativeQuery = true)
    LargeBondSpread findRating(double interestCoverageRatio);

}
