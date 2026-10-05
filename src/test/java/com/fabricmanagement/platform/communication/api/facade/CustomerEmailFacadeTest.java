package com.fabricmanagement.platform.communication.api.facade;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerEmailFacadeTest {

  @Mock private NotificationService notificationService;
  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private EmailOutboxService emailOutbox;

  @Test
  void rendersAndSendsQuoteApprovalEmailWithoutExposingCommunicationInternals() {
    UUID tenantId = UUID.randomUUID();
    CustomerEmailFacade facade =
        new CustomerEmailFacade(notificationService, emailTemplateRenderer, emailOutbox);
    when(emailTemplateRenderer.renderQuoteApproval(
            "Heading", "Body", "Review", "Expires soon", "https://example.com/approve"))
        .thenReturn("<p>Rendered</p>");

    facade.sendQuoteApprovalEmail(
        tenantId,
        "buyer@example.com",
        "Quote ready",
        "Heading",
        "Body",
        "Review",
        "Expires soon",
        "https://example.com/approve");

    verify(notificationService)
        .sendNotificationSync(tenantId, "buyer@example.com", "Quote ready", "<p>Rendered</p>");
  }

  @Test
  void anOrderMessageIsAlwaysQueuedInTheCallersTransactionNeverSentDirectly() {
    UUID tenantId = UUID.randomUUID();
    CustomerEmailFacade facade =
        new CustomerEmailFacade(notificationService, emailTemplateRenderer, emailOutbox);
    when(emailTemplateRenderer.renderOrderMessage(
            "Bradford Mills", "Your code", "<p>123456</p>", null, null, null))
        .thenReturn("<p>Rendered</p>");

    facade.sendOrderMessage(
        tenantId,
        "jane@example.com",
        "Your code",
        "Bradford Mills",
        "Your code",
        "<p>123456</p>",
        null,
        null,
        null);

    verify(emailOutbox).queueEmail(tenantId, "jane@example.com", "Your code", "<p>Rendered</p>");
    verifyNoInteractions(notificationService);
  }
}
