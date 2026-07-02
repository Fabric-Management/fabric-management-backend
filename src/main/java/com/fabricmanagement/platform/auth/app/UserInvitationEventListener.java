package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.common.util.PiiMaskingUtil;
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
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.event.UserCreatedEvent;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens to UserCreatedEvent and automatically sends invitation emails to newly created users.
 *
 * <p>This ensures that users created via the internal/external user creation flow (not onboarding)
 * receive an invitation email with a registration link to set up their password.
 *
 * <p><b>Excluded scenarios:</b>
 *
 * <ul>
 *   <li>Admin users created during onboarding (handled by SignupEmailListener)
 *   <li>Users who already have an AuthUser (already registered)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserInvitationEventListener {

  private final AuthUserRepository authUserRepository;
  private final LoginIdentityRepository loginIdentityRepository;
  private final RegistrationTokenRepository registrationTokenRepository;
  private final IdentityProvisioningService identityProvisioningService;
  private final ContactService contactService;
  private final UserContactAssignmentService userContactAssignmentService;
  private final OrganizationService organizationService;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final FrontendUrlProvider frontendUrlProvider;
  private final LocalizationService localizationService;

  @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
  public void provisionExistingIdentityMembership(UserCreatedEvent event) {
    if (shouldSkipInviteHandling(event)) {
      return;
    }

    String normalizedEmail = normalizeEmail(event.getContactValue());
    loginIdentityRepository
        .findByEmail(normalizedEmail)
        .ifPresent(identity -> provisionExistingIdentityInvite(event, identity));
  }

  /**
   * Handle UserCreatedEvent - send invitation email if user doesn't have AuthUser yet.
   *
   * <p>Runs after the transaction commits to ensure user data is persisted before sending email.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserCreated(UserCreatedEvent event) {
    try {
      String normalizedEmail = normalizeEmail(event.getContactValue());
      if (loginIdentityRepository.findByEmail(normalizedEmail).isPresent()) {
        sendAddedToOrganizationEmail(event);
        return;
      }

      // Skip if user already has auth credentials (e.g., admin created during onboarding)
      if (shouldSkipInviteHandling(event)) {
        return;
      }

      // Create registration token and send invitation email
      sendInvitationEmail(event);

    } catch (Exception e) {
      // Don't fail user creation if invitation email fails
      log.error(
          "Failed to send invitation email for userId={}: {}",
          event.getUserId(),
          e.getMessage(),
          e);
    }
  }

  private boolean shouldSkipInviteHandling(UserCreatedEvent event) {
    if (authUserRepository.existsByUserId(event.getUserId())) {
      log.debug("Skipping invitation for userId={}: AuthUser already exists", event.getUserId());
      return true;
    }

    if (registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
        event.getContactValue(), java.time.Instant.now())) {
      log.debug(
          "Skipping invitation for userId={}: active registration token exists", event.getUserId());
      return true;
    }

    return false;
  }

  private void provisionExistingIdentityInvite(UserCreatedEvent event, LoginIdentity identity) {
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

  private void ensureAuthenticationContact(UserCreatedEvent event) {
    // Look up with the RAW value: contacts are stored as-typed (trim only, no lowercasing in
    // ContactService.normalizeContactValue), and this contact was just created in this same
    // transaction by the user-creation flow. A lowercased lookup would miss mixed-case values
    // and create a duplicate contact. (Identity lookups DO lowercase — login_identity is stored
    // normalized; contacts are not.)
    Contact contact =
        contactService
            .findByValue(event.getContactValue())
            .orElseGet(
                () -> {
                  log.info(
                      "Creating contact for existing identity invite: {}",
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

  private void sendInvitationEmail(UserCreatedEvent event) {
    // Create registration token within correct tenant context
    RegistrationToken token =
        TenantContext.executeInTenantContext(
            event.getTenantId(),
            () -> {
              RegistrationToken regToken =
                  RegistrationToken.create(
                      event.getContactValue(), RegistrationTokenType.INVITED_USER);
              regToken.linkTo(event.getUserId(), event.getOrganizationId());
              regToken.setTenantId(event.getTenantId());
              return registrationTokenRepository.save(regToken);
            });

    String setupUrl = frontendUrlProvider.buildUrl("/setup?token=" + token.getToken());
    String displayName = event.getDisplayName() != null ? event.getDisplayName() : "";
    String firstName = displayName.contains(" ") ? displayName.split(" ")[0] : displayName;

    // Resolve locale from UserLocaleConfig cascade: user → tenant → EN
    var locale = localizationService.resolveLocaleForUser(event.getTenantId(), event.getUserId());
    String subject = localizationService.getMessage("email.invitation.subject", null, locale);
    String message =
        emailTemplateRenderer.renderSetupPassword(firstName, "", event.getContactValue(), setupUrl);

    notificationService.sendNotificationSync(event.getContactValue(), subject, message);

    log.info(
        "Invitation email sent: userId={}, contact={}",
        event.getUserId(),
        event.getContactValue().replaceAll("(.).*@", "$1***@"));
  }

  private void sendAddedToOrganizationEmail(UserCreatedEvent event) {
    String displayName = event.getDisplayName() != null ? event.getDisplayName() : "";
    String firstName = displayName.contains(" ") ? displayName.split(" ")[0] : displayName;
    String organizationName =
        organizationService
            .findById(event.getTenantId(), event.getOrganizationId())
            .map(organization -> organization.getName())
            .orElse("your organization");

    var locale = localizationService.resolveLocaleForUser(event.getTenantId(), event.getUserId());
    String subject = localizationService.getMessage("email.added-to-org.subject", null, locale);
    String message =
        emailTemplateRenderer.renderAddedToOrganization(
            firstName, organizationName, event.getContactValue());

    notificationService.sendNotificationSync(event.getContactValue(), subject, message);

    log.info(
        "Added-to-organization email sent: userId={}, contact={}",
        event.getUserId(),
        event.getContactValue().replaceAll("(.).*@", "$1***@"));
  }

  private String normalizeEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
  }
}
