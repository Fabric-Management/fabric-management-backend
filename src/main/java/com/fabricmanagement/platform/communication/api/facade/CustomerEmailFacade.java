package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.EmailOutboxService;
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
  private final EmailOutboxService emailOutbox;

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

  /**
   * Sends a message about a sales order to the customer's contact. {@code bodyHtml} is markup the
   * caller built with every value escaped; {@code actionUrl} may be null for a message without a
   * button. The message is always queued in the outbox within the caller's transaction, whatever
   * {@code application.email.use-outbox} says: a rolled-back step sends nothing, and the caller
   * never holds its locks while a mail server answers. The outbox worker sends it after the commit.
   */
  public void sendOrderMessage(
      UUID tenantId,
      String recipient,
      String subject,
      String sellerName,
      String heading,
      String bodyHtml,
      String actionLabel,
      String actionUrl,
      String footnote) {
    String message =
        emailTemplateRenderer.renderOrderMessage(
            sellerName, heading, bodyHtml, actionLabel, actionUrl, footnote);
    emailOutbox.queueEmail(tenantId, recipient, subject, message);
  }
}
