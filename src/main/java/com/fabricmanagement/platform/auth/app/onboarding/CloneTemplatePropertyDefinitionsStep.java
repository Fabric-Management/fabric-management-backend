package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Step 11: provision the code-owned Property Registry before demo data or publication. */
@Order(11)
@Component
@RequiredArgsConstructor
@Slf4j
public class CloneTemplatePropertyDefinitionsStep implements OnboardingStep {

  private final TenantClonerService tenantClonerService;

  @Override
  public void execute(OnboardingContext context) {
    UUID targetTenantId = context.getTenantId();
    if (targetTenantId == null) {
      log.warn("CloneTemplatePropertyDefinitionsStep: tenantId is null, skipping.");
      return;
    }
    UUID templateTenantId = tenantClonerService.findTemplateTenantId();
    if (templateTenantId == null) {
      log.warn("CloneTemplatePropertyDefinitionsStep: golden-template tenant not found.");
      return;
    }
    int inserted =
        tenantClonerService.copyMissingSystemPropertyDefinitions(templateTenantId, targetTenantId);
    log.info(
        "CloneTemplatePropertyDefinitionsStep: inserted {} missing system definitions into {}.",
        inserted,
        targetTenantId);
  }
}
