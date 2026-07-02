package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.tradingpartner.domain.event.PartnerUserCreatedEvent;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles partner user invitation emails when a tenant admin invites a partner portal user.
 *
 * <p>Uses the {@code partner-invitation.html} template and routes to {@code
 * /partner-portal/setup?token=...} instead of the internal {@code /setup} path.
 *
 * <p>Partner users are intentionally excluded from {@link
 * com.fabricmanagement.platform.auth.app.UserInvitationEventListener} to ensure they receive the
 * correct branding and URL.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerUserInvitationEventListener {

  private final AuthUserRepository authUserRepository;
  private final LoginIdentityRepository loginIdentityRepository;
  private final RegistrationTokenRepository registrationTokenRepository;
  private final IdentityProvisioningService identityProvisioningService;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final FrontendUrlProvider frontendUrlProvider;
  private final LocalizationService localizationService;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void provisionExistingIdentityMembership(PartnerUserCreatedEvent event) {
    if (shouldSkipInviteHandling(event)) {
      return;
    }

    String normalizedEmail = normalizeEmail(event.getContactValue());
    loginIdentityRepository
        .findByEmail(normalizedEmail)
        .ifPresent(identity -> provisionExistingIdentityInvite(event, identity));
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPartnerUserCreated(PartnerUserCreatedEvent event) {
    try {
      String normalizedEmail = normalizeEmail(event.getContactValue());
      if (loginIdentityRepository.findByEmail(normalizedEmail).isPresent()) {
        sendAddedToOrganizationEmail(event);
        return;
      }

      if (shouldSkipInviteHandling(event)) {
        return;
      }

      sendPartnerInvitationEmail(event);

    } catch (Exception e) {
      log.error(
          "Failed to send partner invitation email for userId={}: {}",
          event.getUserId(),
          e.getMessage(),
          e);
    }
  }

  private boolean shouldSkipInviteHandling(PartnerUserCreatedEvent event) {
    if (authUserRepository.existsByUserId(event.getUserId())) {
      log.debug(
          "Skipping partner invitation for userId={}: AuthUser already exists", event.getUserId());
      return true;
    }

    if (registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
        event.getContactValue(), java.time.Instant.now())) {
      log.debug(
          "Skipping partner invitation for userId={}: active registration token exists",
          event.getUserId());
      return true;
    }

    return false;
  }

  private void provisionExistingIdentityInvite(
      PartnerUserCreatedEvent event, LoginIdentity identity) {
    identityProvisioningService.provisionMembershipForExistingIdentity(
        event.getContactValue(), event.getTenantId(), event.getUserId());

    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          AuthUser authUser = AuthUser.create(event.getUserId(), identity.getPasswordHash());
          authUser.setTenantId(event.getTenantId());
          authUser.verify();
          authUserRepository.save(authUser);
          ensureAuthenticationContact(event);
        });
  }

  private void ensureAuthenticationContact(PartnerUserCreatedEvent event) {
    // Raw-value lookup on purpose — contacts are stored as-typed (no lowercasing); see
    // UserInvitationEventListener.ensureAuthenticationContact for the full rationale.
    Contact contact =
        contactService
            .findByValue(event.getContactValue())
            .orElseGet(
                () -> {
                  log.info(
                      "Creating contact for existing partner identity invite: {}",
                      PiiMaskingUtil.maskEmail(event.getContactValue()));
                  return contactService.createContact(
                      event.getContactValue(), null, "Primary", true, null);
                });

    contactService.verifyContact(contact.getId());

    if (!userContactAssignmentService.existsUserContact(event.getUserId(), contact.getId())) {
      userContactAssignmentService.assignContact(event.getUserId(), contact.getId(), true);
    } else {
      userContactAssignmentService.setAsDefault(event.getUserId(), contact.getId());
    }
  }

  private void sendPartnerInvitationEmail(PartnerUserCreatedEvent event) {
    RegistrationToken token =
        TenantContext.executeInTenantContext(
            event.getTenantId(),
            () -> {
              RegistrationToken regToken =
                  RegistrationToken.create(
                      event.getContactValue(), RegistrationTokenType.PARTNER_INVITED_USER);
              regToken.linkTo(event.getUserId(), event.getOrganizationId());
              regToken.setTenantId(event.getTenantId());
              return registrationTokenRepository.save(regToken);
            });

    String setupUrl =
        frontendUrlProvider.buildUrl("/partner-portal/setup?token=" + token.getToken());

    String displayName = event.getDisplayName() != null ? event.getDisplayName() : "";
    String firstName = displayName.contains(" ") ? displayName.split(" ")[0] : displayName;

    // Resolve locale from UserLocaleConfig cascade: user → tenant → EN
    var locale = localizationService.resolveLocaleForUser(event.getTenantId(), event.getUserId());
    String subject =
        localizationService.getMessage("email.partner.invitation.subject", null, locale);
    String message =
        emailTemplateRenderer.renderPartnerInvitation(
            firstName, event.getPartnerDisplayName(), event.getContactValue(), setupUrl);

    notificationService.sendNotificationSync(event.getContactValue(), subject, message);

    log.info(
        "Partner invitation email sent: userId={}, partnerId={}, contact={}",
        event.getUserId(),
        event.getPartnerId(),
        event.getContactValue().replaceAll("(.).*@", "$1***@"));
  }

  private void sendAddedToOrganizationEmail(PartnerUserCreatedEvent event) {
    String displayName = event.getDisplayName() != null ? event.getDisplayName() : "";
    String firstName = displayName.contains(" ") ? displayName.split(" ")[0] : displayName;
    String organizationName =
        event.getPartnerDisplayName() != null && !event.getPartnerDisplayName().isBlank()
            ? event.getPartnerDisplayName()
            : "your organization";

    var locale = localizationService.resolveLocaleForUser(event.getTenantId(), event.getUserId());
    String subject = localizationService.getMessage("email.added-to-org.subject", null, locale);
    String message =
        emailTemplateRenderer.renderAddedToOrganization(
            firstName, organizationName, event.getContactValue());

    notificationService.sendNotificationSync(event.getContactValue(), subject, message);

    log.info(
        "Partner added-to-organization email sent: userId={}, partnerId={}, contact={}",
        event.getUserId(),
        event.getPartnerId(),
        event.getContactValue().replaceAll("(.).*@", "$1***@"));
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
