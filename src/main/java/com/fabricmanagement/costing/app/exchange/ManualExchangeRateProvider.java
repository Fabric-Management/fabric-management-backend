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

@Component
@Order(1)
@RequiredArgsConstructor
public class ManualExchangeRateProvider implements ExchangeRateProvider {

  private final ExchangeRateCacheRepository cacheRepo;

  @Override
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    // Aynı birim → 1.0
    if (from.equalsIgnoreCase(to)) {
      return Optional.of(BigDecimal.ONE);
    }

    // 1. Exact date
    Optional<ExchangeRateCache> exact =
        cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, from, to, date);
    if (exact.isPresent()) {
      return Optional.of(exact.get().getRate());
    }

    // 2. En yakın önceki tarih (max 7 gün geriye — bounded at DB level)
    LocalDate cutoff = date.minusDays(7);
    return cacheRepo
        .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateBetweenAndIsActiveTrueOrderByRateDateDesc(
            tenantId, from, to, cutoff, date)
        .map(ExchangeRateCache::getRate);
  }
}
