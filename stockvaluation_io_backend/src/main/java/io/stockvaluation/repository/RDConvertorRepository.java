package io.stockvaluation.repository;

import io.stockvaluation.domain.RDConvertor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RDConvertorRepository extends JpaRepository<RDConvertor, Long> {

    @Query(value = "Select * from rdconvertor where industry_name =:industryName", nativeQuery = true)
    RDConvertor findAmortizationPeriod(String industryName);
}
