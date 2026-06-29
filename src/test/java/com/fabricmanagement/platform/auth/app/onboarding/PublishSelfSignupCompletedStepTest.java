package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.platform.auth.domain.event.SelfSignupCompletedEvent;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class PublishSelfSignupCompletedStepTest {

  @Mock private FrontendUrlProvider frontendUrlProvider;
  @Mock private DomainEventPublisher eventPublisher;

  @AfterEach
  void tearDown() {
    LocalizationContext.clear();
  }

  @Test
  void setsSetupUrlAndPublishesSelfSignupCompletedEvent() {
    UUID tenantId = UUID.randomUUID();
    PublishSelfSignupCompletedStep step =
        new PublishSelfSignupCompletedStep(frontendUrlProvider, eventPublisher);
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);
    context.setUserId(UUID.randomUUID());
    context.setRegistrationToken("setup-token");
    context.setAdminContact("owner@example.com");
    context.setAdminFirstName("Owner");
    context.setOrganizationName("Acme Textiles");
    context.setSalesLed(true);
    context.setSubscriptionOsCodes(List.of("FabricOS", "WarehouseOS"));
    LocalizationContext.setLocale("tr");
    when(frontendUrlProvider.buildUrl("/setup?token=setup-token"))
        .thenReturn("https://app.example.com/setup?token=setup-token");

    step.execute(context);

    assertThat(context.toResult().getSetupUrl())
        .isEqualTo("https://app.example.com/setup?token=setup-token");
    ArgumentCaptor<SelfSignupCompletedEvent> eventCaptor =
        ArgumentCaptor.forClass(SelfSignupCompletedEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    verifyNoMoreInteractions(eventPublisher);
    SelfSignupCompletedEvent event = eventCaptor.getValue();
    assertThat(event.getTenantId()).isEqualTo(tenantId);
    assertThat(event.getRecipientEmail()).isEqualTo("owner@example.com");
    assertThat(event.getFirstName()).isEqualTo("Owner");
    assertThat(event.getOrganizationName()).isEqualTo("Acme Textiles");
    assertThat(event.getSetupUrl()).isEqualTo("https://app.example.com/setup?token=setup-token");
    assertThat(event.isSalesLed()).isTrue();
    assertThat(event.getSubscriptionOsCodes()).containsExactly("FabricOS", "WarehouseOS");
    assertThat(event.getLocaleLanguageTag()).isEqualTo("tr");
  }

  @Test
  void missingTokenOrEmailLogsWarningAndDoesNotPublish(CapturedOutput output) {
    PublishSelfSignupCompletedStep step =
        new PublishSelfSignupCompletedStep(frontendUrlProvider, eventPublisher);
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(UUID.randomUUID());
    context.setUserId(UUID.randomUUID());
    context.setAdminContact("owner@example.com");

    step.execute(context);

    verify(eventPublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    assertThat(output).contains("missing token or recipient email, skipping signup email event");
  }
}
