package com.fabricmanagement.platform.communication.app;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * NotificationService no longer decides anything about playgrounds.
 *
 * <p>It used to drop the email when {@code tenant.type == PLAYGROUND} — a check that never fired,
 * because the supported register-first playground carries {@code type=REGULAR}, and which dropped
 * mail the prospect was meant to receive. Both concerns now live in {@link EmailRecipientPolicy},
 * applied inside {@code queueEmail} for the outbox path and in {@code sendDirectly} for the legacy
 * one. See EMAIL-SANDBOX-1.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService (Unit Test)")
class NotificationServiceTest {

  @Mock private EmailStrategy emailStrategy;
  @Mock private EmailOutboxService emailOutboxService;
  @Mock private EmailRecipientPolicy emailRecipientPolicy;

  @InjectMocks private NotificationService notificationService;

  private final UUID TENANT_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    ReflectionTestUtils.setField(notificationService, "useOutbox", true);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("sendNotification")
  class SendNotification {

    @Test
    @DisplayName("hands the email to the outbox untouched; the outbox applies the sandbox")
    void queuesAsync() {
      notificationService.sendNotification("test@test.com", "Subject", "Message");

      verify(emailOutboxService).queueEmail(eq("test@test.com"), eq("Subject"), eq("Message"));
      verify(emailStrategy, never()).sendEmail(anyString(), anyString(), anyString());
      verify(emailRecipientPolicy, never()).resolve(anyString(), anyString());
    }

    @Test
    @DisplayName("sync path also goes through the outbox")
    void queuesSync() {
      notificationService.sendNotificationSync("test@test.com", "Subject", "Message");

      verify(emailOutboxService).queueEmail(eq("test@test.com"), eq("Subject"), eq("Message"));
      verify(emailStrategy, never()).sendEmail(anyString(), anyString(), anyString());
    }
  }

  @Nested
  @DisplayName("legacy direct-send path (use-outbox=false)")
  class DirectSend {

    @BeforeEach
    void disableOutbox() {
      ReflectionTestUtils.setField(notificationService, "useOutbox", false);
    }

    @Test
    @DisplayName("a sandboxed tenant's email is redirected, not dropped")
    void redirectsWhenSandboxed() {
      org.mockito.Mockito.when(emailRecipientPolicy.resolve("customer@mill.com", "Subject"))
          .thenReturn(
              new EmailRecipientPolicy.Resolution(
                  false, "prospect@acme.co.uk", "customer@mill.com", "[Playground] Subject"));

      notificationService.sendNotificationSync("customer@mill.com", "Subject", "Message");

      verify(emailStrategy).sendEmail("prospect@acme.co.uk", "[Playground] Subject", "Message");
    }

    @Test
    @DisplayName("a dropped resolution never reaches the mail sender")
    void doesNotSendWhenDropped() {
      org.mockito.Mockito.when(emailRecipientPolicy.resolve("customer@mill.com", "Subject"))
          .thenReturn(
              new EmailRecipientPolicy.Resolution(true, null, "customer@mill.com", "Subject"));

      notificationService.sendNotificationSync("customer@mill.com", "Subject", "Message");

      verify(emailStrategy, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a real tenant's email passes through")
    void passesThroughForRealTenant() {
      org.mockito.Mockito.when(emailRecipientPolicy.resolve("customer@mill.com", "Subject"))
          .thenReturn(
              new EmailRecipientPolicy.Resolution(false, "customer@mill.com", null, "Subject"));

      notificationService.sendNotificationSync("customer@mill.com", "Subject", "Message");

      verify(emailStrategy).sendEmail("customer@mill.com", "Subject", "Message");
    }
  }
}
