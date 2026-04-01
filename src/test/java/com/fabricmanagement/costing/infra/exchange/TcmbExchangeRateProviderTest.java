package com.fabricmanagement.costing.infra.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TcmbExchangeRateProviderTest {

  @Mock private ExchangeRateCacheRepository cacheRepo;

  @InjectMocks private TcmbExchangeRateProvider provider;

  @Captor private ArgumentCaptor<ExchangeRateCache> cacheCaptor;

  private final UUID tenantId = UUID.randomUUID();

  // ─── getRate() ────────────────────────────────────────

  @Test
  void getRate_SameCurrency_ShouldReturnOne() {
    Optional<BigDecimal> rate = provider.getRate(tenantId, "USD", "USD", LocalDate.now());
    assertThat(rate).isPresent().contains(BigDecimal.ONE);
  }

  // ─── buildTcmbUrl() — always date-based, no today.xml ────

  @Test
  void buildTcmbUrl_ShouldAlwaysUseDateBasedUrl() {
    LocalDate today = LocalDate.of(2026, 4, 1);
    String url = provider.buildTcmbUrl(today);
    assertThat(url).isEqualTo("https://www.tcmb.gov.tr/kurlar/202604/01042026.xml");
    assertThat(url).doesNotContain("today.xml");
  }

  @Test
  void buildTcmbUrl_HistoricalDate_ShouldFormatCorrectly() {
    LocalDate date = LocalDate.of(2025, 12, 25);
    String url = provider.buildTcmbUrl(date);
    assertThat(url).isEqualTo("https://www.tcmb.gov.tr/kurlar/202512/25122025.xml");
  }

  // ─── calculateCrossRate() ──────────────────────────────

  @Test
  void calculateCrossRate_DirectToTRY_ShouldReturnRate() {
    Map<String, BigDecimal> dayRates =
        Map.of("USD", new BigDecimal("38.5432"), "TRY", BigDecimal.ONE);

    Optional<BigDecimal> rate = provider.calculateCrossRate("USD", "TRY", dayRates);

    // USD->TRY = 38.5432 / 1 = 38.543200
    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("38.543200");
  }

  @Test
  void calculateCrossRate_CrossPair_ShouldCalculateCorrectly() {
    Map<String, BigDecimal> dayRates =
        Map.of(
            "USD", new BigDecimal("38.5432"),
            "EUR", new BigDecimal("42.1234"),
            "TRY", BigDecimal.ONE);

    Optional<BigDecimal> rate = provider.calculateCrossRate("USD", "EUR", dayRates);

    // USD->EUR = 38.5432 / 42.1234 = 0.915... (scale 6)
    assertThat(rate).isPresent();
    BigDecimal expected =
        new BigDecimal("38.5432").divide(new BigDecimal("42.1234"), 6, RoundingMode.HALF_UP);
    assertThat(rate.get()).isEqualByComparingTo(expected);
  }

  @Test
  void calculateCrossRate_MissingCurrency_ShouldReturnEmpty() {
    Map<String, BigDecimal> dayRates =
        Map.of("USD", new BigDecimal("38.5432"), "TRY", BigDecimal.ONE);

    Optional<BigDecimal> rate = provider.calculateCrossRate("USD", "GBP", dayRates);
    assertThat(rate).isEmpty();
  }

  // ─── saveToAuditCache() — package-private, no reflection ──

  @Test
  void saveToAuditCache_NewRate_ShouldInsertWithTCMBSource() {
    LocalDate date = LocalDate.now();
    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.empty());

    provider.saveToAuditCache(tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    verify(cacheRepo).save(cacheCaptor.capture());
    ExchangeRateCache saved = cacheCaptor.getValue();

    assertThat(saved.getBaseCurrency()).isEqualTo("USD");
    assertThat(saved.getTargetCurrency()).isEqualTo("TRY");
    assertThat(saved.getRate()).isEqualTo(new BigDecimal("38.50"));
    assertThat(saved.getSource()).isEqualTo(ExchangeRateSource.TCMB);
    assertThat(saved.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void saveToAuditCache_ExistingTCMBRate_ShouldUpdate() {
    LocalDate date = LocalDate.now();
    ExchangeRateCache existing = ExchangeRateCache.builder().build();
    existing.setSource(ExchangeRateSource.TCMB);
    existing.setRate(new BigDecimal("37.00"));

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(existing));

    provider.saveToAuditCache(tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    verify(cacheRepo).save(cacheCaptor.capture());
    assertThat(cacheCaptor.getValue().getRate()).isEqualByComparingTo("38.50");
  }

  @Test
  void saveToAuditCache_ExistingAnySourceRate_ShouldStillUpdate() {
    // Bu provider'a sıra geldiğinde, Manual provider cache'te kayıt bulamamıştır.
    // Dolayısıyla existing kayıt varsa bile, TCMB güncelleme yapabilir.
    LocalDate date = LocalDate.now();
    ExchangeRateCache existing = ExchangeRateCache.builder().build();
    existing.setSource(ExchangeRateSource.MANUAL);
    existing.setRate(new BigDecimal("99.99"));

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(existing));

    provider.saveToAuditCache(tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    // Artık güncelleme yapılır — dead code guard kaldırıldı
    verify(cacheRepo).save(any());
  }
}
