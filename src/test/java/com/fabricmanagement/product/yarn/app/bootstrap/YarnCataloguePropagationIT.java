package com.fabricmanagement.product.yarn.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.bootstrap.DevSeedDataRunner;
import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateBackfillRunner;
import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.auth.app.onboarding.CloneTemplatePropertyDefinitionsStep;
import com.fabricmanagement.platform.auth.app.onboarding.OnboardingContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.product.core.api.facade.PropertyRegistryFacade;
import com.fabricmanagement.product.core.app.bootstrap.PropertyDefinitionBackfillRunner;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.annotation.Order;

@ExtendWith(OutputCaptureExtension.class)
class YarnCataloguePropagationIT extends AbstractIntegrationTest {

  @Autowired private YarnCatalogueBackfillRunner runner;
  @Autowired private PropertyDefinitionBackfillRunner propertyDefinitionBackfillRunner;
  @Autowired private CloneTemplatePropertyDefinitionsStep onboardingRegistryStep;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private PropertyRegistryFacade propertyRegistryFacade;
  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @Test
  void runnerOrderIsStrictFromDevSeedThroughYarnCatalogue() throws NoSuchMethodException {
    Order devSeedOrder = listenerOrder(DevSeedDataRunner.class);
    Order permissionOrder = listenerOrder(PermissionTemplateBackfillRunner.class);
    Order propertyOrder = listenerOrder(PropertyDefinitionBackfillRunner.class);
    Order yarnOrder = listenerOrder(YarnCatalogueBackfillRunner.class);

    assertThat(devSeedOrder).isNotNull();
    assertThat(permissionOrder).isNotNull();
    assertThat(propertyOrder).isNotNull();
    assertThat(yarnOrder).isNotNull();
    assertThat(permissionOrder.value()).isGreaterThan(devSeedOrder.value());
    assertThat(propertyOrder.value()).isGreaterThan(permissionOrder.value());
    assertThat(yarnOrder.value()).isGreaterThan(propertyOrder.value());
  }

  private static Order listenerOrder(Class<?> runnerType) throws NoSuchMethodException {
    return runnerType.getDeclaredMethod("run").getAnnotation(Order.class);
  }

