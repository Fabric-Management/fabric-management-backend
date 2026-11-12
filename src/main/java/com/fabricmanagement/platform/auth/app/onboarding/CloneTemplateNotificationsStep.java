package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 13: give the new tenant the notification catalogue (templates and their wording).
 *
 * <p>The notification hub reads both per tenant. Without this step a tenant created after the seed
 * migrations has neither: every notification is skipped, and one that is not skipped would show its
 * raw translation key. It runs before the registered-demo seed, whose events are delivered to users
 * right after the transaction commits.
 */
@Order(13)
@Component
@RequiredArgsConstructor
@Slf4j
public class CloneTemplateNotificationsStep implements OnboardingStep {

  private final TenantClonerService tenantClonerService;

  @Override
  public void execute(OnboardingContext context) {
    UUID targetTenantId = context.getTenantId();
    if (targetTenantId == null) {
      log.warn("CloneTemplateNotificationsStep: tenantId is null, skipping.");
      return;
    }
    UUID templateTenantId = tenantClonerService.findTemplateTenantId();
    if (templateTenantId == null) {
      log.warn("CloneTemplateNotificationsStep: golden-template tenant not found.");
      return;
    }
    int templates =
        tenantClonerService.cloneNotificationDataInCallerTransaction(
            templateTenantId, targetTenantId);
    log.info(
        "CloneTemplateNotificationsStep: copied {} notification templates into {}.",
        templates,
        targetTenantId);
  }
}
