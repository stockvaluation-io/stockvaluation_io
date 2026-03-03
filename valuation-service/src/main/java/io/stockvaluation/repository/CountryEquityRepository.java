package io.stockvaluation.repository;

import io.stockvaluation.domain.CountryEquity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CountryEquityRepository extends JpaRepository<CountryEquity, Long> {

    @Query("SELECT c.corporateTaxRate FROM CountryEquity c WHERE c.country = :country")
    Optional<Double> findCorporateTaxRateByCountry(String country);

    @Query(value = "select * from country_equity where country =:country", nativeQuery = true)
    CountryEquity findDefaultSpread(String country);

    @Query("SELECT (c.equityRiskPremium - c.countryRiskPremium) FROM CountryEquity c WHERE c.country = :country")
    Optional<Double> findMatureMarketPremiumByCountry(String country);

}
