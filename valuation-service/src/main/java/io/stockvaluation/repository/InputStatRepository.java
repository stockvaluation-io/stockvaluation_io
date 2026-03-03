package io.stockvaluation.repository;

import io.stockvaluation.domain.InputStatDistribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InputStatRepository extends JpaRepository<InputStatDistribution, Long> {

    Optional<InputStatDistribution> findFirstByIndustryGroupOrderByIdAsc(String industryGroup);

}
