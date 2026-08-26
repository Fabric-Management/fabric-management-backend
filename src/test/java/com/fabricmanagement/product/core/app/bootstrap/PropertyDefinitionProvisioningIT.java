package com.fabricmanagement.product.core.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.onboarding.CloneTemplatePermissionsStep;
import com.fabricmanagement.platform.auth.app.onboarding.CloneTemplatePropertyDefinitionsStep;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingStep;
import com.fabricmanagement.platform.auth.app.onboarding.PublishSelfSignupCompletedStep;
import com.fabricmanagement.platform.auth.app.onboarding.SeedRegisteredTenantDemoStep;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PropertyDefinitionProvisioningIT extends AbstractIntegrationTest {

  @Autowired private List<OnboardingStep> onboardingSteps;
  @Autowired private CloneTemplatePropertyDefinitionsStep registryStep;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @Test
  void onboardingStepRunsInTheBindingSlotAndCopiesOnlySystemRows() {
    List<Class<?>> orderedTypes = onboardingSteps.stream().map(Object::getClass).toList();
    assertThat(orderedTypes)
        .containsSubsequence(
            CloneTemplatePermissionsStep.class,
            CloneTemplatePropertyDefinitionsStep.class,
            SeedRegisteredTenantDemoStep.class,
            PublishSelfSignupCompletedStep.class);

    insertTemplateCustomRow();
    UUID tenantId = insertTenant("onboarding");
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(tenantId);

    registryStep.execute(context);

    assertRegistryReadyWithoutCustomRows(tenantId);
  }

  @Test
  void bootstrapReferenceClonePathUsesTheMissingOnlyRegistryCopy() {
    insertTemplateCustomRow();
    UUID tenantId = insertTenant("bootstrap");

    tenantClonerService.cloneReferenceDataToTenant(tenantId);

    assertRegistryReadyWithoutCustomRows(tenantId);
  }

  private UUID insertTenant(String path) {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "PREG-" + path.toUpperCase(java.util.Locale.ROOT) + "-" + suffix,
              "preg-" + path + "-" + suffix,
              "Property Registry " + path);
          return null;
        });
    return tenantId;
  }

  private void insertTemplateCustomRow() {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
              INSERT INTO production.prod_property_definition (
                  id, tenant_id, uid, property_key, canonical_field_name,
                  semantic_role_default, dimension, data_type, unit_family,
                  allowed_unit_codes, description, system_defined, is_active,
                  created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'CUSTOM_TEMPLATE_ONLY',
                  'templateOnly', 'MEASUREMENT', 'templateOnly', 'DECIMAL', 'NONE',
                  '[]'::jsonb, 'Must never leave its owning tenant.', FALSE, TRUE,
                  NOW(), NOW(), 0
              )
              ON CONFLICT (tenant_id, property_key) DO NOTHING
              """,
              TenantContext.TEMPLATE_TENANT_ID);
          return null;
        });
  }

  private void assertRegistryReadyWithoutCustomRows(UUID tenantId) {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          Integer systemRows =
              jdbc.queryForObject(
                  "SELECT count(*) FROM production.prod_property_definition "
                      + "WHERE tenant_id = ? AND system_defined = TRUE",
                  Integer.class,
                  tenantId);
          Integer customRows =
              jdbc.queryForObject(
                  "SELECT count(*) FROM production.prod_property_definition "
                      + "WHERE tenant_id = ? AND property_key LIKE 'CUSTOM\\_%'",
                  Integer.class, tenantId);
          assertThat(systemRows).isEqualTo(6);
          assertThat(customRows).isZero();
          return null;
        });
  }
}
