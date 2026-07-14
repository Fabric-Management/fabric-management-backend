package com.fabricmanagement.sales.quote.infra.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.fabricmanagement.common.infrastructure.persistence.LikePattern;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.QuoteResponse;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Quote Repository Integration Test")
class QuoteRepositoryIntegrationTest extends AbstractIntegrationTest {

  @Autowired private QuoteRepository quoteRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID customerId = UUID.randomUUID();
  private final UUID assignedToId = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(assignedToId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("find by id initializes lines for detached QuoteResponse mapping")
  void findByTenantIdAndIdAndIsActiveTrue_initializesLinesForResponseMapping() {
    UUID quoteId = persistQuoteWithLine();

    Quote quote =
        quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId).orElseThrow();

    QuoteResponse response = assertDoesNotThrow(() -> QuoteResponse.from(quote));

    assertThat(response.getLines()).hasSize(1);
    assertThat(response.getLines().get(0).getProductDesc()).isEqualTo("Organic cotton jersey");
  }

  @Test
  @DisplayName("paged find all initializes lines for detached QuoteResponse mapping")
  void findAllByTenantIdAndIsActiveTrue_initializesLinesForResponseMapping() {
    persistQuoteWithLine();

    Page<Quote> quotes =
        quoteRepository.findAllByTenantIdAndIsActiveTrue(tenantId, PageRequest.of(0, 10));

    assertThat(quotes.getContent()).hasSize(1);
    QuoteResponse response =
        assertDoesNotThrow(() -> QuoteResponse.from(quotes.getContent().get(0)));

    assertThat(response.getLines()).hasSize(1);
    assertThat(response.getLines().get(0).getRequestedQty())
        .isEqualByComparingTo(new BigDecimal("12.500"));
  }

  @Test
  @DisplayName("search is tenant-scoped, active-only, status-aware, and treats wildcards literally")
  void searchByQuoteNumber_appliesAllGuardsAndLiteralContains() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    Quote expected = quote(tenantId, "QT-50%_-" + suffix, customerId, QuoteStatus.DRAFT, true);
    UUID expectedId = persist(expected);
    persist(quote(tenantId, "QT-50AB-" + suffix, customerId, QuoteStatus.DRAFT, true));
    persist(quote(tenantId, "QT-50%_-inactive-" + suffix, customerId, QuoteStatus.DRAFT, false));
    persist(
        quote(UUID.randomUUID(), "QT-50%_-other-" + suffix, customerId, QuoteStatus.DRAFT, true));
    persist(quote(tenantId, "QT-50%_-approved-" + suffix, customerId, QuoteStatus.APPROVED, true));

    Page<Quote> result =
        quoteRepository.searchByQuoteNumber(
            tenantId,
            QuoteStatus.DRAFT,
            LikePattern.literalContains("50%_"),
            LikePattern.ESCAPE_CHARACTER,
            PageRequest.of(0, 20));

    assertThat(result.getContent()).extracting(Quote::getId).containsExactly(expectedId);
  }

  @Test
  @DisplayName("customer IDs and quote number compose with status in one paged search")
  void searchByQuoteNumberOrCustomerId_composesWithStatus() {
    UUID matchedCustomerId = UUID.randomUUID();
    UUID expectedId =
        persist(
            quote(
                tenantId,
                "QT-CUSTOMER-" + UUID.randomUUID(),
                matchedCustomerId,
                QuoteStatus.DRAFT,
                true));
    persist(
        quote(
            tenantId,
            "QT-CUSTOMER-" + UUID.randomUUID(),
            matchedCustomerId,
            QuoteStatus.APPROVED,
            true));

    Page<Quote> result =
        quoteRepository.searchByQuoteNumberOrCustomerId(
            tenantId,
            QuoteStatus.DRAFT,
            LikePattern.literalContains("no-number-match"),
            LikePattern.ESCAPE_CHARACTER,
            java.util.List.of(matchedCustomerId),
            PageRequest.of(0, 20));

    assertThat(result.getContent()).extracting(Quote::getId).containsExactly(expectedId);
  }

  @Test
  @DisplayName("status counts are tenant-scoped and active-only")
  void countActiveByStatus_isTenantScopedAndActiveOnly() {
    persist(quote(tenantId, "QT-COUNT-" + UUID.randomUUID(), customerId, QuoteStatus.DRAFT, true));
    persist(
        quote(tenantId, "QT-COUNT-" + UUID.randomUUID(), customerId, QuoteStatus.EXPIRED, true));
    persist(quote(tenantId, "QT-COUNT-" + UUID.randomUUID(), customerId, QuoteStatus.DRAFT, false));
    persist(
        quote(
            UUID.randomUUID(),
            "QT-COUNT-" + UUID.randomUUID(),
            customerId,
            QuoteStatus.DRAFT,
            true));

    Map<QuoteStatus, Long> counts =
        quoteRepository.countActiveByStatus(tenantId).stream()
            .collect(
                Collectors.toMap(
                    QuoteRepository.StatusCountProjection::getStatus,
                    QuoteRepository.StatusCountProjection::getCount));

    assertThat(counts).containsEntry(QuoteStatus.DRAFT, 1L).containsEntry(QuoteStatus.EXPIRED, 1L);
    assertThat(counts).hasSize(2);
  }

  private UUID persistQuoteWithLine() {
    return transactionTemplate.execute(
        status -> {
          Quote saved = quoteRepository.saveAndFlush(quoteWithLine());
          return saved.getId();
        });
  }

  private UUID persist(Quote quote) {
    return transactionTemplate.execute(status -> quoteRepository.saveAndFlush(quote).getId());
  }

  private Quote quote(
      UUID quoteTenantId,
      String quoteNumber,
      UUID quoteCustomerId,
      QuoteStatus status,
      boolean active) {
    Quote quote = new Quote();
    quote.setTenantId(quoteTenantId);
    quote.setQuoteNumber(quoteNumber);
    quote.setCustomerId(quoteCustomerId);
    quote.setAssignedToId(assignedToId);
    quote.setModuleType("FABRIC");
    quote.setStatus(status);
    quote.setCurrency("GBP");
    quote.setValidUntil(LocalDate.now().plusDays(30));
    quote.setPaymentTerms("NET_30");
    quote.setActive(active);
    return quote;
  }

  private Quote quoteWithLine() {
    Quote quote = new Quote();
    quote.setTenantId(tenantId);
    quote.setQuoteNumber("QT-LAZY-" + UUID.randomUUID());
    quote.setCustomerId(customerId);
    quote.setAssignedToId(assignedToId);
    quote.setModuleType("FABRIC");
    quote.setCurrency("GBP");
    quote.setValidUntil(LocalDate.now().plusDays(30));
    quote.setPaymentTerms("NET_30");
    quote.setLeadTimeDays(14);

    QuoteLine line = new QuoteLine();
    line.setTenantId(tenantId);
    line.setProductDesc("Organic cotton jersey");
    line.setRequestedQty(new BigDecimal("12.500"));
    line.setUnit("KG");
    line.setListPrice(new BigDecimal("8.0000"));
    line.setOfferedPrice(new BigDecimal("7.2500"));
    line.setCurrency("GBP");
    line.setDiscountRate(new BigDecimal("0.0938"));
    line.setProfitMargin(new BigDecimal("0.1500"));
    line.setPriceZone(QuotePriceZone.FREE);

    quote.addLine(line);
    return quote;
  }
}
