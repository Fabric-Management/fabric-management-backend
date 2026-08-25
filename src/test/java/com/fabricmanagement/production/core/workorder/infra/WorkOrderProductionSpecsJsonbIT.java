package com.fabricmanagement.production.core.workorder.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.costing.domain.template.CostTemplate;
import com.fabricmanagement.costing.domain.template.CostTemplateItem;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import com.fabricmanagement.procurement.purchaseorder.domain.specs.GenericPurchaseSpecs;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteLine;
import com.fabricmanagement.procurement.quote.domain.specs.GenericQuoteSpecs;
import com.fabricmanagement.procurement.rfq.domain.SupplierRFQLine;
import com.fabricmanagement.procurement.rfq.domain.specs.GenericRFQSpecs;
import com.fabricmanagement.production.core.workorder.domain.WorkOrder;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.core.workorder.domain.specs.GenericProductionSpecs;
import com.fabricmanagement.production.core.workorder.domain.specs.WorkOrderProductionSpecs;
import com.fabricmanagement.production.dyeing.domain.specs.DyeingProductionSpecs;
import com.fabricmanagement.production.finishing.domain.specs.FinishingProductionSpecs;
import com.fabricmanagement.production.knitting.domain.specs.KnittingProductionSpecs;
import com.fabricmanagement.production.spinning.domain.specs.SpinningProductionSpecs;
import com.fabricmanagement.production.weaving.domain.specs.WeavingProductionSpecs;
import com.fabricmanagement.sales.salesproduct.domain.SalesProduct;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class WorkOrderProductionSpecsJsonbIT {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000003");

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  @Autowired private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentTenantUid("MOD-003");
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update(
        "DELETE FROM procurement.supplier_quote_line WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM procurement.supplier_quote WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM procurement.supplier_rfq_line WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM procurement.supplier_rfq WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM procurement.purchase_order WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM costing.cost_template WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM sales.sales_product WHERE tenant_id = ?", TENANT_ID);
    jdbcTemplate.update("DELETE FROM production.prod_work_order WHERE tenant_id = ?", TENANT_ID);
    TenantContext.clear();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("legacyProductionSpecs")
  void readsLegacyJsonThroughJpaAndWritesItBackWithTheSameDiscriminator(SpecsCase specsCase) {
    UUID workOrderId = UUID.randomUUID();
    insertLegacyWorkOrder(workOrderId, specsCase);

    transactionTemplate.executeWithoutResult(
        status -> {
          WorkOrder workOrder = entityManager.find(WorkOrder.class, workOrderId);

          assertThat(workOrder).isNotNull();
          assertThat(workOrder.getProductionSpecs()).isInstanceOf(specsCase.expectedClass());
          assertThat(workOrder.getProductionSpecs().specType()).isEqualTo(specsCase.type());
          assertThat(workOrder.getAttachments())
              .singleElement()
              .satisfies(
                  attachment -> {
                    assertThat(attachment.get("name")).isEqualTo("legacy.pdf");
                    assertThat(attachment.get("tags")).isEqualTo(List.of("old"));
                  });

          // WorkOrder has no @DynamicUpdate, so this ordinary entity update exercises
          // JsonType serialization of production_specs on the real Hibernate path.
          workOrder.setNotes("round-tripped by MOD-3");
          entityManager.flush();
        });

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT production_specs ->> 'specType'"
                    + " FROM production.prod_work_order WHERE id = ?",
                String.class,
                workOrderId))
        .isEqualTo(specsCase.type().name());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT attachments -> 0 ->> 'name'"
                    + " FROM production.prod_work_order WHERE id = ?",
                String.class,
                workOrderId))
        .isEqualTo("legacy.pdf");
  }

  @Test
  void preservesAllThreeProcurementPolymorphicFamiliesOnTheJpaPath() {
    ProcurementIds ids = insertLegacyProcurementRows();

    transactionTemplate.executeWithoutResult(
        status -> {
          PurchaseOrder purchaseOrder =
              entityManager.find(PurchaseOrder.class, ids.purchaseOrderId());
          SupplierRFQLine rfqLine = entityManager.find(SupplierRFQLine.class, ids.rfqLineId());
          SupplierQuoteLine quoteLine =
              entityManager.find(SupplierQuoteLine.class, ids.quoteLineId());

          assertThat(purchaseOrder.getModuleSpecs()).isInstanceOf(GenericPurchaseSpecs.class);
          assertThat(rfqLine.getModuleSpecs()).isInstanceOf(GenericRFQSpecs.class);
          assertThat(quoteLine.getModuleSpecs()).isInstanceOf(GenericQuoteSpecs.class);

          purchaseOrder.setNotes("purchase round-trip");
          rfqLine.setProductDesc("rfq round-trip");
          quoteLine.setNotes("quote round-trip");
          entityManager.flush();
        });

    assertJsonDiscriminator(
        "procurement.purchase_order", ids.purchaseOrderId(), "module_specs", "GENERIC");
    assertJsonDiscriminator(
        "procurement.supplier_rfq_line", ids.rfqLineId(), "module_specs", "GENERIC");
    assertJsonDiscriminator(
        "procurement.supplier_quote_line", ids.quoteLineId(), "module_specs", "GENERIC");
  }

  @Test
  void roundTripsRecordBasedJsonbValueObjects() {
    CostTemplateItem item = new CostTemplateItem("RAW_PRODUCT", new BigDecimal("0.60"), true);
    UUID[] templateId = new UUID[1];

    transactionTemplate.executeWithoutResult(
        status -> {
          CostTemplate template =
              CostTemplate.create(TENANT_ID, "MOD-3 record probe", "GENERIC", false, List.of(item));
          entityManager.persist(template);
          entityManager.flush();
          templateId[0] = template.getId();
          entityManager.clear();

          assertThat(entityManager.find(CostTemplate.class, templateId[0]).getItems())
              .containsExactly(item);
        });

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT items -> 0 ->> 'costItemCode'" + " FROM costing.cost_template WHERE id = ?",
                String.class,
                templateId[0]))
        .isEqualTo("RAW_PRODUCT");
  }

  @Test
  void roundTripsAnActualSalesJsonbEntity() {
    UUID[] salesProductId = new UUID[1];

    transactionTemplate.executeWithoutResult(
        status -> {
          SalesProduct salesProduct = new SalesProduct();
          salesProduct.setTenantId(TENANT_ID);
          salesProduct.setProductId(UUID.randomUUID());
          salesProduct.setProductName("MOD-3 sales probe");
          salesProduct.setModuleType("GENERIC");
          salesProduct.setListPrice(new BigDecimal("12.5000"));
          salesProduct.setCurrency("GBP");
          salesProduct.setSpecs("{\"style\":\"plain\"}");
          salesProduct.setPhotos("[{\"url\":\"https://example.invalid/photo.jpg\"}]");
          entityManager.persist(salesProduct);
          entityManager.flush();
          salesProductId[0] = salesProduct.getId();
          entityManager.clear();

          SalesProduct reloaded = entityManager.find(SalesProduct.class, salesProductId[0]);
          assertThat(reloaded.getSpecs()).contains("plain");
          assertThat(reloaded.getPhotos()).contains("photo.jpg");
        });

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT specs ->> 'style' FROM sales.sales_product WHERE id = ?",
                String.class,
                salesProductId[0]))
        .isEqualTo("plain");
  }

  private void insertLegacyWorkOrder(UUID workOrderId, SpecsCase specsCase) {
    jdbcTemplate.update(
        """
                INSERT INTO production.prod_work_order (
                    id, tenant_id, uid, created_at, updated_at, is_active, version,
                    work_order_number, fulfillment_type, planned_qty, unit, status,
                    module_type, production_specs, attachments)
                VALUES (?, ?, ?, now(), now(), true, 0, ?, 'INTERNAL', 1, 'KG', 'DRAFT',
                    ?, CAST(? AS jsonb), CAST('[{"name":"legacy.pdf","tags":["old"]}]' AS jsonb))
                """,
        workOrderId,
        TENANT_ID,
        "MOD-3-WO-" + workOrderId,
        "MOD-3-" + workOrderId,
        specsCase.type().name(),
        specsCase.legacyJson());
  }

  private ProcurementIds insertLegacyProcurementRows() {
    UUID rfqId = UUID.randomUUID();
    UUID rfqLineId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID quoteLineId = UUID.randomUUID();
    UUID purchaseOrderId = UUID.randomUUID();

    jdbcTemplate.update(
        """
                INSERT INTO procurement.supplier_rfq (
                    id, tenant_id, uid, rfq_number, work_order_id, module_type, rfq_type,
                    status, deadline, attachments, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, 'GENERIC', 'PURCHASE', 'DRAFT', now(), '[]'::jsonb,
                    true, now(), now(), 0)
                """,
        rfqId,
        TENANT_ID,
        "MOD-3-RFQ-" + rfqId,
        "MOD-3-RFQ-" + rfqId,
        UUID.randomUUID());
    jdbcTemplate.update(
        """
                INSERT INTO procurement.supplier_rfq_line (
                    id, tenant_id, uid, rfq_id, product_desc, requested_qty, unit, module_specs,
                    is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'legacy rfq', 1, 'KG',
                    '{"specType":"GENERIC","notes":"legacy rfq"}'::jsonb,
                    true, now(), now(), 0)
                """,
        rfqLineId,
        TENANT_ID,
        "MOD-3-RFQL-" + rfqLineId,
        rfqId);
    jdbcTemplate.update(
        """
                INSERT INTO procurement.supplier_quote (
                    id, tenant_id, uid, quote_number, rfq_id, trading_partner_id, status,
                    module_type, valid_until, currency, entry_method, attachments, is_active,
                    created_at, updated_at, version)
                VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', 'GENERIC', current_date + 1, 'GBP',
                    'MANUAL_ENTRY', '[]'::jsonb, true, now(), now(), 0)
                """,
        quoteId,
        TENANT_ID,
        "MOD-3-QUOTE-" + quoteId,
        "MOD-3-QUOTE-" + quoteId,
        rfqId,
        UUID.randomUUID());
    jdbcTemplate.update(
        """
                INSERT INTO procurement.supplier_quote_line (
                    id, tenant_id, uid, supplier_quote_id, rfq_line_id, unit_price, currency,
                    qty, unit, volume_discounts, module_specs, is_active, created_at, updated_at,
                    version)
                VALUES (?, ?, ?, ?, ?, 1, 'GBP', 1, 'KG', '{}'::jsonb,
                    '{"specType":"GENERIC","notes":"legacy quote"}'::jsonb,
                    true, now(), now(), 0)
                """,
        quoteLineId,
        TENANT_ID,
        "MOD-3-QUOTEL-" + quoteLineId,
        quoteId,
        rfqLineId);
    jdbcTemplate.update(
        """
                INSERT INTO procurement.purchase_order (
                    id, tenant_id, uid, created_at, updated_at, is_active, version, po_number,
                    work_order_id, trading_partner_id, status, currency, total_amount,
                    revision_number, module_type, module_specs, attachments)
                VALUES (?, ?, ?, now(), now(), true, 0, ?, ?, ?, 'DRAFT', 'GBP', 1, 1,
                    'GENERIC', '{"specType":"GENERIC","description":"legacy purchase"}'::jsonb,
                    '[]'::jsonb)
                """,
        purchaseOrderId,
        TENANT_ID,
        "MOD-3-PO-" + purchaseOrderId,
        "MOD-3-PO-" + purchaseOrderId,
        UUID.randomUUID(),
        UUID.randomUUID());

    return new ProcurementIds(purchaseOrderId, rfqLineId, quoteLineId);
  }

  private void assertJsonDiscriminator(
      String table, UUID id, String column, String expectedSpecType) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT " + column + " ->> 'specType' FROM " + table + " WHERE id = ?",
                String.class,
                id))
        .isEqualTo(expectedSpecType);
  }

  private static Stream<SpecsCase> legacyProductionSpecs() {
    return Stream.of(
        new SpecsCase(
            WorkOrderModuleType.SPINNING,
            "{\"specType\":\"SPINNING\",\"targetYarnCount\":\"30/1\"}",
            SpinningProductionSpecs.class),
        new SpecsCase(
            WorkOrderModuleType.WEAVING,
            "{\"specType\":\"WEAVING\",\"loomType\":\"Air-jet\"}",
            WeavingProductionSpecs.class),
        new SpecsCase(
            WorkOrderModuleType.KNITTING,
            "{\"specType\":\"KNITTING\",\"machineType\":\"Single Jersey\"}",
            KnittingProductionSpecs.class),
        new SpecsCase(
            WorkOrderModuleType.DYEING,
            "{\"specType\":\"DYEING\",\"dyeMethod\":\"Jet\"}",
            DyeingProductionSpecs.class),
        new SpecsCase(
            WorkOrderModuleType.FINISHING,
            "{\"specType\":\"FINISHING\",\"finishType\":\"Enzyme Wash\"}",
            FinishingProductionSpecs.class),
        new SpecsCase(
            WorkOrderModuleType.GENERIC,
            "{\"specType\":\"GENERIC\",\"processNotes\":\"legacy\"}",
            GenericProductionSpecs.class));
  }

  private record SpecsCase(
      WorkOrderModuleType type,
      String legacyJson,
      Class<? extends WorkOrderProductionSpecs> expectedClass) {}

  private record ProcurementIds(UUID purchaseOrderId, UUID rfqLineId, UUID quoteLineId) {}
}
