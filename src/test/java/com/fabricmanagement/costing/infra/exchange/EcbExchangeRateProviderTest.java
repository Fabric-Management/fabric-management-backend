package com.fabricmanagement.costing.infra.exchange;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcbExchangeRateProviderTest {

  @Mock private ExchangeRateCacheRepository cacheRepo;

  private EcbExchangeRateProvider provider;

  private final UUID tenantId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    // We override parseXml/buildUrl or mock HttpClient.
    // Spying the provider is simpler to mock just the fetchRatesForDate method
    // without spinning up a real HTTP server.
    provider = spy(new EcbExchangeRateProvider(cacheRepo, 5));
  }

  @Test
  void getRate_SameCurrency_ReturnsOne() {
    Optional<BigDecimal> rate = provider.getRate(tenantId, "GBP", "GBP", LocalDate.now());
    assertThat(rate).isPresent().contains(BigDecimal.ONE);
  }

  @Test
  void getRate_SuccessfulFetch_CalculatesCrossRateAndSaves() throws Exception {
    LocalDate date = LocalDate.of(2023, 10, 20);

    Map<String, BigDecimal> rates =
        Map.of(
            "EUR", BigDecimal.ONE,
            "USD", new BigDecimal("1.0596"),
            "GBP", new BigDecimal("0.87123"));

    // We override fetchRatesForDate
    doReturn(rates).when(provider).fetchRatesForDate(date);

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            any(), any(), any(), any()))
        .thenReturn(Optional.empty());

    // Cross rate GBP -> USD = EUR->USD / EUR->GBP = 1.0596 / 0.87123 = 1.216212
    Optional<BigDecimal> result = provider.getRate(tenantId, "GBP", "USD", date);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("1.216212");

    // Verify it saved
    ArgumentCaptor<ExchangeRateCache> captor = ArgumentCaptor.forClass(ExchangeRateCache.class);
    verify(cacheRepo).save(captor.capture());
    ExchangeRateCache saved = captor.getValue();
    assertThat(saved.getBaseCurrency()).isEqualTo("GBP");
    assertThat(saved.getTargetCurrency()).isEqualTo("USD");
    assertThat(saved.getRate()).isEqualByComparingTo("1.216212");
  }

  @Test
  void getRate_ExistingActiveRate_SkipsSave() {
    LocalDate date = LocalDate.of(2023, 10, 20);
    Map<String, BigDecimal> rates =
        Map.of(
            "EUR", BigDecimal.ONE,
            "USD", new BigDecimal("1.0596"),
            "GBP", new BigDecimal("0.87123"));

    doReturn(rates).when(provider).fetchRatesForDate(date);

    // Existing active rate
    ExchangeRateCache existing = ExchangeRateCache.builder().build();
    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "GBP", "USD", date))
        .thenReturn(Optional.of(existing));

    Optional<BigDecimal> result = provider.getRate(tenantId, "GBP", "USD", date);

    assertThat(result).isPresent();
    verify(cacheRepo, never()).save(any());
  }

  @Test
  void getRate_FetchFailure_ReturnsEmpty() {
    LocalDate date = LocalDate.now();
    doReturn(Map.of()).when(provider).fetchRatesForDate(date);

    Optional<BigDecimal> result = provider.getRate(tenantId, "GBP", "USD", date);

    assertThat(result).isEmpty();
  }
}
