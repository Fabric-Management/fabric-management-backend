package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.auth.infra.repository.VerificationCodeRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification rate limiting: per-contact, per-tenant, and global throttling. Throws if limits
 * exceeded. No code generation or sending — use with {@link VerificationCodeService} and {@link
 * VerificationDispatcher}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationThrottleService {

  private final VerificationCodeRepository verificationCodeRepository;

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

  /**
   * Enforce rate limits for verification code issuance. Throws if any limit exceeded.
   *
   * @param contactValue contact (email/phone)
   * @param tenantId tenant ID (use TenantContext.SYSTEM_TENANT_ID if no tenant)
   * @param type verification type
   */
  @Transactional(readOnly = true)
  public void checkThrottle(String contactValue, UUID tenantId, VerificationType type) {
    Instant windowStart = Instant.now().minusSeconds(contactWindowSeconds);
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

  private String maskContact(String contactValue) {
    return contactValue.contains("@")
        ? PiiMaskingUtil.maskEmail(contactValue)
        : PiiMaskingUtil.maskPhone(contactValue);
  }
}
