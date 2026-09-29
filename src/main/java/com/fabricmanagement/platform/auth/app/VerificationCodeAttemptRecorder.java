package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.infra.repository.VerificationCodeRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a failed verification-code attempt in a transaction of its own.
 *
 * <p>{@link VerificationCodeService#validateAndConsume} rejects a wrong code by throwing, which
 * rolls back the caller's transaction. Counting the attempt inside that transaction would roll the
 * count back with it, so the attempt limit would never be reached. This bean runs the increment in
 * a new transaction that commits before the exception is thrown.
 */
@Service
@RequiredArgsConstructor
public class VerificationCodeAttemptRecorder {

  private final VerificationCodeRepository verificationCodeRepository;

  /**
   * Increments the attempt counter of the given code and commits it immediately.
   *
   * @return the attempt count after the increment, or 0 if the code no longer exists
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int recordFailedAttempt(UUID verificationCodeId) {
    if (verificationCodeRepository.incrementAttemptCount(verificationCodeId) == 0) {
      return 0;
    }
    return verificationCodeRepository
        .findById(verificationCodeId)
        .map(code -> code.getAttemptCount() == null ? 0 : code.getAttemptCount())
        .orElse(0);
  }
}
