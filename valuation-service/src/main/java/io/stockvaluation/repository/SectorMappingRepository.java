package io.stockvaluation.repository;

import io.stockvaluation.domain.SectorMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SectorMappingRepository extends JpaRepository<SectorMapping, Long> {

    @Query(value = "Select * from sector_mapping where yahoo_industry_key =:industryName", nativeQuery = true)
    public SectorMapping findByIndustryName(String industryName);
}
