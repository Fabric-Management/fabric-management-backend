package com.fabricmanagement.common.platform.auth.app.onboarding;

import com.fabricmanagement.common.platform.user.api.facade.UserFacade;
import com.fabricmanagement.common.platform.user.dto.UserDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3) // After CreateTenantStep (1) and CreateOrganizationStep (2)
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateAdminUserStep implements OnboardingStep {

  private final UserFacade userFacade;

  @Override
  public void execute(OnboardingContext context) {
    UserDto user = userFacade.createAdminUser(context.toCreateAdminUserRequest());
    context.setUserId(user.getId());
    context.setAdminContactValue(context.getAdminContact());
    log.debug("CreateAdminUserStep: userId={}", user.getId());
  }
}
