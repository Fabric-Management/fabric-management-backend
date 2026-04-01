package com.fabricmanagement.costing.infra.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateSource;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
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

  @Test
  void getRate_SameCurrency_ShouldReturnOne() {
    Optional<BigDecimal> rate = provider.getRate(tenantId, "USD", "USD", LocalDate.now());
    assertThat(rate).isPresent().contains(BigDecimal.ONE);
  }

  // To test the HTTP parsing without real network, we'd normally mock the HttpClient.
  // We can at least test the cache fallback mechanism.

  @Test
  void saveToAuditCache_GivenNewRate_ShouldSaveAsTCMB() throws Exception {
    // We use reflection to test the private saveToAuditCache method to ensure DB write logic is
    // solid
    java.lang.reflect.Method method =
        TcmbExchangeRateProvider.class.getDeclaredMethod(
            "saveToAuditCache",
            UUID.class,
            String.class,
            String.class,
            BigDecimal.class,
            LocalDate.class);
    method.setAccessible(true);

    LocalDate date = LocalDate.now();
    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.empty());

    // Invoke inner private method
    method.invoke(provider, tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    verify(cacheRepo).save(cacheCaptor.capture());
    ExchangeRateCache saved = cacheCaptor.getValue();

    assertThat(saved.getBaseCurrency()).isEqualTo("USD");
    assertThat(saved.getTargetCurrency()).isEqualTo("TRY");
    assertThat(saved.getRate()).isEqualTo(new BigDecimal("38.50"));
    assertThat(saved.getSource()).isEqualTo(ExchangeRateSource.TCMB);
    assertThat(saved.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void saveToAuditCache_GivenManualOverride_ShouldNotOverwrite() throws Exception {
    java.lang.reflect.Method method =
        TcmbExchangeRateProvider.class.getDeclaredMethod(
            "saveToAuditCache",
            UUID.class,
            String.class,
            String.class,
            BigDecimal.class,
            LocalDate.class);
    method.setAccessible(true);

    LocalDate date = LocalDate.now();
    ExchangeRateCache existingManual = ExchangeRateCache.builder().build();
    existingManual.setSource(ExchangeRateSource.MANUAL);
    existingManual.setRate(new BigDecimal("99.99"));

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(existingManual));

    // Invoke inner private method
    method.invoke(provider, tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    // Should NOT save the new rate because it's MANUAL overridden
    verify(cacheRepo, org.mockito.Mockito.never()).save(any());
  }
}
