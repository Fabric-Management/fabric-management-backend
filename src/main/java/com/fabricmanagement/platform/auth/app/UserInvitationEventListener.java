package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.user.domain.event.UserCreatedEvent;
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
 *   <li>Admin users created during onboarding (handled by SendWelcomeEmailStep)
 *   <li>Users who already have an AuthUser (already registered)
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserInvitationEventListener {

  private final AuthUserRepository authUserRepository;
  private final RegistrationTokenRepository registrationTokenRepository;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final FrontendUrlProvider frontendUrlProvider;

  /**
   * Handle UserCreatedEvent - send invitation email if user doesn't have AuthUser yet.
   *
   * <p>Runs after the transaction commits to ensure user data is persisted before sending email.
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserCreated(UserCreatedEvent event) {
    try {
      // Skip if user already has auth credentials (e.g., admin created during onboarding)
      if (authUserRepository.existsByUserId(event.getUserId())) {
        log.debug("Skipping invitation for userId={}: AuthUser already exists", event.getUserId());
        return;
      }

      // Skip if active registration token already exists (e.g., onboarding flow already sent one)
      if (registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
          event.getContactValue(), java.time.Instant.now())) {
        log.debug(
            "Skipping invitation for userId={}: active registration token exists",
            event.getUserId());
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

    String subject = "You've been invited to FabricOS";
    String message =
        emailTemplateRenderer.renderSetupPassword(firstName, "", event.getContactValue(), setupUrl);

    notificationService.sendNotificationSync(event.getContactValue(), subject, message);

    log.info(
        "Invitation email sent: userId={}, contact={}",
        event.getUserId(),
        event.getContactValue().replaceAll("(.).*@", "$1***@"));
  }
}
