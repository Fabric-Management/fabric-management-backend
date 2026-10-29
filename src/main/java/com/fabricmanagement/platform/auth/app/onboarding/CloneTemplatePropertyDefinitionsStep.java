package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Step 11: provision the Property Registry, then dependent yarn catalogues. */
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
    // On the onboarding transaction (ONBOARD-ATOMIC-1): the template is read through the system
    // connection, the copies are written on the onboarding connection.
    int inserted =
        tenantClonerService.copyMissingSystemPropertyDefinitionsInCallerTransaction(
            templateTenantId, targetTenantId);
    int yarnCataloguesInserted =
        tenantClonerService.copyMissingSystemYarnCataloguesInCallerTransaction(
            templateTenantId, targetTenantId);
    int fabricCataloguesInserted =
        tenantClonerService.copyMissingSystemFabricCataloguesInCallerTransaction(
            templateTenantId, targetTenantId);
    log.info(
        "CloneTemplatePropertyDefinitionsStep: inserted {} property definitions, {} yarn "
            + "catalogue rows and {} fabric catalogue rows into {}.",
        inserted,
        yarnCataloguesInserted,
        fabricCataloguesInserted,
        targetTenantId);
  }
}
