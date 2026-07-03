package com.fabricmanagement.sales.quote.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.domain.event.QuoteApprovalTokenGeneratedEvent;
import com.fabricmanagement.sales.quote.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import java.time.LocalDate;
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
}
