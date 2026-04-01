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
    // When this provider is reached, it means the Manual provider did not find a cache entry.
    // Therefore, even if an existing record exists, TCMB can update it.
    LocalDate date = LocalDate.now();
    ExchangeRateCache existing = ExchangeRateCache.builder().build();
    existing.setSource(ExchangeRateSource.MANUAL);
    existing.setRate(new BigDecimal("99.99"));

    when(cacheRepo.findFirstByTenantIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndIsActiveTrue(
            tenantId, "USD", "TRY", date))
        .thenReturn(Optional.of(existing));

    provider.saveToAuditCache(tenantId, "USD", "TRY", new BigDecimal("38.50"), date);

    // Update goes through — dead code guard was removed
    verify(cacheRepo).save(any());
  }

  // ─── parseXml() ───────────────────────────────────────────

  @Test
  void parseXml_ValidXml_ShouldParseRatesAndSkipXDR() throws Exception {
    String xml =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
            + "<Tarih_Date Tarih=\"01.04.2026\" Date=\"04/01/2026\"  Bulten_No=\"2026/63\">\n"
            + "    <Currency CrossOrder=\"0\" Kod=\"USD\" CurrencyCode=\"USD\">\n"
            + "        <Unit>1</Unit>\n"
            + "        <ForexBuying>32.1234</ForexBuying>\n"
            + "    </Currency>\n"
            + "    <Currency CrossOrder=\"1\" Kod=\"EUR\" CurrencyCode=\"EUR\">\n"
            + "        <Unit>1</Unit>\n"
            + "        <ForexBuying>35.5678</ForexBuying>\n"
            + "    </Currency>\n"
            + "    <Currency CrossOrder=\"2\" Kod=\"XDR\" CurrencyCode=\"XDR\">\n"
            + "        <Unit>1</Unit>\n"
            + "        <ForexBuying>41.0000</ForexBuying>\n"
            + "    </Currency>\n"
            + "    <Currency CrossOrder=\"3\" Kod=\"GBP\" CurrencyCode=\"GBP\">\n"
            + "        <Unit>1</Unit>\n"
            + "        <ForexBuying>  </ForexBuying>\n"
            + "    </Currency>\n"
            + "</Tarih_Date>";

    java.io.InputStream is =
        new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8));

    Map<String, BigDecimal> parsed = provider.parseXml(is);

    assertThat(parsed).containsEntry("TRY", BigDecimal.ONE);
    assertThat(parsed).containsEntry("USD", new BigDecimal("32.1234"));
    assertThat(parsed).containsEntry("EUR", new BigDecimal("35.5678"));
    assertThat(parsed).doesNotContainKey("XDR"); // Specially skipped
    assertThat(parsed).doesNotContainKey("GBP"); // Empty text skipped
  }
}
