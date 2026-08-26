package com.fabricmanagement.product.core.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tenant.app.TenantClonerService;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PropertyDefinitionBackfillIT extends AbstractIntegrationTest {

  @Autowired private PropertyDefinitionBackfillRunner runner;
  @Autowired private TenantClonerService tenantClonerService;
  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @Test
  void repairsCompletePartialAndEmptyTenantsWithoutCopyingCustomRows() {
    UUID complete = UUID.randomUUID();
    UUID partial = UUID.randomUUID();
    UUID empty = UUID.randomUUID();
    insertTenant(complete, "complete");
    insertTenant(partial, "partial");
    insertTenant(empty, "empty");
    insertTemplateCustomRow();

    tenantClonerService.copyMissingSystemPropertyDefinitions(
        TenantContext.TEMPLATE_TENANT_ID, complete);
    tenantClonerService.copyMissingSystemPropertyDefinitions(
        TenantContext.TEMPLATE_TENANT_ID, partial);
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "DELETE FROM production.prod_property_definition WHERE tenant_id = ? "
                  + "AND property_key IN ('YARN_ELONGATION', 'YARN_EVENNESS_CVM')",
              partial);
          jdbc.update(
              "UPDATE production.prod_property_definition SET is_active = FALSE "
                  + "WHERE tenant_id = ? AND property_key = 'YARN_CSP'",
              complete);
          jdbc.update(
              """
              INSERT INTO production.prod_property_definition (
                  id, tenant_id, uid, property_key, canonical_field_name,
                  semantic_role_default, dimension, data_type, unit_family,
                  allowed_unit_codes, description, system_defined, is_active,
                  created_at, updated_at, version
              ) VALUES (
                  gen_random_uuid(), ?, gen_random_uuid()::varchar, 'CUSTOM_SENSOR_VALUE',
                  'sensorValue', 'MEASUREMENT', 'sensorValue', 'DECIMAL', 'NONE',
                  '[]'::jsonb, 'Tenant-owned sensor value.', FALSE, TRUE, NOW(), NOW(), 0
              )
              """,
              partial);
          return null;
        });

    runner.run();

    assertThat(systemCount(complete)).isEqualTo(6);
    assertThat(systemCount(partial)).isEqualTo(6);
    assertThat(systemCount(empty)).isEqualTo(6);
    assertThat(customCount(complete)).isZero();
    assertThat(customCount(partial)).isEqualTo(1);
    assertThat(customCount(empty)).isZero();
    assertThat(isActive(complete, "YARN_CSP")).isFalse();

    int before = totalCount();
    runner.run();
    assertThat(totalCount()).isEqualTo(before);
    assertThat(customCount(partial)).isEqualTo(1);
  }

  private void insertTenant(UUID tenantId, String suffix) {
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "PREG-" + suffix + "-" + tenantId,
              "preg-" + suffix + "-" + tenantId,
              "Property Registry " + suffix);
          return null;
        });
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

  private int systemCount(UUID tenantId) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM production.prod_property_definition "
                    + "WHERE tenant_id = ? AND system_defined = TRUE",
                Integer.class,
                tenantId));
  }

  private int customCount(UUID tenantId) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM production.prod_property_definition "
                    + "WHERE tenant_id = ? AND property_key LIKE 'CUSTOM\\_%'",
                Integer.class, tenantId));
  }

  private int totalCount() {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM production.prod_property_definition", Integer.class));
  }

  private boolean isActive(UUID tenantId, String propertyKey) {
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            Boolean.TRUE.equals(
                jdbc.queryForObject(
                    "SELECT is_active FROM production.prod_property_definition "
                        + "WHERE tenant_id = ? AND property_key = ?",
                    Boolean.class,
                    tenantId,
                    propertyKey)));
  }
}
