package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.communication.app.VerificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coordinates verification flow: throttle → generate → send. Single entry for "send verification
 * code" used by Registration, Login, PasswordReset.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationDispatcher {

  private final VerificationThrottleService throttleService;
  private final VerificationCodeService codeService;
  private final VerificationService verificationService;

  /**
   * Check throttle, generate code, persist, and send via communication channel. Returns the issued
   * code result for callers that need expiresAt or for backward compatibility.
   *
   * @param contactValue email or phone
   * @param type verification type
   * @return issued code (code is already sent; return for logging/expiresAt)
   */
  @Transactional
  public VerificationCodeManager.IssuedVerificationCode sendVerificationCode(
      String contactValue, VerificationType type) {
    UUID tenantId =
        TenantContext.getCurrentTenantIdOrNull() != null
            ? TenantContext.getCurrentTenantIdOrNull()
            : TenantContext.SYSTEM_TENANT_ID;

    UUID userId = TenantContext.getCurrentUserIdOrNull();
    if (userId == null) {
      userId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    }

    return sendVerificationCode(contactValue, type, tenantId, userId);
  }

  /**
   * Check throttle, generate code, persist, and send via communication channel with explicit
   * tenantId and userId.
   *
   * @param contactValue email or phone
   * @param type verification type
   * @param tenantId tenant ID
   * @param userId user ID
   * @return issued code (code is already sent; return for logging/expiresAt)
   */
  @Transactional
  public VerificationCodeManager.IssuedVerificationCode sendVerificationCode(
      String contactValue, VerificationType type, UUID tenantId, UUID userId) {
    throttleService.checkThrottle(contactValue, tenantId, type);
    VerificationCodeManager.IssuedVerificationCode result =
        codeService.generate(contactValue, type);

    verificationService.sendVerificationCode(contactValue, result.code(), tenantId, userId, type);
    return result;
  }
}
