package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CreateRegistrationTokenStepTest {

  private final RegistrationTokenRepository tokenRepository =
      Mockito.mock(RegistrationTokenRepository.class);
  private final CreateRegistrationTokenStep step = new CreateRegistrationTokenStep(tokenRepository);

  @Test
  void skipsExistingIdentityOnboarding() {
    OnboardingContext context = new OnboardingContext();
    context.setExistingIdentity(true);
    context.setUserId(UUID.randomUUID());
    context.setOrganizationId(UUID.randomUUID());
    context.setAdminContact("owner@example.com");

    step.execute(context);

    verify(tokenRepository, never()).save(Mockito.any(RegistrationToken.class));
    assertThat(context.getRegistrationToken()).isNull();
  }

  @Test
  void createsTokenForRegularSignup() {
    OnboardingContext context = new OnboardingContext();
    context.setUserId(UUID.randomUUID());
    context.setOrganizationId(UUID.randomUUID());
    context.setAdminContact("owner@example.com");

    step.execute(context);

    ArgumentCaptor<RegistrationToken> tokenCaptor =
        ArgumentCaptor.forClass(RegistrationToken.class);
    verify(tokenRepository).save(tokenCaptor.capture());
    assertThat(tokenCaptor.getValue().getContactValue()).isEqualTo("owner@example.com");
    assertThat(context.getRegistrationToken()).isNotBlank();
  }
}
