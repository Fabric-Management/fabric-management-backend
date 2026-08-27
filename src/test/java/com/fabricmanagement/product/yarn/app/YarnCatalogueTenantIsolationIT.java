package com.fabricmanagement.product.yarn.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.SpinningTechnologyFamily;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

class YarnCatalogueTenantIsolationIT extends AbstractIntegrationTest {

  @Autowired private SpinningSystemCatalogService spinningSystemService;
  @Autowired private EndUseCatalogService endUseService;
  @Autowired private TestMethodCatalogService testMethodService;
  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @EnumSource(CatalogueKind.class)
  void tenantCannotReadUpdateOrDeactivateAnotherTenantsRowButMayReuseItsCode(CatalogueKind kind) {
    UUID tenantA = insertTenant("a");
    UUID tenantB = insertTenant("b");
    String code = "ISOLATION_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    TenantContext.setCurrentTenantId(tenantA);
    UUID tenantARow = create(kind, code);

    TenantContext.setCurrentTenantId(tenantB);
    assertThat(listIds(kind)).doesNotContain(tenantARow);
    assertThatThrownBy(() -> get(kind, tenantARow)).isInstanceOf(YarnDomainException.class);
    assertThatThrownBy(() -> update(kind, tenantARow, code))
        .isInstanceOf(YarnDomainException.class);
    assertThatThrownBy(() -> deactivate(kind, tenantARow)).isInstanceOf(YarnDomainException.class);

    UUID tenantBRow = create(kind, code);

    assertThat(tenantBRow).isNotEqualTo(tenantARow);
    assertThat(rowsWithCode(kind, code, tenantA, tenantB)).isEqualTo(2);
  }

  private UUID create(CatalogueKind kind, String code) {
    return switch (kind) {
      case SPINNING_SYSTEM ->
          spinningSystemService
              .defineTenantSpinningSystem(
                  code, "Tenant Spinning System", null, 50, SpinningTechnologyFamily.FRICTION)
              .getId();
      case END_USE -> endUseService.defineTenantEndUse(code, "Tenant End Use", null, 50).getId();
      case TEST_METHOD ->
          testMethodService
              .defineTenantTestMethod(code, "Tenant Test Method", null, 50, null, null, null)
              .getId();
    };
  }

  private java.util.List<UUID> listIds(CatalogueKind kind) {
    return switch (kind) {
      case SPINNING_SYSTEM ->
          spinningSystemService.list().stream().map(row -> row.getId()).toList();
      case END_USE -> endUseService.list().stream().map(row -> row.getId()).toList();
      case TEST_METHOD -> testMethodService.list().stream().map(row -> row.getId()).toList();
    };
  }

  private void get(CatalogueKind kind, UUID id) {
    switch (kind) {
      case SPINNING_SYSTEM -> spinningSystemService.get(id);
      case END_USE -> endUseService.get(id);
      case TEST_METHOD -> testMethodService.get(id);
    }
  }

  private void update(CatalogueKind kind, UUID id, String code) {
    switch (kind) {
      case SPINNING_SYSTEM ->
          spinningSystemService.update(
              id, code, "Other Tenant Update", null, 60, SpinningTechnologyFamily.FRICTION);
      case END_USE -> endUseService.update(id, code, "Other Tenant Update", null, 60);
      case TEST_METHOD ->
          testMethodService.update(id, code, "Other Tenant Update", null, 60, null, null, null);
    }
  }

  private void deactivate(CatalogueKind kind, UUID id) {
    switch (kind) {
      case SPINNING_SYSTEM -> spinningSystemService.deactivate(id);
      case END_USE -> endUseService.deactivate(id);
      case TEST_METHOD -> testMethodService.deactivate(id);
    }
  }

  private UUID insertTenant(String label) {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8);
    systemTransactionExecutor.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "YARN-ISO-" + label.toUpperCase() + "-" + suffix,
              "yarn-iso-" + label + "-" + suffix,
              "Yarn Isolation " + label);
          return null;
        });
    return tenantId;
  }

  private int rowsWithCode(CatalogueKind kind, String code, UUID tenantA, UUID tenantB) {
    String table =
        switch (kind) {
          case SPINNING_SYSTEM -> "prod_yarn_spinning_system";
          case END_USE -> "prod_yarn_end_use";
          case TEST_METHOD -> "prod_yarn_test_method";
        };
    return systemTransactionExecutor.executeInTransaction(
        jdbc ->
            jdbc.queryForObject(
                "SELECT count(*) FROM production."
                    + table
                    + " WHERE code = ? AND tenant_id IN (?, ?)",
                Integer.class,
                code,
                tenantA,
                tenantB));
  }

  enum CatalogueKind {
    SPINNING_SYSTEM,
    END_USE,
    TEST_METHOD
  }
}
