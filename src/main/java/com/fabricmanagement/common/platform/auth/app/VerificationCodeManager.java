package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.VerificationCode;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.infra.repository.VerificationCodeRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * VerificationCodeManager centralises verification code issuance and validation.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Generate single-use verification codes with BCrypt hashing
 *   <li>Enforce per-contact, per-tenant, and global throttling rules
 *   <li>Validate codes with attempt tracking and audit logging
 *   <li>Provide consistent error messaging and logging for security monitoring
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeManager {

  private final VerificationCodeRepository verificationCodeRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.verification.code-length:6}")
  private int codeLength;

  @Value("${application.verification.code-expiry-minutes:10}")
  private int codeExpiryMinutes;

  @Value("${application.verification.throttle.contact.max:5}")
  private int maxPerContactWindow;

  @Value("${application.verification.throttle.contact.window-seconds:600}")
  private int contactWindowSeconds;

  @Value("${application.verification.throttle.tenant.max:100}")
  private int maxPerTenantWindow;

  @Value("${application.verification.throttle.global.max:1000}")
  private int maxGlobalWindow;

  @Value("${application.verification.throttle.window-seconds:600}")
  private int throttleWindowSeconds;

  @Value("${application.verification.max-attempts:3}")
  private int maxAttempts;

  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public IssuedVerificationCode issueCode(String contactValue, VerificationType type) {
    UUID tenantId = resolveTenant();
    Instant windowStart = Instant.now().minusSeconds(contactWindowSeconds);
    enforceRateLimits(tenantId, contactValue, type, windowStart);

    String rawCode = generateNumericCode();
    String hash = passwordEncoder.encode(rawCode);

    verificationCodeRepository.deleteByTenantIdAndContactValueAndType(tenantId, contactValue, type);

    VerificationCode entity = VerificationCode.create(contactValue, hash, type, codeExpiryMinutes);
    verificationCodeRepository.save(entity);

    log.info(
        "Verification code issued: tenantId={}, contact={}, type={}, expiresAt={}",
        tenantId,
        maskContact(contactValue),
        type,
        entity.getExpiresAt());

    return new IssuedVerificationCode(rawCode, entity.getExpiresAt());
  }

  @Transactional
  public void validateAndConsume(String contactValue, VerificationType type, String rawCode) {
    UUID tenantId = resolveTenant();
    VerificationCode verificationCode =
        verificationCodeRepository
            .findTopByTenantIdAndContactValueAndTypeOrderByCreatedAtDesc(
                tenantId, contactValue, type)
            .orElseThrow(
                () -> {
                  log.warn(
                      "Verification code not found: tenantId={}, contact={}, type={}",
                      tenantId,
                      maskContact(contactValue),
                      type);
                  return new IllegalArgumentException("Verification code is invalid or expired");
                });

    if (verificationCode.isExpired()) {
      log.warn(
          "Verification code expired: tenantId={}, contact={}, type={}",
          tenantId,
          maskContact(contactValue),
          type);
      verificationCode.markAsUsed();
      verificationCodeRepository.save(verificationCode);
      throw new IllegalArgumentException(
          "Verification code has expired. Please request a new code.");
    }

    if (Boolean.TRUE.equals(verificationCode.getIsUsed())) {
      log.warn(
          "Verification code already used: tenantId={}, contact={}, type={}",
          tenantId,
          maskContact(contactValue),
          type);
      throw new IllegalArgumentException("Verification code has already been used.");
    }

    if (verificationCode.getAttemptCount() >= maxAttempts) {
      log.warn(
          "Verification code attempt limit reached: tenantId={}, contact={}, type={}",
          tenantId,
          maskContact(contactValue),
          type);
      throw new IllegalArgumentException(
          "Too many verification attempts. Please request a new code.");
    }

    if (!verificationCode.matches(rawCode, passwordEncoder)) {
      verificationCode.incrementAttempt();
      verificationCodeRepository.save(verificationCode);
      log.warn(
          "Verification code mismatch: tenantId={}, contact={}, type={}, attempts={}",
          tenantId,
          maskContact(contactValue),
          type,
          verificationCode.getAttemptCount());
      throw new IllegalArgumentException("Verification code is invalid or expired");
    }

    verificationCode.markAsUsed();
    verificationCodeRepository.save(verificationCode);
    log.info(
        "Verification code validated: tenantId={}, contact={}, type={}",
        tenantId,
        maskContact(contactValue),
        type);
  }

  private void enforceRateLimits(
      UUID tenantId, String contactValue, VerificationType type, Instant windowStart) {
    long perContact =
        verificationCodeRepository.countByTenantIdAndContactValueAndTypeAndCreatedAtAfter(
            tenantId, contactValue, type, windowStart);
    if (perContact >= maxPerContactWindow) {
      log.warn(
          "Verification throttle exceeded (contact): tenantId={}, contact={}, type={}",
          tenantId,
          maskContact(contactValue),
          type);
      throw new IllegalArgumentException("Too many verification requests. Please try again later.");
    }

    Instant genericWindowStart = Instant.now().minusSeconds(throttleWindowSeconds);
    long perTenant =
        verificationCodeRepository.countByTenantIdAndTypeAndCreatedAtAfter(
            tenantId, type, genericWindowStart);
    if (perTenant >= maxPerTenantWindow) {
      log.warn("Verification throttle exceeded (tenant): tenantId={}, type={}", tenantId, type);
      throw new IllegalArgumentException(
          "Verification temporarily limited for your organisation. Please try later.");
    }

    long globalCount = verificationCodeRepository.countByCreatedAtAfter(genericWindowStart);
    if (globalCount >= maxGlobalWindow) {
      log.error("Verification throttle exceeded (global): type={} count={}", type, globalCount);
      throw new IllegalStateException("Verification system is busy. Please try again shortly.");
    }
  }

  private String generateNumericCode() {
    int max = (int) Math.pow(10, codeLength);
    int min = (int) Math.pow(10, codeLength - 1);
    int code = secureRandom.nextInt(max - min) + min;
    return String.valueOf(code);
  }

  private UUID resolveTenant() {
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    return tenantId != null ? tenantId : TenantContext.SYSTEM_TENANT_ID;
  }

  private String maskContact(String contactValue) {
    return contactValue.contains("@")
        ? PiiMaskingUtil.maskEmail(contactValue)
        : PiiMaskingUtil.maskPhone(contactValue);
  }

  public record IssuedVerificationCode(String code, Instant expiresAt) {}
}
