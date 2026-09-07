package com.fabricmanagement.production.spinning.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.inventory.domain.InventoryTransaction;
import com.fabricmanagement.production.core.inventory.domain.enums.InventoryTransactionType;
import com.fabricmanagement.production.core.inventory.infra.repository.InventoryTransactionRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PhysicalMovementTypesIT extends AbstractIntegrationTest {

  private static final Pattern SQL_LITERAL = Pattern.compile("'([^']+)'");

  @Autowired private SystemTransactionExecutor systemTransactions;
  @Autowired private InventoryTransactionRepository transactionRepository;

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void everyEnumPersistsAndValidatedConstraintContainsExactlyTheEnumSet() {
    UUID tenantId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();
    seedLedgerOwner(tenantId, productId, batchId);
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentTenantUid("PHY-MOVE");

    for (InventoryTransactionType type : InventoryTransactionType.values()) {
      transactionRepository.saveAndFlush(
          InventoryTransaction.create(
              tenantId,
              batchId,
              type,
              BigDecimal.ONE,
              "KG",
              null,
              null,
              null,
              Instant.parse("2026-09-01T00:00:00Z"),
              type.name(),
              null,
              "physical-movement-" + type.name()));
    }

    Constraint constraint =
        systemTransactions.executeInTransaction(
            jdbc ->
                jdbc.queryForObject(
                    "SELECT convalidated, pg_get_constraintdef(oid) "
                        + "FROM pg_constraint WHERE conname='ck_inv_txn_type_valid' "
                        + "AND conrelid='production.production_execution_inventory_transaction'::regclass",
                    (result, row) ->
                        new Constraint(result.getBoolean("convalidated"), result.getString(2))));
    assertThat(constraint.validated()).isTrue();
    assertThat(literals(constraint.definition()))
        .containsExactlyInAnyOrderElementsOf(
            Arrays.stream(InventoryTransactionType.values())
                .map(Enum::name)
                .collect(Collectors.toSet()));
  }

  private void seedLedgerOwner(UUID tenantId, UUID productId, UUID batchId) {
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "PHY-" + tenantId,
              "physical-movement-" + tenantId,
              "Physical Movement");
          jdbc.update(
              "INSERT INTO production.prod_product "
                  + "(id, tenant_id, uid, product_type, unit, is_active) "
                  + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
              productId,
              tenantId,
              "PHY-PRODUCT-" + productId);
          jdbc.update(
              "INSERT INTO production.production_execution_batch "
                  + "(id, tenant_id, uid, product_id, product_type, batch_code, quantity, "
                  + "reserved_quantity, consumed_quantity, waste_quantity, unit, status, is_active, "
                  + "created_at, updated_at) "
                  + "VALUES (?, ?, ?, ?, 'YARN', ?, 1, 0, 0, 0, 'KG', 'AVAILABLE', TRUE, "
                  + "NOW(), NOW())",
              batchId,
              tenantId,
              "PHY-BATCH-" + batchId,
              productId,
              "PHY-BATCH-CODE-" + batchId);
          return null;
        });
  }

  private Set<String> literals(String definition) {
    Matcher matcher = SQL_LITERAL.matcher(definition);
    java.util.Set<String> values = new java.util.HashSet<>();
    while (matcher.find()) {
      values.add(matcher.group(1));
    }
    return values;
  }

  private record Constraint(boolean validated, String definition) {}
}
