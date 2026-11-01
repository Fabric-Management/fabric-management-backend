package com.fabricmanagement.production.core.goodsreceipt.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class GoodsReceiptItemBarcodeTenantScopeIT extends AbstractIntegrationTest {

  private static final String SHARED_BARCODE = "PO-20260625-00001-001";

  // The database is shared with other test classes, so every id this test writes is its own.
  private final String run = UUID.randomUUID().toString().substring(0, 8);
  private final String tenant1 = UUID.randomUUID().toString();
  private final String tenant2 = UUID.randomUUID().toString();
  private final String receipt1 = UUID.randomUUID().toString();
  private final String receipt2 = UUID.randomUUID().toString();
  private final String item1 = UUID.randomUUID().toString();
  private final String item2 = UUID.randomUUID().toString();

  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @AfterEach
  void removeOwnRows() {
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          jdbcTemplate.update(
              "DELETE FROM production.goods_receipt_item WHERE tenant_id IN (?::uuid, ?::uuid)",
              tenant1,
              tenant2);
          jdbcTemplate.update(
              "DELETE FROM production.goods_receipt WHERE tenant_id IN (?::uuid, ?::uuid)",
              tenant1,
              tenant2);
          return null;
        });
  }

  @Test
  void goodsReceiptItemBarcodeIsUniquePerTenantNotGlobally() {
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          insertGoodsReceiptWithItem(
              jdbcTemplate, tenant1, receipt1, item1, "TEST-GR-1-" + run, "TEST-1-GRI-" + run);
          insertGoodsReceiptWithItem(
              jdbcTemplate, tenant2, receipt2, item2, "TEST-GR-2-" + run, "TEST-2-GRI-" + run);
          return null;
        });

    Integer count =
        systemTransactionExecutor.executeInTransaction(
            jdbcTemplate ->
                jdbcTemplate.queryForObject(
                    """
                    SELECT count(*)
                    FROM production.goods_receipt_item
                    WHERE barcode = ?
                      AND tenant_id IN (?::uuid, ?::uuid)
                      AND is_active = true
                    """,
                    Integer.class,
                    SHARED_BARCODE,
                    tenant1,
                    tenant2));

    assertThat(count).isEqualTo(2);
  }

  private static void insertGoodsReceiptWithItem(
      org.springframework.jdbc.core.JdbcTemplate jdbcTemplate,
      String tenantId,
      String receiptId,
      String itemId,
      String receiptNumber,
      String itemUid) {
    jdbcTemplate.update(
        """
        INSERT INTO production.goods_receipt (
            id, tenant_id, uid, created_at, updated_at, is_active, version,
            receipt_number, source_type, source_id, source_line_id, received_by_id, received_at,
            package_count, gross_weight, net_weight, status
        ) VALUES (
            ?::uuid, ?::uuid, ? || '-UID', NOW(), NOW(), true, 0,
            ?, 'PURCHASE_ORDER', gen_random_uuid(), gen_random_uuid(), gen_random_uuid(), NOW(),
            1, 12.000, 10.000, 'DRAFT'
        )
        """,
        receiptId,
        tenantId,
        receiptNumber,
        receiptNumber);

    jdbcTemplate.update(
        """
        INSERT INTO production.goods_receipt_item (
            id, tenant_id, uid, created_at, updated_at, is_active, version,
            goods_receipt_id, sequence_no, barcode, net_weight, gross_weight
        ) VALUES (
            ?::uuid, ?::uuid, ?, NOW(), NOW(), true, 0,
            ?::uuid, 1, ?, 10.000, 12.000
        )
        """,
        itemId,
        tenantId,
        itemUid,
        receiptId,
        SHARED_BARCODE);
  }
}
