package io.stockvaluation.repository;

import io.stockvaluation.domain.InputStatDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InputStatRepository extends JpaRepository<InputStatDistribution, Long> {

    @Query("SELECT inst FROM InputStatDistribution inst WHERE inst.industryGroup = :industryName")
    Optional<InputStatDistribution> findPreOperatingMarginByIndustryName(String industryName);

}
