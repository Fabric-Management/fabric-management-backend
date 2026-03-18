package com.fabricmanagement.sales.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.domain.exception.SalesDomainException;
import com.fabricmanagement.sales.domain.quote.Quote;
import com.fabricmanagement.sales.domain.quote.QuoteApprovalChannel;
import com.fabricmanagement.sales.domain.quote.QuoteApprovalStatus;
import com.fabricmanagement.sales.domain.quote.QuoteApprovalToken;
import com.fabricmanagement.sales.domain.quote.QuoteStatus;
import com.fabricmanagement.sales.infra.repository.QuoteApprovalTokenRepository;
import com.fabricmanagement.sales.infra.repository.QuoteRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuoteApprovalService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final QuoteApprovalTokenRepository tokenRepository;
  private final QuoteRepository quoteRepository;

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

    // Note: Emitting a QuoteApprovalTokenGeneratedEvent here would trigger the
    // Notification Hub (Phase 7) to actually dispatch the email/SMS/WhatsApp.

    return tokenRepository.save(token);
  }

  @Transactional
  public Quote processCustomerApproval(String tokenStr, String ipAddress, String userAgent) {
    QuoteApprovalToken token =
        tokenRepository
            .findByTokenAndIsActiveTrue(tokenStr)
            .orElseThrow(
                () -> SalesDomainException.tokenExpiredOrUsed("Invalid or non-existent token"));

    if (token.getStatus() != QuoteApprovalStatus.PENDING) {
      throw SalesDomainException.tokenExpiredOrUsed("Token has already been used or expired");
    }

    if (Instant.now().isAfter(token.getExpiresAt())) {
      token.setStatus(QuoteApprovalStatus.EXPIRED);
      tokenRepository.save(token);
      throw SalesDomainException.tokenExpiredOrUsed("This quote approval link has expired");
    }

    // Mark token as USED with full audit trail
    token.setStatus(QuoteApprovalStatus.USED);
    token.setUsedAt(Instant.now());
    token.setIpAddress(ipAddress);
    token.setUserAgent(userAgent);
    tokenRepository.save(token);

    // Transition quote to CONVERTED — accepted by customer, ready to become a Sales Order
    Quote quote = getActiveQuote(token.getQuoteId());
    quote.setStatus(QuoteStatus.CONVERTED);
    return quoteRepository.save(quote);
  }

  private Quote getActiveQuote(UUID quoteId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    return quoteRepository
        .findByTenantIdAndIdAndIsActiveTrue(tenantId, quoteId)
        .orElseThrow(() -> SalesDomainException.quoteNotFound(quoteId.toString()));
  }
}
