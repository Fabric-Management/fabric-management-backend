package com.fabricmanagement.platform.auth.app;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

class SignupEmailAfterCommitIntegrationTest extends AbstractIntegrationTest {

  @Autowired private DomainEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;

  @MockBean private NotificationService notificationService;
  @MockBean private EmailTemplateRenderer emailTemplateRenderer;

  @Test
  void rollbackPreventsSignupEmailListenerDelivery() {
    SelfSignupCompletedEvent event =
        new SelfSignupCompletedEvent(
            UUID.randomUUID(),
            "rollback@example.com",
            "Rollback",
            "Owner",
            "Rollback Textiles",
            "ROLL-123",
            "SPINNER",
            "https://app.example.com/setup?token=rollback",
            false,
            List.of("FabricOS"),
            "PLAYGROUND",
            UUID.randomUUID(),
            "en");

    transactionTemplate.executeWithoutResult(
        status -> {
          eventPublisher.publish(event);
          status.setRollbackOnly();
        });

    verify(notificationService, after(500).never())
        .sendNotificationSync(anyString(), anyString(), anyString());
  }
}
