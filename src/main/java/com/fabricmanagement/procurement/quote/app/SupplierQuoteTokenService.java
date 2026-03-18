package com.fabricmanagement.procurement.quote.app;

import com.fabricmanagement.procurement.common.exception.ProcurementDomainException;
import com.fabricmanagement.procurement.quote.domain.QuoteEntryMethod;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteToken;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteTokenStatus;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteTokenRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fix #15 — Tedarikçi portal erişim token'larını yönetir.
 *
 * <p>Faz 5'teki {@code QuoteApprovalService} ile aynı güvenli token stratejisi: {@code
 * SecureRandom} + {@code HexFormat} ile 256-bit entropi (64 karakter hex).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SupplierQuoteTokenService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int TOKEN_VALIDITY_DAYS = 7;

  private final SupplierQuoteTokenRepository tokenRepository;

  /**
   * Belirtilen rfqRecipientId için yeni bir portal token'ı üretir. Varsa önceki PENDING token'ı
   * EXPIRED yaparak tek token ilkesini korur.
   */
  @Transactional
  public SupplierQuoteToken generateToken(
      UUID rfqRecipientId, QuoteEntryMethod entryMethod, UUID tenantId) {

    // Önceki PENDING token varsa iptal et
    tokenRepository
        .findPendingByRecipientId(rfqRecipientId)
        .ifPresent(
            existing -> {
              existing.setStatus(SupplierQuoteTokenStatus.EXPIRED);
              tokenRepository.save(existing);
              log.info("Invalidated previous pending token for recipient={}", rfqRecipientId);
            });

    // Fix #15: SecureRandom — 32 byte = 256-bit entropi
    byte[] tokenBytes = new byte[32];
    SECURE_RANDOM.nextBytes(tokenBytes);
    String secureToken = HexFormat.of().formatHex(tokenBytes);

    SupplierQuoteToken token = new SupplierQuoteToken();
    token.setTenantId(tenantId);
    token.setRfqRecipientId(rfqRecipientId);
    token.setToken(secureToken);
    token.setEntryMethod(entryMethod);
    token.setExpiresAt(Instant.now().plus(TOKEN_VALIDITY_DAYS, ChronoUnit.DAYS));
    token.setStatus(SupplierQuoteTokenStatus.PENDING);

    SupplierQuoteToken saved = tokenRepository.save(token);
    log.info(
        "Generated supplier quote token for recipient={}, method={}", rfqRecipientId, entryMethod);
    return saved;
  }

  /**
   * Portal üzerinden gelen token'ı doğrular ve {@code USED} olarak işaretler. Geçersiz, süresi
   * dolmuş veya zaten kullanılmış token'lar için {@link ProcurementDomainException} fırlatılır.
   */
  @Transactional
  public SupplierQuoteToken validateAndConsume(String tokenStr) {
    SupplierQuoteToken token =
        tokenRepository
            .findByTokenAndIsActiveTrue(tokenStr)
            .orElseThrow(() -> new ProcurementDomainException("Invalid or non-existent token"));

    if (token.getStatus() != SupplierQuoteTokenStatus.PENDING) {
      throw new ProcurementDomainException(
          "Token has already been used or expired: status=" + token.getStatus());
    }

    if (Instant.now().isAfter(token.getExpiresAt())) {
      token.setStatus(SupplierQuoteTokenStatus.EXPIRED);
      tokenRepository.save(token);
      throw new ProcurementDomainException("This supplier quote link has expired");
    }

    token.setStatus(SupplierQuoteTokenStatus.USED);
    token.setUsedAt(Instant.now());
    tokenRepository.save(token);

    log.info("Supplier quote token consumed: recipient={}", token.getRfqRecipientId());
    return token;
  }

  /** Belirtilen rfqRecipientId'ye ait aktif PENDING token'ı getirir (yoksa empty). */
  public java.util.Optional<SupplierQuoteToken> findPendingToken(UUID rfqRecipientId) {
    return tokenRepository.findPendingByRecipientId(rfqRecipientId);
  }
}
