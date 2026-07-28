package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Public cross-module API for sending customer-facing transactional email. */
@Component
@RequiredArgsConstructor
public class CustomerEmailFacade {

  private final NotificationService notificationService;
  private final EmailTemplateRenderer emailTemplateRenderer;

  public void sendQuoteApprovalEmail(
      UUID tenantId,
      String recipient,
      String subject,
      String heading,
      String body,
      String cta,
      String expires,
      String approvalUrl) {
    String message =
        emailTemplateRenderer.renderQuoteApproval(heading, body, cta, expires, approvalUrl);
    notificationService.sendNotificationSync(tenantId, recipient, subject, message);
  }
}
