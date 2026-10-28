package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.config.LoggingAsyncUncaughtExceptionHandler;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingStep;
import com.fabricmanagement.platform.auth.app.onboarding.PublishSelfSignupCompletedStep;
import com.fabricmanagement.platform.auth.app.onboarding.TenantOnboardingOrchestrator;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.testsupport.SensitiveLogCapture;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class SignupEmailLogSafetyTest {
  private static final String TOKEN = "b24788a1-62fe-40ad-899f-80ea7543a1cc";
  private static final String URL = "https://app.example.com/setup?token=" + TOKEN;
  private static final String PASSWORD = "Hawthorn!Secret27";
  @Mock private NotificationService notifications;
  @Mock private EmailTemplateRenderer renderer;
  @Mock private LocalizationService localisation;
  @Mock private ApplicationEventPublisher springEvents;
  @Mock private FrontendUrlProvider frontend;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void realSetupLinkReachesTheMessageButNotSuccessLogs(boolean salesLed) {
    var event = event(salesLed);
    arrangeMessage(salesLed);
    var listener = new SignupEmailListener(notifications, renderer, localisation);
    try (var logs = capture()) {
      listener.onSelfSignupCompleted(event);
      verify(notifications)
          .sendNotificationSync(event.getTenantId(), event.getRecipientEmail(), "Setup", URL);
      assertSafe(logs);
      assertThat(logs.text())
          .contains(
              "Signup email submitted",
              event.getEventId().toString(),
              event.getTenantId().toString());
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failureAndAsyncHandlerOmitProviderMessageCausesSuppressedPayloadAndArguments(
      boolean salesLed) throws Exception {
    var event = event(salesLed);
    arrangeMessage(salesLed);
    var failure =
        new IllegalStateException("SMTP echoed " + URL, new IllegalArgumentException(PASSWORD));
    failure.addSuppressed(new RuntimeException(TOKEN));
    doThrow(failure)
        .when(notifications)
        .sendNotificationSync(any(), anyString(), anyString(), anyString());
    var listener = new SignupEmailListener(notifications, renderer, localisation);
    try (var logs = capture()) {
      assertThatThrownBy(() -> listener.onSelfSignupCompleted(event)).isSameAs(failure);
      new LoggingAsyncUncaughtExceptionHandler()
          .handleUncaughtException(
              failure,
              SignupEmailListener.class.getMethod(
                  "onSelfSignupCompleted", SelfSignupCompletedEvent.class),
              event);
      assertSafe(logs);
      assertThat(logs.text())
          .contains(
              "Signup email submission failed", "failureType=", event.getEventId().toString());
      assertThat(logs.text()).doesNotContain("Signup email submitted:");
    }
  }

  @Test
  void publisherDoesNotInvokeAnEventsPotentiallySensitiveToString() {
    var event =
        new SelfSignupCompletedEvent(
            UUID.randomUUID(),
            "eleanor.hughes@example.com",
            "Eleanor",
            "Hughes",
            "Hawthorn Weaving Ltd",
            "GB123456789",
            "WEAVER",
            URL,
            false,
            List.of("FabricOS"),
            "PLAYGROUND",
            UUID.randomUUID(),
            "en-GB") {
          @Override
          public String toString() {
            return URL + PASSWORD;
          }
        };
    try (var logs = capture()) {
      new DomainEventPublisher(springEvents).publish(event);
      verify(springEvents).publishEvent(event);
      assertSafe(logs);
      assertThat(logs.text())
          .contains(event.getEventId().toString(), event.getTenantId().toString());
    }
  }

  @Test
  void publicationStepKeepsTheRealLinkInTheEventAndLogsOnlyItsIdentity() {
    var context = new OnboardingContext();
    context.setTenantId(UUID.randomUUID());
    context.setUserId(UUID.randomUUID());
    context.setAdminContact("eleanor.hughes@example.com");
    context.setRegistrationToken(TOKEN);
    when(frontend.buildUrl("/setup?token=" + TOKEN)).thenReturn(URL);
    try (var logs = capture()) {
      new PublishSelfSignupCompletedStep(frontend, new DomainEventPublisher(springEvents))
          .execute(context);
      var capture = ArgumentCaptor.forClass(SelfSignupCompletedEvent.class);
      verify(springEvents).publishEvent(capture.capture());
      assertThat(capture.getValue().getSetupUrl()).isEqualTo(URL);
      assertThat(context.getSetupUrl()).isEqualTo(URL);
      assertSafe(logs);
      assertThat(logs.text()).contains(capture.getValue().getEventId().toString());
    }
  }

  @Test
  void failedOnboardingStepDoesNotLogItsSecretExceptionMessage() {
    var context = new OnboardingContext();
    context.setTenantId(UUID.randomUUID());
    context.setOrganizationName("Hawthorn Weaving Ltd");
    var failure = new IllegalStateException(URL + PASSWORD);
    OnboardingStep step =
        current -> {
          throw failure;
        };
    var meters = new SimpleMeterRegistry();
    try (var logs = capture()) {
      var orchestrator = new TenantOnboardingOrchestrator(List.of(step), meters);
      assertThatThrownBy(() -> orchestrator.onboard(context)).isSameAs(failure);
      assertSafe(logs);
      assertThat(logs.text())
          .contains(
              "failureType=java.lang.IllegalStateException", context.getTenantId().toString());
    } finally {
      meters.close();
    }
  }

  private void arrangeMessage(boolean salesLed) {
    when(localisation.getMessage(anyString(), any(), any())).thenReturn("Setup");
    if (salesLed)
      when(renderer.renderWelcome(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(URL);
    else
      when(renderer.renderSetupPassword(anyString(), anyString(), anyString(), anyString()))
          .thenReturn(URL);
  }

  private SelfSignupCompletedEvent event(boolean salesLed) {
    return new SelfSignupCompletedEvent(
        UUID.randomUUID(),
        "eleanor.hughes@example.com",
        "Eleanor",
        "Hughes",
        "Hawthorn Weaving Ltd",
        "GB123456789",
        "WEAVER",
        URL,
        salesLed,
        List.of("FabricOS"),
        salesLed ? "SALES_LED" : "PLAYGROUND",
        UUID.randomUUID(),
        "en-GB");
  }

  private SensitiveLogCapture capture() {
    return new SensitiveLogCapture(
        SignupEmailListener.class,
        LoggingAsyncUncaughtExceptionHandler.class,
        DomainEventPublisher.class,
        PublishSelfSignupCompletedStep.class,
        TenantOnboardingOrchestrator.class);
  }

  private void assertSafe(SensitiveLogCapture logs) {
    assertThat(logs.text())
        .doesNotContain(TOKEN, TOKEN.substring(0, 8), URL, PASSWORD, "eleanor.hughes@example.com");
  }
}
