package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.tradingpartner.domain.event.PartnerUserCreatedEvent;
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
  private final RegistrationTokenRepository registrationTokenRepository;
  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;
  private final FrontendUrlProvider frontendUrlProvider;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPartnerUserCreated(PartnerUserCreatedEvent event) {
    try {
      if (authUserRepository.existsByUserId(event.getUserId())) {
        log.debug(
            "Skipping partner invitation for userId={}: AuthUser already exists",
            event.getUserId());
        return;
      }

      if (registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
          event.getContactValue(), java.time.Instant.now())) {
        log.debug(
            "Skipping partner invitation for userId={}: active registration token exists",
            event.getUserId());
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

    String subject = "You've been invited to the Partner Portal";
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
}
