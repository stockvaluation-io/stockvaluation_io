package io.stockvaluation.repository;

import io.stockvaluation.domain.IndustryAveragesUS;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndustryAveragesUSRepository extends JpaRepository<IndustryAveragesUS, Long> {

    @Query("SELECT inus.salesToCapital FROM IndustryAveragesUS inus WHERE inus.industryName =:industryName  ")
    Optional<Double> findSalesToCapitalByIndustryName(String industryName);

    @Query(value = "Select * from industry_averages_us where industry_name =:industryName", nativeQuery = true)
    IndustryAveragesUS findByIndustryName(String industryName);
}
