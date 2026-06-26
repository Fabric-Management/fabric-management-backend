package com.fabricmanagement.production.execution.goodsreceipt.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {"spring.flyway.enabled=true"})
@ActiveProfiles("test")
@Testcontainers
class GoodsReceiptItemBarcodeTenantScopeIT {

  private static final String TENANT_1 = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_2 = "22222222-2222-2222-2222-222222222222";
  private static final String SHARED_BARCODE = "PO-20260625-00001-001";

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16.2-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("fabric_owner")
          .withPassword("fabric123");

  @DynamicPropertySource
  static void registerPgProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private SystemTransactionExecutor systemTransactionExecutor;

  @Test
  void goodsReceiptItemBarcodeIsUniquePerTenantNotGlobally() {
    systemTransactionExecutor.executeInTransaction(
        jdbcTemplate -> {
          insertGoodsReceiptWithItem(
              jdbcTemplate,
              TENANT_1,
              "00000000-0000-0000-0000-000000000101",
              "00000000-0000-0000-0000-000000000201",
              "TEST-GR-001",
              "TEST-001-GRI-00001");
          insertGoodsReceiptWithItem(
              jdbcTemplate,
              TENANT_2,
              "00000000-0000-0000-0000-000000000102",
              "00000000-0000-0000-0000-000000000202",
              "TEST-GR-002",
              "TEST-002-GRI-00001");
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
                    TENANT_1,
                    TENANT_2));

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
            receipt_number, source_type, source_id, received_by_id, received_at,
            package_count, gross_weight, net_weight, status
        ) VALUES (
            ?::uuid, ?::uuid, ? || '-UID', NOW(), NOW(), true, 0,
            ?, 'PURCHASE_ORDER', gen_random_uuid(), gen_random_uuid(), NOW(),
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
