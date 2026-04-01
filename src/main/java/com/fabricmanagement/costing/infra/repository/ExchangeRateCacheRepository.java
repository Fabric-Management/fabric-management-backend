package com.fabricmanagement.costing.infra.repository;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExchangeRateCacheRepository extends JpaRepository<ExchangeRateCache, UUID> {

  /** Exact date match */
  Optional<ExchangeRateCache>
      findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
          UUID tenantId, String baseCurrency, String targetCurrency, LocalDate rateDate);

  /** Nearest previous date within bounded range [cutoffDate, rateDate] — DB-level efficient */
  Optional<ExchangeRateCache>
      findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateBetweenAndIsActiveTrueOrderByRateDateDesc(
          UUID tenantId,
          String baseCurrency,
          String targetCurrency,
          LocalDate cutoffDate,
          LocalDate rateDate);
}
