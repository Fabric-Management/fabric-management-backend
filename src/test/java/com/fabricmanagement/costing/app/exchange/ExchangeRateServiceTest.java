package com.fabricmanagement.costing.app.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.domain.exception.ExchangeRateRequiredException;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateProvider;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExchangeRateServiceTest {

  @Mock private ExchangeRateProvider rateProvider;
  @Mock private ExchangeRateCacheRepository cacheRepo;

  private ExchangeRateService service;

  @Captor private ArgumentCaptor<ExchangeRateCache> cacheCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private MockedStatic<TenantContext> tenantContextMock;

  @BeforeEach
  void setUp() {
    tenantContextMock = mockStatic(TenantContext.class);
    tenantContextMock.when(TenantContext::requireTenantId).thenReturn(tenantId);
    service = new ExchangeRateService(List.of(rateProvider), cacheRepo);
  }

  @AfterEach
  void tearDown() {
    tenantContextMock.close();
  }

  // ─── convert() ────────────────────────────────────────

  @Test
  void convert_SameCurrency_ShouldReturnSameUnit() {
    BigDecimal amount = new BigDecimal("100.50");
    LocalDate date = LocalDate.now();

    ConvertedMoney result = service.convert(amount, "USD", "USD", date);

    assertThat(result.getOriginalAmount()).isEqualTo(amount);
    assertThat(result.getOriginalCurrency()).isEqualTo("USD");
    assertThat(result.getConvertedAmount()).isEqualTo(amount);
    assertThat(result.getConvertedCurrency()).isEqualTo("USD");
    assertThat(result.getExchangeRate()).isEqualTo(BigDecimal.ONE);
    // Provider should not be called
    verify(rateProvider, never()).getRate(any(), any(), any(), any());
  }

  @Test
  void convert_DifferentCurrencyAndRateFound_ShouldReturnConvertedMoney() {
    BigDecimal amount = new BigDecimal("100");
    LocalDate date = LocalDate.now();
    BigDecimal rate = new BigDecimal("38.50");

    when(rateProvider.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.of(rate));

    ConvertedMoney result = service.convert(amount, "USD", "TRY", date);

    assertThat(result.getOriginalAmount()).isEqualTo(amount);
    assertThat(result.getOriginalCurrency()).isEqualTo("USD");
    // 100 * 38.50 = 3850.0000 (scale 4)
    assertThat(result.getConvertedAmount()).isEqualByComparingTo("3850.0000");
    assertThat(result.getConvertedCurrency()).isEqualTo("TRY");
    assertThat(result.getExchangeRate()).isEqualTo(rate);
    assertThat(result.getRateDate()).isEqualTo(date);
  }

  @Test
  void convert_RateNotFound_ShouldThrowException() {
    BigDecimal amount = new BigDecimal("100");
    LocalDate date = LocalDate.now();

    when(rateProvider.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.convert(amount, "USD", "TRY", date))
        .isInstanceOf(ExchangeRateRequiredException.class)
        .hasMessageContaining("USD")
        .hasMessageContaining("TRY");
  }

  @Test
  void convertWithExplicitTenantId_ShouldPassTenantIdToProvider() {
    BigDecimal amount = new BigDecimal("100");
    LocalDate date = LocalDate.now();
    UUID explicitTenantId = UUID.randomUUID();
    BigDecimal rate = new BigDecimal("38.50");

    when(rateProvider.getRate(explicitTenantId, "USD", "TRY", date)).thenReturn(Optional.of(rate));

    ConvertedMoney result = service.convert(explicitTenantId, amount, "USD", "TRY", date);

    assertThat(result.getConvertedAmount()).isEqualByComparingTo("3850.0000");
    verify(rateProvider).getRate(explicitTenantId, "USD", "TRY", date);
  }

  // ─── getRequiredRate() ────────────────────────────────

  @Test
  void getRequiredRate_RateFound_ShouldReturnRate() {
    LocalDate date = LocalDate.now();
    BigDecimal rate = new BigDecimal("38.50");
    when(rateProvider.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.of(rate));

    BigDecimal result = service.getRequiredRate("USD", "TRY", date);

    assertThat(result).isEqualTo(rate);
  }

  // ─── saveRate() ───────────────────────────────────────

  @Test
  void saveRate_ShouldSaveForwardAndReverseRates() {
    LocalDate date = LocalDate.now();
    BigDecimal forwardRate = new BigDecimal("38.50");

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.empty());
    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "TRY", "USD", date))
        .thenReturn(Optional.empty());

    service.saveRate("USD", "TRY", forwardRate, date, ExchangeRateSource.MANUAL);

    verify(cacheRepo, times(2)).save(cacheCaptor.capture());

    var savedCaches = cacheCaptor.getAllValues();
    assertThat(savedCaches).hasSize(2);

    ExchangeRateCache forwardCache = savedCaches.get(0);
    assertThat(forwardCache.getBaseCurrency()).isEqualTo("USD");
    assertThat(forwardCache.getTargetCurrency()).isEqualTo("TRY");
    assertThat(forwardCache.getRate()).isEqualTo(forwardRate);
    assertThat(forwardCache.getSource()).isEqualTo(ExchangeRateSource.MANUAL);

    ExchangeRateCache reverseCache = savedCaches.get(1);
    assertThat(reverseCache.getBaseCurrency()).isEqualTo("TRY");
    assertThat(reverseCache.getTargetCurrency()).isEqualTo("USD");
    // 1 / 38.50 = 0.025974 (scale 6 with HALF_UP)
    assertThat(reverseCache.getRate()).isEqualByComparingTo("0.025974");
    assertThat(reverseCache.getSource()).isEqualTo(ExchangeRateSource.MANUAL);
  }

  @Test
  void saveRate_ExistingRate_ShouldUpdate() {
    LocalDate date = LocalDate.now();
    BigDecimal newForwardRate = new BigDecimal("38.50");

    ExchangeRateCache existingForward = ExchangeRateCache.builder().build();
    existingForward.setRate(new BigDecimal("38.00"));
    existingForward.setSource(ExchangeRateSource.MANUAL);

    ExchangeRateCache existingReverse = ExchangeRateCache.builder().build();
    existingReverse.setRate(new BigDecimal("0.026315"));
    existingReverse.setSource(ExchangeRateSource.MANUAL);

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(existingForward));
    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "TRY", "USD", date))
        .thenReturn(Optional.of(existingReverse));

    service.saveRate("USD", "TRY", newForwardRate, date, ExchangeRateSource.ECB);

    verify(cacheRepo, times(2)).save(cacheCaptor.capture());

    var savedCaches = cacheCaptor.getAllValues();
    assertThat(savedCaches).hasSize(2);

    assertThat(savedCaches.get(0).getRate()).isEqualTo(newForwardRate);
    assertThat(savedCaches.get(0).getSource()).isEqualTo(ExchangeRateSource.ECB);

    assertThat(savedCaches.get(1).getRate()).isEqualByComparingTo("0.025974");
    assertThat(savedCaches.get(1).getSource()).isEqualTo(ExchangeRateSource.ECB);
  }

  // ─── Chain of Responsibility behavior ─────────────────

  @Nested
  class ChainBehaviorTests {

    @Mock private ExchangeRateProvider provider1;
    @Mock private ExchangeRateProvider provider2;

    @Test
    void getRate_FirstProviderReturnsRate_ShouldNotCallSecond() {
      ExchangeRateService chainService =
          new ExchangeRateService(List.of(provider1, provider2), cacheRepo);
      LocalDate date = LocalDate.now();
      BigDecimal rate = new BigDecimal("38.50");

      when(provider1.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.of(rate));

      Optional<BigDecimal> result = chainService.getRate(tenantId, "USD", "TRY", date);

      assertThat(result).isPresent().contains(rate);
      verify(provider1).getRate(tenantId, "USD", "TRY", date);
      verify(provider2, never()).getRate(any(), any(), any(), any());
    }

    @Test
    void getRate_FirstProviderEmpty_ShouldFallToSecond() {
      ExchangeRateService chainService =
          new ExchangeRateService(List.of(provider1, provider2), cacheRepo);
      LocalDate date = LocalDate.now();
      BigDecimal rate = new BigDecimal("38.50");

      when(provider1.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.empty());
      when(provider2.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.of(rate));

      Optional<BigDecimal> result = chainService.getRate(tenantId, "USD", "TRY", date);

      assertThat(result).isPresent().contains(rate);
      verify(provider1).getRate(tenantId, "USD", "TRY", date);
      verify(provider2).getRate(tenantId, "USD", "TRY", date);
    }

    @Test
    void getRate_AllProvidersEmpty_ShouldReturnEmpty() {
      ExchangeRateService chainService =
          new ExchangeRateService(List.of(provider1, provider2), cacheRepo);
      LocalDate date = LocalDate.now();

      when(provider1.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.empty());
      when(provider2.getRate(tenantId, "USD", "TRY", date)).thenReturn(Optional.empty());

      Optional<BigDecimal> result = chainService.getRate(tenantId, "USD", "TRY", date);

      assertThat(result).isEmpty();
    }
  }
}
