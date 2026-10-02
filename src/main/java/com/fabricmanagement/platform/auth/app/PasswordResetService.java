package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.VerificationType;
import com.fabricmanagement.platform.auth.dto.PasswordResetRequest;
import com.fabricmanagement.platform.auth.dto.PasswordResetVerifyRequest;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Password reset by email code.
 *
 * <ol>
 *   <li>{@link #requestPasswordReset}: the person enters their email; if a verified, active account
 *       uses it, a six-digit code is sent there.
 *   <li>{@link #resetPassword}: the person enters the email, the code and a new password.
 * </ol>
 *
 * <p>Neither step reveals whether an account exists. The request step answers the same way for
 * every address, and the reset step gives the same error for a wrong code and for an address with
 * no account. After a reset the person signs in with the new password through the normal login, so
 * MFA and organization selection still apply.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

  static final String REQUEST_ACCEPTED_MESSAGE =
      "If an account exists for this email, a password reset code has been sent.";

  private final LoginIdentityRepository loginIdentityRepository;
  private final VerificationCodeManager verificationCodeManager;
  private final IdentityProvisioningService identityProvisioningService;
  private final PasswordEncoder passwordEncoder;

  /** Hash of a random value, created on first use; see {@link #spendCodeCheck(String)}. */
  private volatile String timingPlaceholderHash;

  /**
   * Sends a reset code if the address belongs to a resettable account, and returns the same message
   * either way.
   *
   * <p>Deliberately not transactional: a failure while issuing the code (for example a throttle)
   * must not mark a surrounding transaction rollback-only and turn into a different response.
   */
  public String requestPasswordReset(PasswordResetRequest request) {
    String email = normalizeEmail(request.getContactValue());
    Optional<LoginIdentity> identity = findResettableIdentity(email);

    if (identity.isEmpty()) {
      // Issuing a real code hashes it; do comparable work here so the two cases take a similar
      // time. This narrows the difference; it does not guarantee to remove it.
      passwordEncoder.encode(UUID.randomUUID().toString());
      log.info(
          "Password reset requested for an address with no resettable account: {}",
          PiiMaskingUtil.maskEmail(email));
      return REQUEST_ACCEPTED_MESSAGE;
    }

    try {
      verificationCodeManager.issueCode(email, VerificationType.PASSWORD_RESET);
      log.info("Password reset code sent: {}", PiiMaskingUtil.maskEmail(email));
    } catch (PlatformDomainException ex) {
      // A throttle only ever fires for addresses that received codes, i.e. real accounts, so it
      // must not reach the caller.
      log.warn(
          "Password reset code not sent: contact={}, reason={}",
          PiiMaskingUtil.maskEmail(email),
          ex.getMessage());
    } catch (RuntimeException ex) {
      // A fault here only happens for real accounts too; a 500 would reveal them. Log it loudly
      // and answer like every other request. AuthAccountEnumerationIT checks the code is stored.
      log.error(
          "Password reset code could not be issued: contact={}",
          PiiMaskingUtil.maskEmail(email),
          ex);
    }
    return REQUEST_ACCEPTED_MESSAGE;
  }

  /**
   * Checks the code and sets the new password. The caller signs in afterwards with the new
   * password.
   */
  @Transactional
  public void resetPassword(PasswordResetVerifyRequest request) {
    String email = normalizeEmail(request.getContactValue());
    Optional<LoginIdentity> identityOpt = findResettableIdentity(email);
    if (identityOpt.isEmpty()) {
      // A wrong code costs one hash comparison; spend the same here so the two answers take a
      // similar time. This narrows the difference; it does not guarantee to remove it.
      spendCodeCheck(request.getCode());
      throw invalidResetCode();
    }
    LoginIdentity identity = identityOpt.get();

    try {
      verificationCodeManager.validateAndConsume(
          email, VerificationType.PASSWORD_RESET, request.getCode());
    } catch (PlatformDomainException ex) {
      // Expired, used and too-many-attempts are collapsed into one answer: they can only happen
      // for an address that received a code, so telling them apart would reveal the account.
      log.warn(
          "Password reset code rejected: contact={}, reason={}",
          PiiMaskingUtil.maskEmail(email),
          ex.getMessage());
      throw invalidResetCode();
    }

    if (identity.getPasswordHash() != null
        && passwordEncoder.matches(request.getNewPassword(), identity.getPasswordHash())) {
      throw new PlatformDomainException(
          "New password must be different from your current password", "AUTH_PASSWORD_REUSE", 400);
    }

    identityProvisioningService.updatePassword(
        email, passwordEncoder.encode(request.getNewPassword()));
    log.info("Password reset completed: identityId={}", identity.getId());
  }

  private Optional<LoginIdentity> findResettableIdentity(String email) {
    if (email == null) {
      return Optional.empty();
    }
    return loginIdentityRepository
        .findByEmail(email)
        .filter(identity -> Boolean.TRUE.equals(identity.getEmailVerified()))
        .filter(identity -> Boolean.TRUE.equals(identity.getIsActive()));
  }

  private static String normalizeEmail(String contactValue) {
    if (contactValue == null || contactValue.isBlank()) {
      return null;
    }
    return contactValue.trim().toLowerCase(Locale.ROOT);
  }

  private void spendCodeCheck(String rawCode) {
    String placeholder = timingPlaceholderHash;
    if (placeholder == null) {
      placeholder = passwordEncoder.encode(UUID.randomUUID().toString());
      timingPlaceholderHash = placeholder;
    }
    if (placeholder != null) {
      passwordEncoder.matches(rawCode == null ? "" : rawCode, placeholder);
    }
  }

  private static PlatformDomainException invalidResetCode() {
    return new PlatformDomainException(
        "Verification code is invalid or expired", "AUTH_VERIFICATION_CODE_INVALID", 400);
  }
}
