package com.fabricmanagement.platform.communication.api.facade;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

  @Test
  void rendersAndSendsQuoteApprovalEmailWithoutExposingCommunicationInternals() {
    UUID tenantId = UUID.randomUUID();
    CustomerEmailFacade facade =
        new CustomerEmailFacade(notificationService, emailTemplateRenderer);
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
}
