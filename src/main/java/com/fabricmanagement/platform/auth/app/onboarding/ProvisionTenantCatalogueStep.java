package com.fabricmanagement.platform.auth.app.onboarding;

import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.platform.tenant.domain.port.CatalogueProvisioningReport;
import com.fabricmanagement.platform.tenant.domain.port.TenantCatalogueProvisioningPort;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Step 12: provision module-owned catalogues (TASK-TEMPLATE-TENANCY-1) from golden-template into
 * the new tenant, inside the onboarding transaction. Unlike the clone steps before it, a missing
 * source or a failure fails the onboarding: a tenant must not start without its catalogue.
 */
@Order(12)
@Component
@RequiredArgsConstructor
@Slf4j
public class ProvisionTenantCatalogueStep implements OnboardingStep {

  private final TenantClonerService tenantClonerService;
  private final TenantCatalogueProvisioningPort catalogueProvisioning;

  @Override
  public void execute(OnboardingContext context) {
    UUID targetTenantId = context.getTenantId();
    if (targetTenantId == null) {
      throw new IllegalStateException("ProvisionTenantCatalogueStep: tenantId is not set");
    }
    UUID templateTenantId = tenantClonerService.findTemplateTenantId();
    if (templateTenantId == null) {
      throw new IllegalStateException("ProvisionTenantCatalogueStep: golden-template not found");
    }
    CatalogueProvisioningReport report =
        catalogueProvisioning.provisionFromTemplate(templateTenantId, targetTenantId);
    log.info(
        "ProvisionTenantCatalogueStep: tenant={} inserted={} adopted={} findings={}",
        targetTenantId,
        report.inserted(),
        report.adopted(),
        report.findings().size());
  }
}
