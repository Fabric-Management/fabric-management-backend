package com.fabricmanagement.sales.quote.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteLine;
import com.fabricmanagement.sales.quote.domain.QuotePriceZone;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.dto.PublicQuoteResponse;
import com.fabricmanagement.sales.quote.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

@DisplayName("Quote Approval Public Token Integration Test")
class QuoteApprovalServicePublicTokenIntegrationTest extends AbstractIntegrationTest {

  @Autowired private QuoteApprovalService quoteApprovalService;
  @Autowired private QuoteRepository quoteRepository;
  @Autowired private QuoteApprovalTokenRepository tokenRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();

  @AfterEach
  void tearDown() {
    TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> {
                  tokenRepository.deleteAll();
                  quoteRepository.deleteAll();
                }));
    TenantContext.clear();
  }

  @Test
  @DisplayName("public no-tenant token read and approval resolve tenant before RLS-bound JPA")
  void publicTokenFlowsSucceedWhenTenantContextIsCleared() {
    String token = persistApprovedQuoteToken();

    TenantContext.clear();
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();

    PublicQuoteResponse response = quoteApprovalService.getPublicQuoteByToken(token);

    assertThat(response.getQuoteNumber()).startsWith("Q-PUBLIC-RLS-");
    assertThat(response.getStatus()).isEqualTo(QuoteStatus.APPROVED);
    assertThat(response.getLines()).hasSize(1);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();

    Quote converted =
        quoteApprovalService.processCustomerApproval(
            token, "203.0.113.10", "JUnit", " Please proceed ");

    assertThat(converted.getStatus()).isEqualTo(QuoteStatus.CONVERTED);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();

    TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          QuoteApprovalToken usedToken =
              tokenRepository.findByTokenAndIsActiveTrue(token).orElseThrow();
          assertThat(usedToken.getStatus()).isEqualTo(QuoteApprovalStatus.USED);
          assertThat(usedToken.getCustomerNote()).isEqualTo("Please proceed");
          assertThat(usedToken.getIpAddress()).isEqualTo("203.0.113.10");
          assertThat(usedToken.getUserAgent()).isEqualTo("JUnit");
        });
  }

  private String persistApprovedQuoteToken() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);

    return transactionTemplate.execute(
        status -> {
          Quote quote = quoteWithLine();
          Quote savedQuote = quoteRepository.saveAndFlush(quote);

          String publicToken = "public-rls-" + UUID.randomUUID();
          QuoteApprovalToken token = new QuoteApprovalToken();
          token.setTenantId(tenantId);
          token.setQuoteId(savedQuote.getId());
          token.setToken(publicToken);
          token.setChannel(QuoteApprovalChannel.EMAIL);
          token.setSentTo("buyer@example.com");
          token.setExpiresAt(Instant.now().plusSeconds(3600));
          token.setStatus(QuoteApprovalStatus.PENDING);
          tokenRepository.saveAndFlush(token);

          return publicToken;
        });
  }

  private Quote quoteWithLine() {
    Quote quote = new Quote();
    quote.setTenantId(tenantId);
    quote.setQuoteNumber("Q-PUBLIC-RLS-" + UUID.randomUUID());
    quote.setCustomerId(UUID.randomUUID());
    quote.setAssignedToId(userId);
    quote.setModuleType("FABRIC");
    quote.setStatus(QuoteStatus.APPROVED);
    quote.setCurrency("GBP");
    quote.setTotalAmount(Money.of(new BigDecimal("145.00"), "GBP"));
    quote.setValidUntil(LocalDate.now().plusDays(14));
    quote.setPaymentTerms("NET_30");
    quote.setLeadTimeDays(7);
    quote.setRevisionNumber(1);

    QuoteLine line = new QuoteLine();
    line.setTenantId(tenantId);
    line.setProductDesc("Public approval fabric");
    line.setRequestedQty(new BigDecimal("10.000"));
    line.setUnit("M");
    line.setListPrice(new BigDecimal("15.0000"));
    line.setOfferedPrice(new BigDecimal("14.5000"));
    line.setCurrency("GBP");
    line.setDiscountRate(new BigDecimal("0.0333"));
    line.setProfitMargin(new BigDecimal("0.1200"));
    line.setPriceZone(QuotePriceZone.FREE);

    quote.addLine(line);
    return quote;
  }
}
