package io.stockvaluation.repository;

import io.stockvaluation.domain.BondRating;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BondRatingRepository extends JpaRepository<BondRating, Long> {
}
