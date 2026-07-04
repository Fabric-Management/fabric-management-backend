package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.sales.common.exception.SalesDomainException;
import com.fabricmanagement.sales.quote.domain.Quote;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalChannel;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalStatus;
import com.fabricmanagement.sales.quote.domain.QuoteApprovalToken;
import com.fabricmanagement.sales.quote.domain.QuoteStatus;
import com.fabricmanagement.sales.quote.domain.event.QuoteApprovalTokenGeneratedEvent;
import com.fabricmanagement.sales.quote.dto.PublicQuoteResponse;
import com.fabricmanagement.sales.quote.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.quote.infra.repository.QuoteRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteApprovalService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final QuoteApprovalTokenRepository tokenRepository;
  private final QuoteRepository quoteRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public QuoteApprovalToken generateTokenForQuote(
      UUID quoteId, QuoteApprovalChannel channel, String sentTo) {
    Quote quote = getActiveQuote(quoteId);

    // Only APPROVED quotes (internally approved) can be sent to the customer
    if (quote.getStatus() != QuoteStatus.APPROVED) {
      throw SalesDomainException.invalidQuoteStatus(
          "Quote must be internally APPROVED before sending to customer.");
    }

    // Invalidate any existing pending token for this quote
    tokenRepository
        .findPendingByQuoteId(quoteId)
        .ifPresent(
            existingToken -> {
              existingToken.setStatus(QuoteApprovalStatus.EXPIRED);
              tokenRepository.save(existingToken);
            });

    // Fix #3: Use SecureRandom for cryptographically strong tokens (64-char hex, 256-bit entropy)
    byte[] tokenBytes = new byte[32];
    SECURE_RANDOM.nextBytes(tokenBytes);
    String secureToken = HexFormat.of().formatHex(tokenBytes);

    QuoteApprovalToken token = new QuoteApprovalToken();
    token.setTenantId(quote.getTenantId());
    token.setQuoteId(quoteId);
    token.setToken(secureToken);
    token.setChannel(channel);
    token.setSentTo(sentTo);
    token.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    token.setStatus(QuoteApprovalStatus.PENDING);

    QuoteApprovalToken savedToken = tokenRepository.save(token);

    if (channel == QuoteApprovalChannel.EMAIL) {
      eventPublisher.publishEvent(
          new QuoteApprovalTokenGeneratedEvent(
              quote.getTenantId(),
              quote.getId(),
              quote.getQuoteNumber(),
              savedToken.getToken(),
              sentTo,
              channel,
              LocalizationContext.getLocale()));
    }

    return savedToken;
  }

  @Transactional
  public PublicQuoteResponse getPublicQuoteByToken(String tokenStr) {
    QuoteApprovalToken token = requireUsablePublicToken(tokenStr);
    Quote quote = getActiveQuote(token.getTenantId(), token.getQuoteId());
    return PublicQuoteResponse.from(quote);
  }

  @Transactional
  public Quote processCustomerApproval(
      String tokenStr, String ipAddress, String userAgent, String customerNote) {
    QuoteApprovalToken token = requireUsablePublicToken(tokenStr);

    // Mark token as USED with full audit trail
    token.setStatus(QuoteApprovalStatus.USED);
    token.setUsedAt(Instant.now());
    token.setIpAddress(ipAddress);
    token.setUserAgent(userAgent);
    token.setCustomerNote(normalizeCustomerNote(customerNote));
    tokenRepository.save(token);

    // Transition quote to CONVERTED — accepted by customer, ready to become a Sales Order
    Quote quote = getActiveQuote(token.getTenantId(), token.getQuoteId());
    quote.setStatus(QuoteStatus.CONVERTED);
    return quoteRepository.save(quote);
  }

  private QuoteApprovalToken requireUsablePublicToken(String tokenStr) {
    QuoteApprovalToken token =
        tokenRepository
            .findByTokenAndIsActiveTrue(tokenStr)
            .orElseThrow(
                () ->
                    SalesDomainException.approvalTokenNotFound(
                        "Invalid or non-existent quote approval token"));

    if (token.getStatus() != QuoteApprovalStatus.PENDING) {
      throw SalesDomainException.approvalTokenNoLongerValid(
          "Quote approval token has already been used or expired");
    }

    if (Instant.now().isAfter(token.getExpiresAt())) {
      token.setStatus(QuoteApprovalStatus.EXPIRED);
      tokenRepository.save(token);
      throw SalesDomainException.approvalTokenNoLongerValid("This quote approval link has expired");
    }

    return token;
  }

  private String normalizeCustomerNote(String customerNote) {
    if (customerNote == null || customerNote.isBlank()) {
      return null;
    }
    return customerNote.strip();
  }

  private Quote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.requireTenantId();
    return getActiveQuote(tenantId, quoteId);
  }

  private Quote getActiveQuote(UUID tenantId, UUID quoteId) {
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound(quoteId.toString()));
  }
}
