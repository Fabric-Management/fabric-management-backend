package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.dto.MfaSetupResponse;
import com.fabricmanagement.platform.auth.dto.MfaStatusResponse;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.TrustedDeviceRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MFA Setup Service - Manages MFA configuration for users.
 *
 * <p>Handles TOTP, EMAIL, SMS, and WHATSAPP MFA setup.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MfaSetupService {

  private final AuthUserRepository authUserRepository;
  private final TrustedDeviceRepository trustedDeviceRepository;
  private final TotpMfaService totpMfaService;
  private final UserRepository userRepository;
  private final LocalizationService localizationService;
  private final IdentityProvisioningService identityProvisioningService;

  /**
   * Initiate MFA setup for user.
   *
   * @param tenantId Tenant ID
   * @param userId User ID
   * @param mfaType MFA type to enable
   * @return Setup response with secret/QR code for TOTP
   */
  @Transactional
  public MfaSetupResponse setupMfa(UUID tenantId, UUID userId, MfaType mfaType) {
    log.info("Setting up MFA: userId={}, mfaType={}", userId, mfaType);

    AuthUser authUser =
        authUserRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "AUTH_USER_NOT_FOUND", 404));

    if (mfaType == MfaType.TOTP) {
      return setupTotpMfa(authUser);
    } else if (mfaType == MfaType.EMAIL || mfaType == MfaType.SMS || mfaType == MfaType.WHATSAPP) {
      return setupOtpMfa(authUser, mfaType);
    } else {
      throw new PlatformDomainException(
          "Invalid MFA type: " + mfaType, "AUTH_INVALID_MFA_TYPE", 400);
    }
  }

  /**
   * Setup TOTP MFA.
   *
   * @param authUser Auth user
   * @return Setup response with secret and QR code
   */
  private MfaSetupResponse setupTotpMfa(AuthUser authUser) {
    String secret = totpMfaService.generateSecret();

    String accountName = authUser.getUserId().toString();
    try {
      User userEntity =
          userRepository
              .findByTenantIdAndId(authUser.getTenantId(), authUser.getUserId())
              .orElse(null);
      if (userEntity != null) {
        accountName =
            userEntity
                .getAnyVerifiedContact()
                .map(contact -> contact.getContactValue())
                .orElse(authUser.getUserId().toString());
      }
    } catch (Exception e) {
      log.warn("Failed to retrieve user contact for TOTP account name, using ID", e);
    }

    String qrCodeUri = totpMfaService.getQrCodeImageUri(secret, accountName, "Fabric Management");

    authUser.setMfaSecret(secret);
    authUser.setPrimaryMfaType(MfaType.TOTP);
    authUser.setIsMfaEnabled(false);

    authUserRepository.save(authUser);
    identityProvisioningService.updateMfaForUser(
        authUser.getUserId(), false, authUser.getPrimaryMfaType(), authUser.getMfaSecret());

    log.info("TOTP MFA setup initiated for user: {}", authUser.getUserId());

    return MfaSetupResponse.builder()
        .mfaType(MfaType.TOTP)
        .secret(secret)
        .qrCodeUri(qrCodeUri)
        .message(localizationService.getMessage("mfa.setup.totp.initiated", null))
        .build();
  }

  /**
   * Setup OTP-based MFA (EMAIL, SMS, WHATSAPP).
   *
   * @param authUser Auth user
   * @param mfaType MFA type
   * @return Setup response
   */
  private MfaSetupResponse setupOtpMfa(AuthUser authUser, MfaType mfaType) {
    authUser.setPrimaryMfaType(mfaType);
    authUser.setIsMfaEnabled(false);
    authUser.setMfaSecret(null);

    authUserRepository.save(authUser);
    identityProvisioningService.updateMfaForUser(
        authUser.getUserId(), false, authUser.getPrimaryMfaType(), authUser.getMfaSecret());

    log.info("{} MFA setup initiated for user: {}", mfaType, authUser.getUserId());

    return MfaSetupResponse.builder()
        .mfaType(mfaType)
        .message(
            localizationService.getMessage(
                "mfa.setup.otp.initiated", new Object[] {mfaType, mfaType}))
        .build();
  }

  /**
   * Confirm MFA setup by verifying code.
   *
   * @param tenantId Tenant ID
   * @param userId User ID
   * @param code Verification code
   */
  @Transactional
  public void confirmMfaSetup(UUID tenantId, UUID userId, String code) {
    log.info("Confirming MFA setup: userId={}", userId);

    AuthUser authUser =
        authUserRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "AUTH_USER_NOT_FOUND", 404));

    if (authUser.getPrimaryMfaType() == null || authUser.getPrimaryMfaType() == MfaType.NONE) {
      throw new PlatformDomainException("MFA setup not initiated", "AUTH_MFA_NOT_INITIATED", 400);
    }

    if (authUser.getPrimaryMfaType() == MfaType.TOTP) {
      if (authUser.getMfaSecret() == null) {
        throw new PlatformDomainException("TOTP secret not found", "AUTH_MFA_SECRET_MISSING", 400);
      }

      boolean isValid = totpMfaService.verifyCode(authUser.getMfaSecret(), code);
      if (!isValid) {
        throw new PlatformDomainException("Invalid TOTP code", "AUTH_MFA_INVALID_CODE", 400);
      }
    } else {
      throw new PlatformDomainException(
          "Confirmation not required for " + authUser.getPrimaryMfaType(),
          "AUTH_MFA_CONFIRM_NOT_REQUIRED",
          400);
    }

    authUser.setIsMfaEnabled(true);
    authUserRepository.save(authUser);
    identityProvisioningService.updateMfaForUser(
        authUser.getUserId(), true, authUser.getPrimaryMfaType(), authUser.getMfaSecret());

    log.info("✅ MFA enabled for user: {}, type={}", userId, authUser.getPrimaryMfaType());
  }

  /**
   * Disable MFA for user.
   *
   * @param tenantId Tenant ID
   * @param userId User ID
   */
  @Transactional
  public void disableMfa(UUID tenantId, UUID userId) {
    log.info("Disabling MFA: userId={}", userId);

    AuthUser authUser =
        authUserRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "AUTH_USER_NOT_FOUND", 404));

    authUser.setIsMfaEnabled(false);
    authUser.setPrimaryMfaType(MfaType.NONE);
    authUser.setMfaSecret(null);

    authUserRepository.save(authUser);
    identityProvisioningService.updateMfaForUser(
        authUser.getUserId(), false, authUser.getPrimaryMfaType(), authUser.getMfaSecret());

    trustedDeviceRepository.deleteByUserId(userId);

    log.info("✅ MFA disabled for user: {}", userId);
  }

  /**
   * Get MFA status for user.
   *
   * @param tenantId Tenant ID
   * @param userId User ID
   * @return MFA status
   */
  @Transactional(readOnly = true)
  public MfaStatusResponse getMfaStatus(UUID tenantId, UUID userId) {
    AuthUser authUser =
        authUserRepository
            .findByTenantIdAndUserId(tenantId, userId)
            .orElseThrow(
                () -> new PlatformDomainException("User not found", "AUTH_USER_NOT_FOUND", 404));

    int trustedDeviceCount = trustedDeviceRepository.countByUserId(userId);

    return MfaStatusResponse.builder()
        .isMfaEnabled(authUser.getIsMfaEnabled())
        .primaryMfaType(authUser.getPrimaryMfaType())
        .trustedDeviceCount(trustedDeviceCount)
        .build();
  }
}
