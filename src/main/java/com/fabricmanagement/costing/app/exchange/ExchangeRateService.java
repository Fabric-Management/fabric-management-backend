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
   * Kuru çeker. Bulamazsa ExchangeRateRequiredException fırlatır. TenantContext'ten tenantId
   * çözümler — controller/facade katmanından çağrılacak convenience metod.
   */
  public BigDecimal getRequiredRate(String from, String to, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return getRequiredRate(tenantId, from, to, date);
  }

  /** Explicit tenantId ile kur çeker. Bulamazsa ExchangeRateRequiredException fırlatır. */
  public BigDecimal getRequiredRate(UUID tenantId, String from, String to, LocalDate date) {
    return getRate(tenantId, from, to, date)
        .orElseThrow(() -> new ExchangeRateRequiredException(from, to, date));
  }

  /**
   * Iterates through the ordered list of providers — returns first rate found. TenantContext'ten
   * tenantId çözümler.
   */
  public Optional<BigDecimal> getRate(String from, String to, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return getRate(tenantId, from, to, date);
  }

  /** Explicit tenantId ile ordered provider chain'i tarar — ilk bulunan rate döner. */
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
   * ConvertedMoney oluşturur — CostCalculationService'in kullanacağı ana metod. TenantContext'ten
   * tenantId çözümler.
   */
  public ConvertedMoney convert(
      BigDecimal originalAmount, String originalCurrency, String targetCurrency, LocalDate date) {
    UUID tenantId = TenantContext.requireTenantId();
    return convert(tenantId, originalAmount, originalCurrency, targetCurrency, date);
  }

  /** Explicit tenantId ile ConvertedMoney oluşturur. */
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

  /** Kuru cache'e yazar + reverse rate'i de otomatik oluşturur. */
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
