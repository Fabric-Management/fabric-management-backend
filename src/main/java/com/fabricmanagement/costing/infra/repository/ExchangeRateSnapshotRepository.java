package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.currency.ExchangeRateSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** JPA repository for {@link ExchangeRateSnapshot}. */
public interface ExchangeRateSnapshotRepository extends JpaRepository<ExchangeRateSnapshot, UUID> {

  /** Latest snapshot for a currency pair regardless of source. */
  @Query(
      """
      SELECT e FROM ExchangeRateSnapshot e
      WHERE e.baseCurrency = :base AND e.targetCurrency = :target
      ORDER BY e.capturedAt DESC
      """)
  Optional<ExchangeRateSnapshot> findLatest(
      @Param("base") String base, @Param("target") String target);
}
