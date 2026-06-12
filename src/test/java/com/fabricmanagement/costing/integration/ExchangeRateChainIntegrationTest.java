package com.fabricmanagement.costing.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantReportingCurrencyPort;
import com.fabricmanagement.costing.app.exchange.ExchangeRateService;
import com.fabricmanagement.costing.domain.exchange.ExchangeRateCache;
import com.fabricmanagement.costing.infra.exchange.TcmbExchangeRateProvider;
import com.fabricmanagement.costing.infra.repository.ExchangeRateCacheRepository;
import com.fabricmanagement.costing.integration.support.TestCostDataFactory;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Validates the Exchange Rate chain logic and database operations. TCMB XML behavior is isolated
 * and mocked via @MockBean.
 */
@DisplayName("Exchange Rate Provider Chain Integration")
class ExchangeRateChainIntegrationTest
    extends com.fabricmanagement.costing.integration.AbstractCostingIntegrationTest {

  @Autowired private ExchangeRateService exchangeRateService;
  @Autowired private ExchangeRateCacheRepository cacheRepo;
  @MockBean private TenantReportingCurrencyPort tenantReportingCurrencyPort;

  // Mock TCMB Provider (Order 2) so we don't do real HTTP requests
  @MockBean private TcmbExchangeRateProvider tcmbProvider;

  @BeforeEach
  void setUp() {
    // Clear tenant context before tests just in case
    TenantContext.clear();
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("Chain Resolution Behavior")
  class ChainResolutionTests {

    @Test
    @DisplayName("Manual Provider successfully overrides TCMB Provider in the chain")
    void manualProvider_overridesTcmb() {
      UUID tenantId = UUID.randomUUID();
      TenantContext.setCurrentTenantId(tenantId);
      LocalDate today = LocalDate.now();

      // Seed a Manual rate in the database (this is picked up by CachedRateProvider)
      ExchangeRateCache manualRate =
          TestCostDataFactory.createRate(
              tenantId,
              "USD",
              "TRY",
              new BigDecimal("35.00"),
              today,
              com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL);
      cacheRepo.save(manualRate);

      // Verify that the chain returns 35.00
      Optional<BigDecimal> result = exchangeRateService.getRate("USD", "TRY", today);
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualByComparingTo(new BigDecimal("35.00"));

      // Verify TCMB provider was never called because Manual returned a value
      verify(tcmbProvider, never()).getRate(any(), any(), any(), any());
    }

    @Test
    @DisplayName("TCMB is called if Manual Provider finds nothing")
    void noManual_tcmbIsCalled() {
      UUID tenantId = UUID.randomUUID();
      LocalDate today = LocalDate.now();

      // Setup TCMB Mock to return 36.50
      when(tcmbProvider.getRate(tenantId, "EUR", "TRY", today))
          .thenReturn(Optional.of(new BigDecimal("36.50")));

      Optional<BigDecimal> result = exchangeRateService.getRate(tenantId, "EUR", "TRY", today);
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualByComparingTo(new BigDecimal("36.50"));

      // Verify TCMB provider WAS called
      verify(tcmbProvider, times(1)).getRate(tenantId, "EUR", "TRY", today);
    }

    @Test
    @DisplayName("Same currency bypasses all providers and returns 1.0")
    void sameCurrency_returnsOne() {
      UUID tenantId = UUID.randomUUID();
      LocalDate today = LocalDate.now();

      Optional<BigDecimal> result = exchangeRateService.getRate(tenantId, "USD", "USD", today);

      assertThat(result).isPresent();
      assertThat(result.get()).isEqualByComparingTo(BigDecimal.ONE);
      verify(tcmbProvider, never()).getRate(any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Save Rate Operations")
  class SaveRateTests {

    @Test
    @DisplayName("Saving a rate stores both forward and reverse rates in DB")
    void saveRate_generatesReverseAndIsIdempotent() {
      UUID tenantId = UUID.randomUUID();
      TenantContext.setCurrentTenantId(tenantId);
      LocalDate date = LocalDate.now();

      // Action: save forward rate = 40.00
      exchangeRateService.saveRate(
          "GBP",
          "TRY",
          new BigDecimal("40.00"),
          date,
          com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL);

      // Asserts
      var forward =
          cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
              tenantId, "GBP", "TRY", date);
      assertThat(forward).isPresent();
      assertThat(forward.get().getRate()).isEqualByComparingTo("40.00");
      assertThat(forward.get().getSource())
          .isEqualTo(com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL);

      var reverse =
          cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
              tenantId, "TRY", "GBP", date);
      assertThat(reverse).isPresent();
      // Reverse is 1/40 = 0.025
      assertThat(reverse.get().getRate()).isEqualByComparingTo("0.0250");
      assertThat(reverse.get().getSource())
          .isEqualTo(com.fabricmanagement.costing.domain.exchange.ExchangeRateSource.MANUAL);
    }
  }
}
