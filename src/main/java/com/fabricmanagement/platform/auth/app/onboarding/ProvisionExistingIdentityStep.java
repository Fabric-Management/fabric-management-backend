package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Links a newly onboarded tenant admin to an already verified login identity. */
@Order(9)
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvisionExistingIdentityStep implements OnboardingStep {

  private final IdentityProvisioningService identityProvisioningService;
  private final AuthUserRepository authUserRepository;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;

  @Override
  public void execute(OnboardingContext context) {
    if (!context.isExistingIdentity()) {
      return;
    }

    UUID tenantId = context.getTenantId();
    UUID userId = context.getUserId();
    String email = context.getAdminContact();
    if (tenantId == null || userId == null || email == null || email.isBlank()) {
      log.warn(
          "ProvisionExistingIdentityStep: missing context tenantId={}, userId={}, emailPresent={}",
          tenantId,
          userId,
          email != null && !email.isBlank());
      return;
    }

    LoginIdentity identity =
        identityProvisioningService.provisionMembershipForExistingIdentity(email, tenantId, userId);

    TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          provisionAuthUserMirror(identity, tenantId, userId);
          ensureVerifiedAuthenticationContact(userId, email);
        });

    log.info(
        "ProvisionExistingIdentityStep: linked existing identity for tenantId={}, userId={}, email={}",
        tenantId,
        userId,
        PiiMaskingUtil.maskEmail(email));
  }

  private void provisionAuthUserMirror(LoginIdentity identity, UUID tenantId, UUID userId) {
    if (authUserRepository.existsByUserId(userId)) {
      return;
    }

    AuthUser authUser = AuthUser.create(userId, identity.getPasswordHash());
    authUser.setTenantId(tenantId);
    authUser.setIsMfaEnabled(Boolean.TRUE.equals(identity.getIsMfaEnabled()));
    authUser.setPrimaryMfaType(identity.getPrimaryMfaType());
    authUser.setMfaSecret(identity.getMfaSecret());
    authUser.verify();
    authUserRepository.save(authUser);
  }

  private void ensureVerifiedAuthenticationContact(UUID userId, String email) {
    Contact contact =
        contactService
            .findByValue(email)
            .orElseGet(() -> contactService.createContact(email, null, "Primary", true, null));

    contactService.verifyContact(contact.getId());

    if (!userContactAssignmentService.existsUserContact(userId, contact.getId())) {
      userContactAssignmentService.assignContact(userId, contact.getId(), true);
    } else {
      userContactAssignmentService.setAsDefault(userId, contact.getId());
    }
  }
}
