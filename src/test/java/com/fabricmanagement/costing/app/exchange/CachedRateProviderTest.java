package com.fabricmanagement.costing.app.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CachedRateProviderTest {

  @Mock private ExchangeRateCacheRepository cacheRepo;

  @InjectMocks private CachedRateProvider provider;

  private final UUID tenantId = UUID.randomUUID();
  private MockedStatic<TenantContext> tenantContextMock;

  @BeforeEach
  void setUp() {
    tenantContextMock = mockStatic(TenantContext.class);
  }

  @AfterEach
  void tearDown() {
    tenantContextMock.close();
  }

  @Test
  void getRate_SameCurrency_ShouldReturnOne() {
    LocalDate date = LocalDate.now();

    Optional<BigDecimal> result = provider.getRate(tenantId, "USD", "usd", date);

    assertThat(result).isPresent().contains(BigDecimal.ONE);
  }

  @Test
  void getRate_ExactDateMatch_ShouldReturnRate() {
    LocalDate date = LocalDate.now();
    BigDecimal rate = new BigDecimal("38.50");
    ExchangeRateCache cache = ExchangeRateCache.builder().build();
    cache.setRate(rate);

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(cache));

    Optional<BigDecimal> result = provider.getRate(tenantId, "USD", "TRY", date);

    assertThat(result).isPresent().contains(rate);
  }

  @Test
  void getRate_NoRecordFound_ShouldReturnEmpty() {
    LocalDate date = LocalDate.now();

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.empty());

    Optional<BigDecimal> result = provider.getRate(tenantId, "USD", "TRY", date);

    assertThat(result).isEmpty();
  }
}
