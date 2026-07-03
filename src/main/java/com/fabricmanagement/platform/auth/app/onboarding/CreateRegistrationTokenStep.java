package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(8) // After CreateSubscriptionsStep (7)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateRegistrationTokenStep implements OnboardingStep {

  private final RegistrationTokenRepository tokenRepository;

  @Override
  public void execute(OnboardingContext context) {
    if (context.isExistingIdentity()) {
      log.debug(
          "CreateRegistrationTokenStep: skipping existing identity tenantId={}",
          context.getTenantId());
      return;
    }

    UUID userId = context.getUserId();
    UUID organizationId = context.getOrganizationId();
    String contactValue = context.getAdminContact();
    if (userId == null || organizationId == null || contactValue == null) {
      return;
    }
    RegistrationTokenType tokenType =
        context.isSalesLed() ? RegistrationTokenType.SALES_LED : RegistrationTokenType.SELF_SERVICE;
    RegistrationToken token = RegistrationToken.create(contactValue, tokenType);
    token.linkTo(userId, organizationId);
    tokenRepository.save(token);
    context.setRegistrationToken(token.getToken());
    log.debug("CreateRegistrationTokenStep: token created for userId={}", userId);
  }
}
