package com.fabricmanagement.costing.app.exchange;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateProvider;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExchangeRateService {

  private final List<ExchangeRateProvider> rateProviders;
  private final ExchangeRateCacheRepository cacheRepo;

  /**
   * Fetches the rate. Throws ExchangeRateRequiredException if not found. Resolves tenantId from
   * TenantContext — convenience method for controller/facade layer.
   */
  public BigDecimal getRequiredRate(String from, String to, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return getRequiredRate(tenantId, from, to, date);
  }

  /** Fetches the rate with explicit tenantId. Throws ExchangeRateRequiredException if not found. */
  public BigDecimal getRequiredRate(UUID tenantId, String from, String to, LocalDate date) {
    return getRate(tenantId, from, to, date)
        .orElseThrow(() -> new ExchangeRateRequiredException(from, to, date));
  }

  /**
   * Iterates through the ordered list of providers — returns first rate found. Resolves tenantId
   * from TenantContext.
   */
  public Optional<BigDecimal> getRate(String from, String to, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return getRate(tenantId, from, to, date);
  }

  /**
   * Iterates through the ordered provider chain with explicit tenantId — returns first rate found.
   */
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    for (ExchangeRateProvider provider : rateProviders) {
      Optional<BigDecimal> rate = provider.getRate(tenantId, from, to, date);
      if (rate.isPresent()) {
        return rate;
      }
    }
    return Optional.empty();
  }

  /**
   * Creates a ConvertedMoney — main method used by CostCalculationService. Resolves tenantId from
   * TenantContext.
   */
  public ConvertedMoney convert(
      BigDecimal originalAmount, String originalCurrency, String targetCurrency, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return convert(tenantId, originalAmount, originalCurrency, targetCurrency, date);
  }

  /** Creates a ConvertedMoney with explicit tenantId. */
  public ConvertedMoney convert(
      UUID tenantId,
      BigDecimal originalAmount,
      String originalCurrency,
      String targetCurrency,
      LocalDate date) {

    if (originalCurrency.equalsIgnoreCase(targetCurrency)) {
      return ConvertedMoney.sameUnit(originalAmount, originalCurrency);
    }

    BigDecimal rate = getRequiredRate(tenantId, originalCurrency, targetCurrency, date);
    BigDecimal convertedAmount = originalAmount.multiply(rate).setScale(4, RoundingMode.HALF_UP);

    return ConvertedMoney.of(
        originalAmount, originalCurrency, convertedAmount, targetCurrency, rate, date);
  }

  /** Saves the rate to cache and automatically creates the reverse rate. */
  @Transactional
  public void saveRate(
      String baseCurrency,
      String targetCurrency,
      BigDecimal rate,
      LocalDate date,
      ExchangeRateSource source) {
    UUID tenantId = TenantContext.requireTenantId();

    // Forward rate: USD → TRY = 38.50
    saveOrUpdate(tenantId, baseCurrency, targetCurrency, rate, date, source);

    // Reverse rate: TRY → USD = 1/38.50
    BigDecimal reverseRate = BigDecimal.ONE.divide(rate, 6, RoundingMode.HALF_UP);
    saveOrUpdate(tenantId, targetCurrency, baseCurrency, reverseRate, date, source);
  }

  private void saveOrUpdate(
      UUID tenantId,
      String base,
      String target,
      BigDecimal rate,
      LocalDate date,
      ExchangeRateSource source) {
    ExchangeRateCache existing =
        cacheRepo
            .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
                tenantId, base, target, date)
            .orElse(null);

    if (existing != null) {
      existing.setRate(rate);
      existing.setSource(source);
      cacheRepo.save(existing);
    } else {
      ExchangeRateCache cache =
          ExchangeRateCache.builder()
              .baseCurrency(base)
              .targetCurrency(target)
              .rate(rate)
              .rateDate(date)
              .source(source)
              .build();
      cache.setTenantId(tenantId);
      cacheRepo.save(cache);
    }
  }
}
