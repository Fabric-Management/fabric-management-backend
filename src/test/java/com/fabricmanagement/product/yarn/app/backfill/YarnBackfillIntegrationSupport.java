package com.fabricmanagement.product.yarn.app.backfill;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;

abstract class YarnBackfillIntegrationSupport extends AbstractIntegrationTest {

  private static final AtomicInteger SEQUENCE = new AtomicInteger();

  @Autowired protected YarnLegacyBackfillService backfillService;
  @Autowired protected SystemTransactionExecutor systemTransactions;
  @Autowired protected ObjectMapper objectMapper;

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  protected TenantFixture insertTenant(String label, int productCount) {
    UUID tenantId = UUID.randomUUID();
    String suffix = tenantId.toString().substring(0, 8).toUpperCase();
    Map<Integer, UUID> productIds = new HashMap<>();
    Map<Integer, String> productUids = new HashMap<>();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO common_tenant.common_tenant (id, uid, slug, name, status) "
                  + "VALUES (?, ?, ?, ?, 'ACTIVE')",
              tenantId,
              "YBF-" + suffix,
              "yarn-backfill-" + label.toLowerCase() + "-" + suffix.toLowerCase(),
              "Yarn Backfill " + label);
          for (int index = 0; index < productCount; index++) {
            UUID productId = UUID.randomUUID();
            String productUid = "YBF-" + suffix + "-PROD-" + index;
            productIds.put(index, productId);
            productUids.put(index, productUid);
            jdbc.update(
                "INSERT INTO production.prod_product "
                    + "(id, tenant_id, uid, product_type, unit, is_active) "
                    + "VALUES (?, ?, ?, 'YARN', 'KG', TRUE)",
                productId,
                tenantId,
                productUid);
          }
          return null;
        });
    return new TenantFixture(tenantId, Map.copyOf(productIds), Map.copyOf(productUids));
  }

  protected void insertBatch(
      TenantFixture fixture, int productIndex, String rawValue, Instant recordedAt) {
    UUID batchId = UUID.randomUUID();
    String attributes = json(Map.of("yarn_count", rawValue));
    int sequence = SEQUENCE.incrementAndGet();
    systemTransactions.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "INSERT INTO production.production_execution_batch "
                  + "(id, tenant_id, uid, product_id, product_type, attributes, batch_code, "
                  + "quantity, reserved_quantity, consumed_quantity, waste_quantity, unit, "
                  + "status, is_active, created_at, updated_at) "
                  + "VALUES (?, ?, ?, ?, 'YARN', CAST(? AS jsonb), ?, 1, 0, 0, 0, 'KG', "
                  + "'AVAILABLE', TRUE, ?, ?)",
              batchId,
              fixture.tenantId(),
              "YBF-BATCH-" + batchId,
              fixture.productId(productIndex),
              attributes,
              "YBF-BATCH-CODE-" + sequence,
              Timestamp.from(recordedAt),
              Timestamp.from(recordedAt));
          return null;
        });
  }

  protected YarnLegacyBackfillReport backfill(TenantFixture fixture) {
    return TenantContext.executeInTenantContext(
        fixture.tenantId(), () -> backfillService.backfillTenant(fixture.tenantId()));
  }

  protected <T> T queryOne(String sql, Class<T> type, Object... args) {
    return systemTransactions.executeInTransaction(jdbc -> jdbc.queryForObject(sql, type, args));
  }

  private String json(Map<String, Object> value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException(exception);
    }
  }

  protected record TenantFixture(
      UUID tenantId, Map<Integer, UUID> productIds, Map<Integer, String> productUids) {

    UUID productId(int index) {
      return productIds.get(index);
    }

    String productUid(int index) {
      return productUids.get(index);
    }
  }
}
