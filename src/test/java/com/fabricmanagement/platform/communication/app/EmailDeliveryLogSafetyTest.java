package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.LoggingAsyncUncaughtExceptionHandler;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.platform.communication.domain.EmailOutbox;
import com.fabricmanagement.platform.communication.domain.EmailOutboxStatus;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.platform.communication.infra.repository.EmailOutboxRepository;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryLogSafetyTest {
  private static final String TOKEN = "b24788a1-62fe-40ad-899f-80ea7543a1cc";
  private static final String URL = "https://app.example.com/setup?token=" + TOKEN;
  private static final String PASSWORD = "Hawthorn!Secret27";
  private static final String RECIPIENT = "eleanor.hughes@example.com";
  @Mock private JavaMailSender sender;
  @Mock private EmailStrategy strategy;
  @Mock private EmailOutboxService outbox;
  @Mock private EmailOutboxRepository rows;
  @Mock private EmailRecipientPolicy recipients;
  @Mock private SystemTransactionExecutor system;

  @Test
  void smtpFailureLogsMetadataWithoutProviderBodyOrCause() {
    var delivery = new EmailStrategy(sender, new MailProperties());
    ReflectionTestUtils.setField(delivery, "fromEmail", "support@example.com");
    ReflectionTestUtils.setField(delivery, "fromName", "FabricOS");
    var mime = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(mime);
    doThrow(secretFailure()).when(sender).send(any(MimeMessage.class));
    try (var logs = new SensitiveLogCapture(EmailStrategy.class)) {
      assertThatThrownBy(() -> delivery.sendEmail(RECIPIENT, "Set up your account", URL))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Email sending failed");
      assertSafe(logs);
      assertThat(logs.text()).contains("failureType=", "MailSendException");
    }
  }

  @Test
  void successfulSmtpStillReceivesTheSetupLink() throws Exception {
    var delivery = new EmailStrategy(sender, new MailProperties());
    ReflectionTestUtils.setField(delivery, "fromEmail", "support@example.com");
    ReflectionTestUtils.setField(delivery, "fromName", "FabricOS");
    var mime = new MimeMessage(Session.getInstance(new Properties()));
    when(sender.createMimeMessage()).thenReturn(mime);
    try (var logs = new SensitiveLogCapture(EmailStrategy.class)) {
      delivery.sendEmail(RECIPIENT, "Set up your account", URL);
      verify(sender).send(mime);
      assertThat(mime.getContent().toString()).contains(URL);
      assertSafe(logs);
    }
  }

  @Test
  void asyncNotificationFailureAndUncaughtArgumentsAreSafe() throws Exception {
    UUID tenant = UUID.randomUUID();
    var notifications = new NotificationService(strategy, outbox, recipients);
    ReflectionTestUtils.setField(notifications, "useOutbox", true);
    var failure = secretFailure();
    when(outbox.queueEmail(tenant, RECIPIENT, "Setup", URL)).thenThrow(failure);
    try (var logs =
        new SensitiveLogCapture(
            NotificationService.class, LoggingAsyncUncaughtExceptionHandler.class)) {
      notifications.sendNotification(tenant, RECIPIENT, "Setup", URL);
      var handler = new LoggingAsyncUncaughtExceptionHandler();
      handler.handleUncaughtException(
          failure,
          NotificationService.class.getMethod(
              "sendNotification", UUID.class, String.class, String.class, String.class),
          tenant,
          RECIPIENT,
          "Setup",
          URL);
      handler.handleUncaughtException(
          failure,
          NotificationService.class.getMethod(
              "sendNotification", String.class, String.class, String.class),
          RECIPIENT,
          "Setup",
          URL);
      assertSafe(logs);
      assertThat(logs.text())
          .contains("Notification submission failed", "Sensitive async operation failed");
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void outboxRetryAndDeadLetterErrorsContainOnlyFailureMetadata(boolean permanent) {
    var meters = new SimpleMeterRegistry();
    try {
      var queue = new EmailOutboxService(rows, strategy, recipients, system, meters);
      queue.registerMetrics();
      ReflectionTestUtils.setField(queue, "deadLetterAlertThreshold", 100);
      var email = EmailOutbox.create(RECIPIENT, "Setup", URL);
      email.setId(UUID.randomUUID());
      email.setTenantId(UUID.randomUUID());
      email.setMaxRetries(permanent ? 1 : 3);
      when(rows.save(any(EmailOutbox.class))).thenAnswer(call -> call.getArgument(0));
      doThrow(secretFailure()).when(strategy).sendEmail(anyString(), anyString(), anyString());
      try (var logs = new SensitiveLogCapture(EmailOutboxService.class)) {
        ReflectionTestUtils.invokeMethod(queue, "processEmail", email);
        assertSafe(logs);
        assertThat(email.getLastError())
            .isEqualTo("failureType=org.springframework.mail.MailSendException");
        assertThat(email.getHtmlBody()).isEqualTo(URL);
        assertThat(email.getRetryCount()).isEqualTo(1);
        assertThat(email.getStatus())
            .isEqualTo(permanent ? EmailOutboxStatus.FAILED : EmailOutboxStatus.PENDING);
        assertThat(logs.text()).contains("failureType=");
      }
    } finally {
      meters.close();
    }
  }

  private MailSendException secretFailure() {
    var failure =
        new MailSendException("SMTP echoed " + URL, new IllegalArgumentException(PASSWORD));
    failure.addSuppressed(new IllegalStateException(TOKEN));
    return failure;
  }

  private void assertSafe(SensitiveLogCapture logs) {
    assertThat(logs.text()).doesNotContain(TOKEN, TOKEN.substring(0, 8), URL, PASSWORD);
  }
}
