package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
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
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class QuoteApprovalService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Pattern BASIC_EMAIL_PATTERN =
      Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

  private final QuoteApprovalTokenRepository tokenRepository;
  private final QuoteRepository quoteRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final SystemTransactionExecutor systemTransactionExecutor;
  private final TransactionTemplate transactionTemplate;

  @Transactional
  public QuoteApprovalToken generateTokenForQuote(
      UUID quoteId, QuoteApprovalChannel channel, String sentTo) {
    return generateTokenForQuote(quoteId, channel, sentTo, null);
  }

  @Transactional
  public QuoteApprovalToken generateTokenForQuote(
      UUID quoteId, QuoteApprovalChannel channel, String sentTo, UUID contactId) {
    Quote quote = getActiveQuote(quoteId);
    validateSentTo(channel, sentTo);

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
    token.setContactId(contactId);
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
  public void expirePendingTokensForQuote(UUID tenantId, UUID quoteId) {
    List<QuoteApprovalToken> pendingTokens =
        tokenRepository.findPendingByTenantIdAndQuoteId(tenantId, quoteId);
    if (pendingTokens.isEmpty()) {
      return;
    }
    pendingTokens.forEach(token -> token.setStatus(QuoteApprovalStatus.EXPIRED));
    tokenRepository.saveAll(pendingTokens);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicQuoteResponse getPublicQuoteByToken(String tokenStr) {
    UUID tenantId = resolvePublicTokenTenant(tokenStr);
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  QuoteApprovalToken token = requireUsablePublicToken(tokenStr);
                  Quote quote = getActiveQuote(token.getTenantId(), token.getQuoteId());
                  requireApprovableQuote(quote);
                  return PublicQuoteResponse.from(quote);
                }));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Quote processCustomerApproval(
      String tokenStr, String ipAddress, String userAgent, String customerNote) {
    UUID tenantId = resolvePublicTokenTenant(tokenStr);
    return TenantContext.executeInTenantContext(
        tenantId,
        () ->
            transactionTemplate.execute(
                status -> {
                  QuoteApprovalToken token = requireUsablePublicToken(tokenStr);
                  Quote quote = getActiveQuote(token.getTenantId(), token.getQuoteId());
                  requireApprovableQuote(quote);

                  // Mark token as USED with full audit trail
                  token.setStatus(QuoteApprovalStatus.USED);
                  token.setUsedAt(Instant.now());
                  token.setIpAddress(ipAddress);
                  token.setUserAgent(userAgent);
                  token.setCustomerNote(normalizeCustomerNote(customerNote));
                  tokenRepository.save(token);

                  // Transition quote to CONVERTED — accepted by customer, ready to become a Sales
                  // Order
                  quote.setStatus(QuoteStatus.CONVERTED);
                  return quoteRepository.save(quote);
                }));
  }

  private UUID resolvePublicTokenTenant(String tokenStr) {
    UUID tenantId =
        systemTransactionExecutor.executeQueryForObject(
            "SELECT tenant_id FROM sales.quote_approval_token WHERE token = ? AND is_active = true",
            (rs, rowNum) -> UUID.fromString(rs.getString("tenant_id")),
            tokenStr);
    if (tenantId == null) {
      throw SalesDomainException.approvalTokenNotFound(
          "Invalid or non-existent quote approval token");
    }
    return tenantId;
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

  private void requireApprovableQuote(Quote quote) {
    if (quote.getStatus() != QuoteStatus.APPROVED) {
      throw SalesDomainException.approvalTokenNoLongerValid("This quote is no longer valid");
    }
  }

  private void validateSentTo(QuoteApprovalChannel channel, String sentTo) {
    if (channel != QuoteApprovalChannel.EMAIL) {
      return;
    }
    if (sentTo == null || sentTo.isBlank() || !BASIC_EMAIL_PATTERN.matcher(sentTo).matches()) {
      throw SalesDomainException.invalidQuoteTokenRecipient(
          "sentTo must be a valid email address when channel is EMAIL");
    }
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
