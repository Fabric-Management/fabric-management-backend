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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

  private final List<ExchangeRateProvider> rateProviders;
  private final ExchangeRateCacheRepository cacheRepo;

  @Value("${costing.fx.max-stale-days:7}")
  private int maxStaleDays = 7;

  record RateResult(BigDecimal rate, LocalDate rateDate) {}

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
   * If not found exactly, falls back to stale cache lookup.
   */
  public Optional<BigDecimal> getRate(UUID tenantId, String from, String to, LocalDate date) {
    return getRateWithDate(tenantId, from, to, date).map(RateResult::rate);
  }

  private Optional<RateResult> getRateWithDate(
      UUID tenantId, String from, String to, LocalDate date) {
    // 1. Exact date lookup through providers
    for (ExchangeRateProvider provider : rateProviders) {
      Optional<BigDecimal> rate = provider.getRate(tenantId, from, to, date);
      if (rate.isPresent()) {
        return Optional.of(new RateResult(rate.get(), date));
      }
    }

    // 2. Generic stale-rate fallback (cache lookup)
    LocalDate cutoff = date.minusDays(maxStaleDays);
    Optional<ExchangeRateCache> staleCache =
        cacheRepo
            .findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateBetweenAndIsActiveTrueOrderByRateDateDesc(
                tenantId, from, to, cutoff, date);

    if (staleCache.isPresent()) {
      ExchangeRateCache cache = staleCache.get();
      log.info(
          "Serving stale rate for {}/{} — requested {} but using {} rate",
          from,
          to,
          date,
          cache.getRateDate());
      return Optional.of(new RateResult(cache.getRate(), cache.getRateDate()));
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

    RateResult result =
        getRateWithDate(tenantId, originalCurrency, targetCurrency, date)
            .orElseThrow(
                () -> new ExchangeRateRequiredException(originalCurrency, targetCurrency, date));

    BigDecimal rate = result.rate();
    BigDecimal convertedAmount = originalAmount.multiply(rate).setScale(4, RoundingMode.HALF_UP);

    return ConvertedMoney.of(
        originalAmount, originalCurrency, convertedAmount, targetCurrency, rate, result.rateDate());
  }

  /**
   * Saves (upserts) the rate to cache and automatically creates the reverse rate.
   *
   * <p>Unlike provider-level writes (which skip if an active row exists to respect MANUAL
   * precedence), this is an explicit user/admin action that intentionally overwrites any existing
   * rate for the same (tenant, pair, date).
   */
  @Transactional
  public void saveRate(
      String baseCurrency,
      String targetCurrency,
      BigDecimal rate,
      LocalDate date,
      ExchangeRateSource source) {
    UUID tenantId = TenantContext.requireTenantId();

    // Forward rate: USD → GBP = 0.81
    saveOrUpdate(tenantId, baseCurrency, targetCurrency, rate, date, source);

    // Reverse rate: GBP → USD = 1/0.81
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
