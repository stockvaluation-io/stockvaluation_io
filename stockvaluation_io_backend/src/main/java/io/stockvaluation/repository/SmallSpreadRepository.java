package io.stockvaluation.repository;

import io.stockvaluation.domain.SmallBondSpread;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SmallSpreadRepository extends JpaRepository<SmallBondSpread, Long> {

    @Query(value = "SELECT * FROM small_bond_spread WHERE coverage_ratio_min <= :interestCoverageRatio AND :interestCoverageRatio <= coverage_ratio_max", nativeQuery = true)
    SmallBondSpread findRating(@Param("interestCoverageRatio") double interestCoverageRatio);

}
