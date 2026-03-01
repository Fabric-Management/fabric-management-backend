package com.fabricmanagement.common.platform.auth.app;

import com.fabricmanagement.common.platform.auth.domain.MfaType;
import com.fabricmanagement.common.platform.auth.domain.VerificationType;
import com.fabricmanagement.common.platform.user.domain.User;
import com.fabricmanagement.common.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Unified facade for all verification code operations (issuance, dispatch, and validation).
 *
 * <h2>Responsibilities:</h2>
 *
 * <ul>
 *   <li><b>Generic codes:</b> {@link #issueCode} delegates to {@link VerificationDispatcher}
 *       (throttle → generate → send).
 *   <li><b>MFA codes:</b> {@link #issueMfaCode} and {@link #validateMfaCode} fully encapsulate
 *       contact resolution and channel selection — callers only provide {@code userId} and {@link
 *       MfaType}.
 *   <li><b>Validation:</b> {@link #validateAndConsume} delegates to {@link
 *       VerificationCodeService}.
 * </ul>
 *
 * <p>Callers (e.g. {@code LoginService}) must <em>not</em> resolve contacts or determine {@link
 * VerificationType} themselves — this class owns that knowledge.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationCodeManager {

  private final VerificationDispatcher verificationDispatcher;
  private final VerificationCodeService verificationCodeService;
  private final UserRepository userRepository;

  // ── Generic code issuance ──────────────────────────────────────────────────────────────────────

  @Transactional
  public IssuedVerificationCode issueCode(String contactValue, VerificationType type) {
    return verificationDispatcher.sendVerificationCode(contactValue, type);
  }

  @Transactional
  public IssuedVerificationCode issueCode(
      String contactValue, VerificationType type, UUID tenantId, UUID userId) {
    return verificationDispatcher.sendVerificationCode(contactValue, type, tenantId, userId);
  }

  @Transactional
  public void validateAndConsume(String contactValue, VerificationType type, String rawCode) {
    verificationCodeService.validateAndConsume(contactValue, type, rawCode);
  }

  // ── MFA-specific facade ────────────────────────────────────────────────────────────────────────

  /**
   * Issue an MFA verification code for the given user and MFA method.
   *
   * <p>Resolves the user's verified contact, determines the correct {@link VerificationType}, and
   * dispatches the code via the appropriate channel (WhatsApp / SMS / Email) using market-based
   * routing. Callers do not need to know any of these details.
   *
   * @param userId the user who must verify
   * @param tenantId the tenant the user belongs to
   * @param mfaType the MFA channel configured for this user
   * @return result with the masked contact (e.g. "j***@gmail.com") and code expiry
   * @throws IllegalArgumentException if the user or a verified contact cannot be found
   */
  @Transactional
  public MfaCodeIssuanceResult issueMfaCode(UUID userId, UUID tenantId, MfaType mfaType) {
    VerificationType vType = resolveVerificationType(mfaType);
    String contactValue = resolveContactForMfa(userId, tenantId);

    IssuedVerificationCode issued =
        verificationDispatcher.sendVerificationCode(contactValue, vType, tenantId, userId);

    String maskedContact = maskContact(contactValue);
    log.info(
        "MFA code issued: userId={}, mfaType={}, maskedContact={}, expiresAt={}",
        userId,
        mfaType,
        maskedContact,
        issued.expiresAt());

    return new MfaCodeIssuanceResult(contactValue, maskedContact, vType, issued.expiresAt());
  }

  /**
   * Validate and consume an MFA code for the given user.
   *
   * <p>Resolves the verified contact and verification type internally. Throws if the code is
   * invalid, expired, or the attempt limit has been reached.
   *
   * @param userId the user being verified
   * @param tenantId tenant context
   * @param mfaType the MFA channel configured for this user
   * @param code the raw 6-digit code submitted by the user
   * @throws IllegalArgumentException on validation failure
   */
  @Transactional
  public void validateMfaCode(UUID userId, UUID tenantId, MfaType mfaType, String code) {
    VerificationType vType = resolveVerificationType(mfaType);
    String contactValue = resolveContactForMfa(userId, tenantId);
    verificationCodeService.validateAndConsume(contactValue, vType, code);
  }

  // ── Private helpers ────────────────────────────────────────────────────────────────────────────

  /**
   * Map {@link MfaType} to the appropriate {@link VerificationType}.
   *
   * <p>EMAIL → MFA_LOGIN_EMAIL; SMS / WHATSAPP → MFA_LOGIN_PHONE.
   */
  private VerificationType resolveVerificationType(MfaType mfaType) {
    return mfaType == MfaType.EMAIL
        ? VerificationType.MFA_LOGIN_EMAIL
        : VerificationType.MFA_LOGIN_PHONE;
  }

  /**
   * Resolve the primary verified contact value for MFA delivery.
   *
   * <p>Loads the user entity within the current tenant context and returns the first verified
   * contact. TOTP callers should not reach this path.
   */
  private String resolveContactForMfa(UUID userId, UUID tenantId) {
    User userEntity =
        userRepository
            .findByTenantIdAndId(tenantId, userId)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "User not found for MFA contact resolution: userId=" + userId));

    return userEntity
        .getAnyVerifiedContact()
        .map(contact -> contact.getContactValue())
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No verified contact found for MFA delivery: userId=" + userId));
  }

  private String maskContact(String contactValue) {
    if (contactValue == null) return null;
    return contactValue.contains("@")
        ? PiiMaskingUtil.maskEmail(contactValue)
        : PiiMaskingUtil.maskPhone(contactValue);
  }

  // ── Records ────────────────────────────────────────────────────────────────────────────────────

  /** Result of a successful MFA code issuance. */
  public record IssuedVerificationCode(String code, Instant expiresAt) {}

  /**
   * Result of {@link #issueMfaCode}. Contains everything the caller needs to build a user-facing
   * response without knowing which contact or channel was used.
   */
  public record MfaCodeIssuanceResult(
      String contactValue,
      String maskedContact,
      VerificationType verificationType,
      Instant expiresAt) {}
}
