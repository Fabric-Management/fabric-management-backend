package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central dual-write service for platform identities and tenant memberships. */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityProvisioningService {

  private final LoginIdentityRepository loginIdentityRepository;
  private final MembershipRepository membershipRepository;

  @Transactional
  public LoginIdentity provisionCredential(
      String email,
      String passwordHash,
      boolean mfaEnabled,
      MfaType mfaType,
      String mfaSecret,
      boolean emailVerified,
      UUID tenantId,
      UUID userId) {
    String normalizedEmail = normalizeEmailOrThrow(email);

    LoginIdentity identity;
    boolean existingIdentity;
    long membershipCount;

    var existingIdentityOpt = loginIdentityRepository.findByEmail(normalizedEmail);
    if (existingIdentityOpt.isPresent()) {
      identity = existingIdentityOpt.get();
      existingIdentity = true;
      membershipCount = membershipRepository.countByLoginIdentityId(identity.getId());
    } else {
      identity =
          LoginIdentity.builder()
              .email(normalizedEmail)
              .passwordHash(passwordHash)
              .isMfaEnabled(mfaEnabled)
              .primaryMfaType(mfaType != null ? mfaType : MfaType.NONE)
              .mfaSecret(mfaSecret)
              .isActive(true)
              .emailVerified(emailVerified)
              .failedLoginAttempts(0)
              .requiresPasswordReset(false)
              .build();
      identity = loginIdentityRepository.save(identity);
      existingIdentity = false;
      membershipCount = 0;
    }

    var existingMembershipForUser = membershipRepository.findByUserId(userId);
    if (existingMembershipForUser.isPresent()) {
      Membership membership = existingMembershipForUser.get();
      if (!membership.getLoginIdentityId().equals(identity.getId())) {
        throw new PlatformDomainException(
            "User is already linked to a different login identity",
            "AUTH_IDENTITY_MEMBERSHIP_CONFLICT",
            409);
      }
      return identity;
    }

    if (existingIdentity && membershipCount > 0) {
      identity.setRequiresPasswordReset(true);
      identity = loginIdentityRepository.save(identity);
      log.warn(
          "LoginIdentity collision for email={} tenantId={} userId={}; password reset required.",
          normalizedEmail,
          tenantId,
          userId);
    }

    var existingMembershipForTenant =
        membershipRepository.findByLoginIdentityIdAndTenantId(identity.getId(), tenantId);
    if (existingMembershipForTenant.isPresent()) {
      Membership membership = existingMembershipForTenant.get();
      if (!membership.getUserId().equals(userId)) {
        throw new PlatformDomainException(
            "Tenant membership is already linked to a different user",
            "AUTH_IDENTITY_MEMBERSHIP_CONFLICT",
            409);
      }
      return identity;
    }

    boolean firstMembership = membershipCount == 0;
    membershipRepository.save(
        Membership.builder()
            .loginIdentityId(identity.getId())
            .tenantId(tenantId)
            .userId(userId)
            .status(MembershipStatus.ACTIVE)
            .isDefault(firstMembership)
            .build());

    return identity;
  }

  @Transactional
  public void updatePassword(String email, String newPasswordHash) {
    String normalizedEmail = normalizeEmailOrThrow(email);
    LoginIdentity identity =
        loginIdentityRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Login identity not found", "AUTH_IDENTITY_NOT_FOUND", 404));

    identity.changePassword(newPasswordHash);
    loginIdentityRepository.save(identity);
  }

  @Transactional
  public void updatePasswordForUser(UUID userId, String newPasswordHash) {
    LoginIdentity identity = findIdentityByUserId(userId);
    identity.changePassword(newPasswordHash);
    loginIdentityRepository.save(identity);
  }

  @Transactional
  public void updateMfa(String email, boolean mfaEnabled, MfaType mfaType, String mfaSecret) {
    String normalizedEmail = normalizeEmailOrThrow(email);
    LoginIdentity identity =
        loginIdentityRepository
            .findByEmail(normalizedEmail)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Login identity not found", "AUTH_IDENTITY_NOT_FOUND", 404));
    updateMfa(identity, mfaEnabled, mfaType, mfaSecret);
  }

  @Transactional
  public void updateMfaForUser(UUID userId, boolean mfaEnabled, MfaType mfaType, String mfaSecret) {
    updateMfa(findIdentityByUserId(userId), mfaEnabled, mfaType, mfaSecret);
  }

  private LoginIdentity findIdentityByUserId(UUID userId) {
    Membership membership =
        membershipRepository
            .findByUserId(userId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Login membership not found", "AUTH_MEMBERSHIP_NOT_FOUND", 404));
    return loginIdentityRepository
        .findById(membership.getLoginIdentityId())
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Login identity not found", "AUTH_IDENTITY_NOT_FOUND", 404));
  }

  private void updateMfa(
      LoginIdentity identity, boolean mfaEnabled, MfaType mfaType, String mfaSecret) {
    identity.setIsMfaEnabled(mfaEnabled);
    identity.setPrimaryMfaType(mfaType != null ? mfaType : MfaType.NONE);
    identity.setMfaSecret(mfaSecret);
    loginIdentityRepository.save(identity);
  }

  private String normalizeEmailOrThrow(String email) {
    if (email == null || email.isBlank()) {
      throw new PlatformDomainException("Email is required", "AUTH_EMAIL_REQUIRED", 400);
    }
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
