package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.VerificationCode;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.infra.repository.VerificationCodeRepository;
import java.security.SecureRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification code CRUD: generate and validate/consume. No throttling or sending — use {@link
 * VerificationThrottleService} and {@link VerificationDispatcher} for full flow.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeService {

  private final VerificationCodeRepository verificationCodeRepository;
  private final PasswordEncoder passwordEncoder;

  @Value("${application.verification.code-length:6}")
  private int codeLength;

  @Value("${application.verification.code-expiry-minutes:10}")
  private int codeExpiryMinutes;

  @Value("${application.verification.max-attempts:3}")
  private int maxAttempts;

  private final SecureRandom secureRandom = new SecureRandom();

  @Transactional
  public VerificationCodeManager.IssuedVerificationCode generate(
      String contactValue, VerificationType type) {
    UUID tenantId = resolveTenant();
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

    return new VerificationCodeManager.IssuedVerificationCode(rawCode, entity.getExpiresAt());
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
}
