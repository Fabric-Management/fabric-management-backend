package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.common.platform.auth.domain.RegistrationTokenType;
import com.fabricmanagement.common.platform.auth.infra.repository.RegistrationTokenRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(7) // After SeedOrganizationStep (6)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateRegistrationTokenStep implements OnboardingStep {

  private final RegistrationTokenRepository tokenRepository;

  @Override
  public void execute(OnboardingContext context) {
    UUID userId = context.getUserId();
    UUID companyId = context.getCompanyId();
    String contactValue = context.getAdminContact();
    if (userId == null || companyId == null || contactValue == null) {
      return;
    }
    RegistrationTokenType tokenType =
        context.isSalesLed() ? RegistrationTokenType.SALES_LED : RegistrationTokenType.SELF_SERVICE;
    RegistrationToken token = RegistrationToken.create(contactValue, tokenType);
    token.linkTo(userId, companyId);
    tokenRepository.save(token);
    context.setRegistrationToken(token.getToken());
    log.debug("CreateRegistrationTokenStep: token created for userId={}", userId);
  }
}