  @Test
  void repairsCompletePartialAndEmptyTenantsIdempotentlyAndRetainsCollisions(
      CapturedOutput output) {
    UUID complete = insertTenant("complete");
    UUID partial = insertTenant("partial");
    UUID empty = insertTenant("empty");
    UUID collision = insertTenant("collision");
    provisionRegistry(complete, partial, empty, collision);
    insertTemplateTenantRowsThatMustNotPropagate();

    tenantClonerService.copyMissingSystemYarnCatalogues(TenantContext.TEMPLATE_TENANT_ID, complete);
    tenantClonerService.copyMissingSystemYarnCatalogues(TenantContext.TEMPLATE_TENANT_ID, partial);
    tenantClonerService.copyMissingSystemYarnCatalogues(
        TenantContext.TEMPLATE_TENANT_ID, collision);
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "DELETE FROM production.prod_yarn_spinning_system "
                  + "WHERE tenant_id = ? AND code IN ('FRICTION', 'COMPACT')",
              partial);
          jdbc.update(
              "DELETE FROM production.prod_yarn_end_use "
                  + "WHERE tenant_id = ? AND code = 'EMBROIDERY'",
              partial);
          jdbc.update(
              "DELETE FROM production.prod_yarn_test_method "
                  + "WHERE tenant_id = ? AND code = 'ISO_17202'",
              partial);
          jdbc.update(
              "UPDATE production.prod_yarn_spinning_system SET is_active = FALSE "
                  + "WHERE tenant_id = ? AND code = 'RING'",
              complete);
          jdbc.update(
              """
              INSERT INTO production.prod_yarn_end_use (
                  id, tenant_id, uid, code, name, description, display_order,
                  system_defined, is_active, created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'MEDICAL', 'Medical',
                  'Tenant-owned end use', 90, FALSE, TRUE, NOW(), NOW(), 0
              )
              """,
              partial);
          jdbc.update(
              "DELETE FROM production.prod_yarn_spinning_system "
                  + "WHERE tenant_id = ? AND code = 'AIR_JET'",
              collision);
          jdbc.update(
              """
              INSERT INTO production.prod_yarn_spinning_system (
                  id, tenant_id, uid, code, name, description, display_order,
                  technology_family, system_defined, is_active, created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'AIR_JET',
                  'Tenant Air Jet', 'Collision evidence', 91, 'AIR_JET', FALSE, TRUE,
                  NOW(), NOW(), 0
              )
              """,
              collision);
          return null;
        });

    propertyDefinitionBackfillRunner.run();
    runner.run();

    assertCompleteSystemCatalogue(complete);
    assertCompleteSystemCatalogue(partial);
    assertCompleteSystemCatalogue(empty);
    assertThat(
            rowCount("prod_yarn_end_use", partial, "code = 'MEDICAL' AND system_defined = FALSE"))
        .isEqualTo(1);
    assertThat(isActive("prod_yarn_spinning_system", complete, "RING")).isFalse();
    assertThat(templateOnlyRowCount(complete, partial, empty, collision)).isZero();

    assertThat(
            rowCount(
                "prod_yarn_spinning_system",
                collision,
                "code = 'AIR_JET' AND system_defined = FALSE AND name = 'Tenant Air Jet'"))
        .isEqualTo(1);
    assertThat(
            rowCount(
                "prod_yarn_spinning_system",
                collision,
                "code = 'AIR_JET' AND system_defined = TRUE"))
        .isZero();
    assertThat(output.getAll())
        .contains("Yarn system catalogue collision retained for explicit repair")
        .contains("AIR_JET")
        .contains(collision.toString());

    TenantContext.executeInTenantContext(
        empty,
        () -> {
          propertyRegistryFacade.resolve(empty, YarnCatalogueSeeder.TWIST_PROPERTY_KEY);
          return null;
        });
    assertThat(
            rowCount("prod_yarn_test_method", empty, "applicable_property_key = 'YARN_TWIST_TPM'"))
        .isEqualTo(2);

    int before = totalCatalogueRows();
    runner.run();
    assertThat(totalCatalogueRows()).isEqualTo(before);
  }

  @Test
  void onboardingAndTenantSeederReferencePathsCopyRegistryBeforeSystemCatalogues() {
    insertTemplateTenantRowsThatMustNotPropagate();

    UUID onboardingTenant = insertTenant("onboarding");
    OnboardingContext context = new OnboardingContext();
    context.setTenantId(onboardingTenant);
    onboardingRegistryStep.execute(context);
    assertCompleteSystemCatalogue(onboardingTenant);
    assertNoTenantRows(onboardingTenant);

    UUID tenantSeederTenant = insertTenant("tenant-seeder");
    tenantClonerService.cloneReferenceDataToTenant(tenantSeederTenant);
    assertCompleteSystemCatalogue(tenantSeederTenant);
    assertNoTenantRows(tenantSeederTenant);
  }

  private void provisionRegistry(UUID... tenantIds) {
    for (UUID tenantId : tenantIds) {
      tenantClonerService.copyMissingSystemPropertyDefinitions(
          TenantContext.TEMPLATE_TENANT_ID, tenantId);
    }
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
              "YARN-" + path.toUpperCase(java.util.Locale.ROOT) + "-" + suffix,
              "yarn-" + path + "-" + suffix,
              "Yarn Catalogue " + path);
          return null;
        });
    return tenantId;
  }

  private void insertTemplateTenantRowsThatMustNotPropagate() {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              """
              INSERT INTO production.prod_yarn_spinning_system (
                  id, tenant_id, uid, code, name, display_order, technology_family,
                  system_defined, is_active, created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'TEMPLATE_ONLY_SPIN',
                  'Template Only Spin', 900, 'RING', FALSE, TRUE, NOW(), NOW(), 0
              ) ON CONFLICT (tenant_id, code) DO NOTHING
              """,
              TenantContext.TEMPLATE_TENANT_ID);
          jdbc.update(
              """
              INSERT INTO production.prod_yarn_end_use (
                  id, tenant_id, uid, code, name, display_order, system_defined,
                  is_active, created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'TEMPLATE_ONLY_USE',
                  'Template Only Use', 901, FALSE, TRUE, NOW(), NOW(), 0
              ) ON CONFLICT (tenant_id, code) DO NOTHING
              """,
              TenantContext.TEMPLATE_TENANT_ID);
          jdbc.update(
              """
              INSERT INTO production.prod_yarn_test_method (
                  id, tenant_id, uid, code, name, display_order, system_defined,
                  is_active, created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'TEMPLATE_ONLY_METHOD',
                  'Template Only Method', 902, FALSE, TRUE, NOW(), NOW(), 0
              ) ON CONFLICT (tenant_id, code) DO NOTHING
              """,
              TenantContext.TEMPLATE_TENANT_ID);
          return null;
        });
  }

  private void assertCompleteSystemCatalogue(UUID tenantId) {
    assertThat(systemRowCount("prod_yarn_spinning_system", tenantId))
        .isEqualTo(YarnCatalogueSeeder.spinningSystems().size());
    assertThat(systemRowCount("prod_yarn_end_use", tenantId))
        .isEqualTo(YarnCatalogueSeeder.endUses().size());
    assertThat(systemRowCount("prod_yarn_test_method", tenantId))
        .isEqualTo(YarnCatalogueSeeder.testMethods().size());
  }

  private void assertNoTenantRows(UUID tenantId) {
    assertThat(rowCount("prod_yarn_spinning_system", tenantId, "system_defined = FALSE")).isZero();
    assertThat(rowCount("prod_yarn_end_use", tenantId, "system_defined = FALSE")).isZero();
    assertThat(rowCount("prod_yarn_test_method", tenantId, "system_defined = FALSE")).isZero();
  }

  private int systemRowCount(String table, UUID tenantId) {
    return rowCount(table, tenantId, "system_defined = TRUE");
  }

  private int rowCount(String table, UUID tenantId, String predicate) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM production."
                    + table
                    + " WHERE tenant_id = ? AND "
                    + predicate,
                Integer.class,
                tenantId));
  }

  private boolean isActive(String table, UUID tenantId, String code) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "SELECT is_active FROM production."
                        + table
                        + " WHERE tenant_id = ? AND code = ?",
                    Boolean.class,
                    tenantId,
                    code)));
  }

  private int templateOnlyRowCount(UUID... tenantIds) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          int total = 0;
          for (UUID tenantId : tenantIds) {
            total +=
                jdbc.queryForObject(
                    """
                    SELECT
                      (SELECT count(*) FROM production.prod_yarn_spinning_system
                       WHERE tenant_id = ? AND code = 'TEMPLATE_ONLY_SPIN')
                      + (SELECT count(*) FROM production.prod_yarn_end_use
                         WHERE tenant_id = ? AND code = 'TEMPLATE_ONLY_USE')
                      + (SELECT count(*) FROM production.prod_yarn_test_method
                         WHERE tenant_id = ? AND code = 'TEMPLATE_ONLY_METHOD')
                    """,
                    Integer.class,
                    tenantId,
                    tenantId,
                    tenantId);
          }
          return total;
        });
  }

  private int totalCatalogueRows() {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                """
                SELECT
                  (SELECT count(*) FROM production.prod_yarn_spinning_system)
                  + (SELECT count(*) FROM production.prod_yarn_end_use)
                  + (SELECT count(*) FROM production.prod_yarn_test_method)
                """,
                Integer.class));
  }
}
