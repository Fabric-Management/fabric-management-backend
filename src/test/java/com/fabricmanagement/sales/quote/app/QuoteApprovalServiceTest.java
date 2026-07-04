package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.ConvertedMoney;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.domain.event.QuoteApprovalTokenGeneratedEvent;
import com.fabricmanagement.sales.quote.dto.PublicQuoteLineResponse;
import com.fabricmanagement.sales.quote.dto.PublicQuoteResponse;
import com.fabricmanagement.sales.quote.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class QuoteApprovalServiceTest {

  @Mock private QuoteApprovalTokenRepository tokenRepository;
  @Mock private QuoteRepository quoteRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  @InjectMocks private QuoteApprovalService quoteApprovalService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID quoteId = UUID.randomUUID();
  private Quote quote;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(tenantId);
    LocalizationContext.setLocale("tr");
    quote = approvedQuote();
  }

  @AfterEach
  void tearDown() {
    LocalizationContext.clear();
    TenantContext.clear();
  }

  @Test
  void generatesEmailTokenAndPublishesEvent() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tokenRepository.findPendingByQuoteId(quoteId)).thenReturn(Optional.empty());
    when(tokenRepository.save(any(QuoteApprovalToken.class))).thenAnswer(inv -> inv.getArgument(0));

    QuoteApprovalToken token =
        quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com");

    assertNotNull(token.getToken());
    assertEquals(64, token.getToken().length());
    assertEquals(QuoteApprovalChannel.EMAIL, token.getChannel());
    assertEquals("buyer@example.com", token.getSentTo());
    assertEquals(QuoteApprovalStatus.PENDING, token.getStatus());

    ArgumentCaptor<QuoteApprovalTokenGeneratedEvent> eventCaptor =
        ArgumentCaptor.forClass(QuoteApprovalTokenGeneratedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    QuoteApprovalTokenGeneratedEvent event = eventCaptor.getValue();
    assertEquals(tenantId, event.getTenantId());
    assertEquals(quoteId, event.getQuoteId());
    assertEquals("Q-2026-001", event.getQuoteNumber());
    assertEquals(token.getToken(), event.getToken());
    assertEquals("buyer@example.com", event.getCustomerEmail());
    assertEquals(QuoteApprovalChannel.EMAIL, event.getChannel());
    assertEquals("tr", event.getLocaleLanguageTag());
  }

  @Test
  void doesNotPublishEventForNonEmailToken() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tokenRepository.findPendingByQuoteId(quoteId)).thenReturn(Optional.empty());
    when(tokenRepository.save(any(QuoteApprovalToken.class))).thenAnswer(inv -> inv.getArgument(0));

    quoteApprovalService.generateTokenForQuote(
        quoteId, QuoteApprovalChannel.IN_PERSON, "counter-signature");

    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void resendInvalidatesPriorPendingTokenBeforeCreatingNewToken() {
    QuoteApprovalToken existingToken = new QuoteApprovalToken();
    existingToken.setTenantId(tenantId);
    existingToken.setQuoteId(quoteId);
    existingToken.setToken("old-token");
    existingToken.setChannel(QuoteApprovalChannel.EMAIL);
    existingToken.setSentTo("old@example.com");
    existingToken.setStatus(QuoteApprovalStatus.PENDING);

    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(quote));
    when(tokenRepository.findPendingByQuoteId(quoteId)).thenReturn(Optional.of(existingToken));
    when(tokenRepository.save(any(QuoteApprovalToken.class))).thenAnswer(inv -> inv.getArgument(0));

    QuoteApprovalToken newToken =
        quoteApprovalService.generateTokenForQuote(
            quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com");

    assertEquals(QuoteApprovalStatus.EXPIRED, existingToken.getStatus());
    assertEquals(QuoteApprovalStatus.PENDING, newToken.getStatus());
    verify(tokenRepository).save(existingToken);
    verify(eventPublisher).publishEvent(any(QuoteApprovalTokenGeneratedEvent.class));
  }

  @Test
  void preservesTenantIsolationWhenQuoteIsNotVisible() {
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.empty());

    org.junit.jupiter.api.Assertions.assertThrows(
        com.fabricmanagement.sales.common.exception.SalesDomainException.class,
        () ->
            quoteApprovalService.generateTokenForQuote(
                quoteId, QuoteApprovalChannel.EMAIL, "buyer@example.com"));

    verify(tokenRepository, never()).findPendingByQuoteId(any());
    verify(tokenRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any(Object.class));
  }

  @Test
  void getPublicQuoteByTokenReturnsCustomerSafeProjection() {
    Quote publicQuote = quoteWithCustomerFacingTotals();
    QuoteApprovalToken token = pendingToken(tenantId, quoteId);

    when(tokenRepository.findByTokenAndIsActiveTrue("public-token")).thenReturn(Optional.of(token));
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId))
        .thenReturn(Optional.of(publicQuote));

    PublicQuoteResponse response = quoteApprovalService.getPublicQuoteByToken("public-token");

    assertThat(response.getQuoteNumber()).isEqualTo("Q-2026-001");
    assertThat(response.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    assertThat(response.getTotalAmount().getCurrency()).isEqualTo("USD");
    assertThat(response.getTotalAmount().getAmount()).isEqualByComparingTo("200.00");
    assertThat(response.getReportingTotal().getConvertedCurrency()).isEqualTo("GBP");
    assertThat(response.getReportingTotal().getConvertedAmount()).isEqualByComparingTo("160.00");
    assertThat(response.getLines()).hasSize(1);
    assertThat(response.getLines().get(0).getCurrency()).isEqualTo("USD");
    assertThat(response.getLines().get(0).getOfferedPrice()).isEqualByComparingTo("100.00");

    assertThat(fieldNames(PublicQuoteResponse.class))
        .doesNotContain("customerId", "assignedToId", "estimatedUnitCost");
    assertThat(fieldNames(PublicQuoteLineResponse.class))
        .doesNotContain("profitMargin", "priceZone");
  }

  @Test
  void getPublicQuoteByTokenRejectsInvalidTokenAsNotFound() {
    when(tokenRepository.findByTokenAndIsActiveTrue("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> quoteApprovalService.getPublicQuoteByToken("missing"))
        .isInstanceOf(SalesDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(404);

    verify(quoteRepository, never()).findByTenantIdAndIdAndIsActiveTrue(any(), any());
  }

  @Test
  void getPublicQuoteByTokenExpiresPastDueTokenAndReturnsGone() {
    QuoteApprovalToken token = pendingToken(tenantId, quoteId);
    token.setExpiresAt(Instant.now().minusSeconds(60));

    when(tokenRepository.findByTokenAndIsActiveTrue("expired")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> quoteApprovalService.getPublicQuoteByToken("expired"))
        .isInstanceOf(SalesDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(410);

    assertThat(token.getStatus()).isEqualTo(QuoteApprovalStatus.EXPIRED);
    verify(tokenRepository).save(token);
    verify(quoteRepository, never()).findByTenantIdAndIdAndIsActiveTrue(any(), any());
  }

  @Test
  void getPublicQuoteByTokenRejectsUsedTokenAsGone() {
    QuoteApprovalToken token = pendingToken(tenantId, quoteId);
    token.setStatus(QuoteApprovalStatus.USED);

    when(tokenRepository.findByTokenAndIsActiveTrue("used")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> quoteApprovalService.getPublicQuoteByToken("used"))
        .isInstanceOf(SalesDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(410);

    verify(quoteRepository, never()).findByTenantIdAndIdAndIsActiveTrue(any(), any());
  }

  @Test
  void processCustomerApprovalPersistsNoteUsesTokenTenantAndConvertsQuote() {
    UUID tokenTenantId = UUID.randomUUID();
    TenantContext.clear();
    Quote publicQuote = quoteWithCustomerFacingTotals();
    publicQuote.setTenantId(tokenTenantId);
    QuoteApprovalToken token = pendingToken(tokenTenantId, quoteId);

    when(tokenRepository.findByTokenAndIsActiveTrue("approve-token"))
        .thenReturn(Optional.of(token));
    when(quoteRepository.findByTenantIdAndIdAndIsActiveTrue(tokenTenantId, quoteId))
        .thenReturn(Optional.of(publicQuote));
    when(quoteRepository.save(any(Quote.class))).thenAnswer(inv -> inv.getArgument(0));

    Quote approved =
        quoteApprovalService.processCustomerApproval(
            "approve-token", "127.0.0.1", "Mozilla", " Please ship quickly ");

    assertThat(token.getStatus()).isEqualTo(QuoteApprovalStatus.USED);
    assertThat(token.getCustomerNote()).isEqualTo("Please ship quickly");
    assertThat(token.getIpAddress()).isEqualTo("127.0.0.1");
    assertThat(token.getUserAgent()).isEqualTo("Mozilla");
    assertThat(approved.getStatus()).isEqualTo(QuoteStatus.CONVERTED);
    verify(quoteRepository).findByTenantIdAndIdAndIsActiveTrue(tokenTenantId, quoteId);
    verify(tokenRepository).save(token);
    verify(quoteRepository).save(publicQuote);
  }

  @Test
  void processCustomerApprovalRejectsUsedToken() {
    QuoteApprovalToken token = pendingToken(tenantId, quoteId);
    token.setStatus(QuoteApprovalStatus.USED);

    when(tokenRepository.findByTokenAndIsActiveTrue("used")).thenReturn(Optional.of(token));

    assertThatThrownBy(
            () -> quoteApprovalService.processCustomerApproval("used", null, null, "ignored"))
        .isInstanceOf(SalesDomainException.class)
        .extracting("httpStatus")
        .isEqualTo(410);

    verify(quoteRepository, never()).save(any());
  }

  private Quote approvedQuote() {
    Quote q = new Quote();
    q.setId(quoteId);
    q.setTenantId(tenantId);
    q.setQuoteNumber("Q-2026-001");
    q.setCustomerId(UUID.randomUUID());
    q.setAssignedToId(UUID.randomUUID());
    q.setModuleType("FABRIC");
    q.setValidUntil(LocalDate.now().plusDays(10));
    q.setStatus(QuoteStatus.APPROVED);
    q.setRevisionNumber(1);
    return q;
  }

  private Quote quoteWithCustomerFacingTotals() {
    Quote q = approvedQuote();
    q.setEstimatedUnitCost(new BigDecimal("55.00"));
    q.setCurrency("USD");
    q.setPaymentTerms("NET30");
    q.setLeadTimeDays(14);
    q.setNotes("Customer-visible note");
    q.setTotalAmount(Money.of(new BigDecimal("200.00"), "USD"));
    q.setReportingTotal(
        ConvertedMoney.of(
            new BigDecimal("200.00"),
            "USD",
            new BigDecimal("160.00"),
            "GBP",
            new BigDecimal("0.80000000"),
            LocalDate.now()));

    QuoteLine line = new QuoteLine();
    line.setId(UUID.randomUUID());
    line.setTenantId(q.getTenantId());
    line.setProductId(UUID.randomUUID());
    line.setProductDesc("Premium fabric");
    line.setRequestedQty(new BigDecimal("2.000"));
    line.setUnit("m");
    line.setListPrice(new BigDecimal("120.00"));
    line.setOfferedPrice(new BigDecimal("100.00"));
    line.setCurrency("USD");
    line.setDiscountRate(new BigDecimal("0.1667"));
    line.setProfitMargin(new BigDecimal("0.4500"));
    line.setPriceZone(QuotePriceZone.FREE);
    q.addLine(line);

    return q;
  }

  private QuoteApprovalToken pendingToken(UUID tokenTenantId, UUID tokenQuoteId) {
    QuoteApprovalToken token = new QuoteApprovalToken();
    token.setTenantId(tokenTenantId);
    token.setQuoteId(tokenQuoteId);
    token.setToken("public-token");
    token.setChannel(QuoteApprovalChannel.EMAIL);
    token.setSentTo("buyer@example.com");
    token.setStatus(QuoteApprovalStatus.PENDING);
    token.setExpiresAt(Instant.now().plusSeconds(3600));
    return token;
  }

  private List<String> fieldNames(Class<?> type) {
    return Arrays.stream(type.getDeclaredFields()).map(Field::getName).toList();
  }
}
