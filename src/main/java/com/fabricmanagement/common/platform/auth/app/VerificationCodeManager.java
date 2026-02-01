package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade for verification code issuance and validation. Delegates to {@link VerificationDispatcher}
 * (throttle + generate + send) and {@link VerificationCodeService} (validate/consume). Kept for
 * backward compatibility; new code may use dispatcher/codeService directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeManager {

  private final VerificationDispatcher verificationDispatcher;
  private final VerificationCodeService verificationCodeService;

  @Transactional
  public IssuedVerificationCode issueCode(String contactValue, VerificationType type) {
    return verificationDispatcher.sendVerificationCode(contactValue, type);
  }

  @Transactional
  public void validateAndConsume(String contactValue, VerificationType type, String rawCode) {
    verificationCodeService.validateAndConsume(contactValue, type, rawCode);
  }

  public record IssuedVerificationCode(String code, java.time.Instant expiresAt) {}
}
