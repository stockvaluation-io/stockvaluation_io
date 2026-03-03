package io.stockvaluation.repository;

import io.stockvaluation.domain.RDConverter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RDConverterRepository extends JpaRepository<RDConverter, Long> {

    @Query(value = "Select * from rdconvertor where industry_name =:industryName", nativeQuery = true)
    RDConverter findByIndustryName(String industryName);
}
