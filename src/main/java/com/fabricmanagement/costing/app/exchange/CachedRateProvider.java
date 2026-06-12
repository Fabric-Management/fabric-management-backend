package com.fabricmanagement.costing.app.exchange;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateProvider;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Serves any active cached rate regardless of source. MANUAL entries win by insert-precedence. */
@Component
@Order(10)
@RequiredArgsConstructor
public class CachedRateProvider implements ExchangeRateProvider {

  private final ExchangeRateCacheRepository cacheRepo;

  @Override
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    // Same currency → 1.0
    if (from.equalsIgnoreCase(to)) {
      return Optional.of(BigDecimal.ONE);
    }

    // Exact date only
    return cacheRepo
        .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, from, to, date)
        .map(ExchangeRateCache::getRate);
  }
}
