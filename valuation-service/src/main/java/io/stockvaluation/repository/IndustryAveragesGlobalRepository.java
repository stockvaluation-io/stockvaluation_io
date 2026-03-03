package io.stockvaluation.repository;

import io.stockvaluation.domain.IndustryAveragesGlobal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IndustryAveragesGlobalRepository extends JpaRepository<IndustryAveragesGlobal, Long> {

    @Query("SELECT ing.salesToCapital FROM IndustryAveragesGlobal ing WHERE ing.industryName =:industryName")
    Optional<Double> findSalesToCapitalByIndustryName(String industryName);

    @Query(value = "Select * from industry_averages_global where industry_name =:industryName", nativeQuery = true)
    IndustryAveragesGlobal findByIndustryName(String industryName);

    // find revenue growth
    @Query(value = "Select annual_average_revenue_growth from industry_averages_global where industry_name =:industryName", nativeQuery = true)
    Optional<Double> findRevenueGrowth(String industryName);

    // find pre tax operating margin
    @Query(value = "Select pre_tax_operating_margin from industry_averages_global where industry_name =:industryName", nativeQuery = true)
    Optional<Double> findOperatingMargin(String industryName);

    // Batch lookup by multiple industry names
    @Query("SELECT ig FROM IndustryAveragesGlobal ig WHERE ig.industryName IN :industryNames")
    List<IndustryAveragesGlobal> findByIndustryNameIn(List<String> industryNames);
}
