package io.stockvaluation.repository;

import io.stockvaluation.domain.Input;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InputRepository extends JpaRepository<Input, Long> {

    @Query(value = "Select * from input where ticker =:ticker", nativeQuery = true)
    Input findByTicker(String ticker);
}
