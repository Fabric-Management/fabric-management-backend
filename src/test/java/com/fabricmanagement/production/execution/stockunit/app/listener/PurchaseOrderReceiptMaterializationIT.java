package com.fabricmanagement.production.execution.stockunit.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.procurement.purchaseorder.api.query.PurchaseOrderQueryService;
import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.app.StockAvailabilityQueryService;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.dto.ReserveRequest;
import com.fabricmanagement.production.execution.batch.dto.StockAvailabilityDtos;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.masterdata.product.domain.Product;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.masterdata.product.infra.repository.ProductRepository;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionRepository;
import com.fabricmanagement.production.quality.decision.infra.repository.QualityDecisionUnitRepository;
import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    properties = {
      "modulith.events.stuck-monitor.enabled=false",
      "modulith.events.resubmit.interval-ms=3600000",
      "batch.certification.enforce-on-reserve=false"
    })
@ActiveProfiles("test")
@Testcontainers
class PurchaseOrderReceiptMaterializationIT {

  private static final UUID TENANT_ID = UUID.randomUUID();

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
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
    registry.add("spring.flyway.enabled", () -> "true");
  }

  @Autowired private ProductRepository productRepository;
  @Autowired private BatchRepository batchRepository;
  @Autowired private StockUnitRepository stockUnitRepository;
  @Autowired private ApplicationEventPublisher eventPublisher;
  @Autowired private TransactionTemplate transactionTemplate;
  @Autowired private BatchService batchService;
  @Autowired private StockAvailabilityQueryService availabilityQueryService;
  @Autowired private QualityDecisionRepository qualityDecisionRepository;
  @Autowired private QualityDecisionUnitRepository qualityDecisionUnitRepository;

  @MockBean private PurchaseOrderQueryService purchaseOrderQueryService;

  @BeforeEach
  void setUp() throws Exception {
    seedTenantAsOwner();
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(TenantContext.SYSTEM_ACTOR_ID);
  }

  private void seedTenantAsOwner() throws Exception {
    try (var connection =
            DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        var statement =
            connection.prepareStatement(
                """
                INSERT INTO common_tenant.common_tenant
                  (id, uid, slug, name, status, settings, is_active, created_at, updated_at, version)
                VALUES (?, ?, ?, ?, 'ACTIVE', '{}'::jsonb, true, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """)) {
      statement.setObject(1, TENANT_ID);
      statement.setString(2, "PO-RECEIPT-IT");
      statement.setString(3, "po-receipt-it");
      statement.setString(4, "PO Receipt Materialization IT");
      statement.executeUpdate();
    }
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void purchaseReceiptBecomesPieceBackedAvailabilityOnlyAfterQcRelease() {
    Product product = productRepository.save(Product.create(ProductType.FABRIC, "M"));
    UUID poId = UUID.randomUUID();
    UUID poLineId = UUID.randomUUID();
    UUID receiptId = UUID.randomUUID();
    when(purchaseOrderQueryService.getPurchaseOrderLineInfo(TENANT_ID, poId, poLineId))
        .thenReturn(
            new PurchaseOrderQueryService.PurchaseOrderLineInfo("PO-2026-001", product.getId()));

    GoodsReceiptConfirmedEvent event =
        GoodsReceiptConfirmedEvent.builder()
            .tenantId(TENANT_ID)
            .receiptId(receiptId)
            .receiptNumber("GR-2026-ABC12345")
            .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
            .sourceId(poId)
            .sourceLineId(poLineId)
            .supplierBatchCode("SUP-LOT-42")
            .items(
                List.of(item("PO-GR-001", "10", "250", "CM"), item("PO-GR-002", "12", "1.5", "M")))
            .build();

    transactionTemplate.executeWithoutResult(ignored -> eventPublisher.publishEvent(event));

    AtomicReference<Batch> materializedBatch = new AtomicReference<>();
    await()
        .pollInSameThread()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              var batchResult =
                  batchRepository.findFirstByTenantIdAndSourceIdAndSourceType(
                      TENANT_ID, receiptId, BatchSourceType.PURCHASE);
              assertThat(batchResult).isPresent();
              Batch batch =
                  batchResult.orElseThrow(
                      () -> new AssertionError("purchase batch was not materialized"));
              materializedBatch.set(batch);
              assertThat(batch.getStatus()).isEqualTo(BatchStatus.PENDING_QC);
              assertThat(batch.getQuantity()).isEqualByComparingTo("4.0");
              assertThat(batch.getUnit()).isEqualTo("M");
              assertThat(
                      stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(
                          TENANT_ID, List.of(batch.getId())))
                  .hasSize(2)
                  .allSatisfy(
                      unit -> {
                        assertThat(unit.getUnit()).isEqualTo("KG");
                        assertThat(unit.getLengthUnit()).isEqualTo("M");
                        assertThat(unit.getQualityGradeId()).isNull();
                        assertThat(unit.getQualityDisposition())
                            .isEqualTo(QualityDisposition.PENDING_INSPECTION);
                        assertThat(unit.getStatus()).isEqualTo(StockUnitStatus.AVAILABLE);
                      });
            });

    Batch batch = materializedBatch.get();
    assertThat(
            availabilityQueryService.lots(
                null, false, null, batch.getId(), null, false, PageRequest.of(0, 20)))
        .isEmpty();

    batchService.releaseFromQc(batch.getId());

    var decisions =
        qualityDecisionRepository
            .findByTenantIdAndBatchIdOrderByDecidedAtDescSeqDesc(
                TENANT_ID, batch.getId(), PageRequest.of(0, 20))
            .getContent();
    assertThat(decisions)
        .singleElement()
        .satisfies(
            decision -> {
              assertThat(decision.getActorId()).isEqualTo(TenantContext.SYSTEM_ACTOR_ID);
              assertThat(decision.getOrigin()).isEqualTo(QualityDecisionOrigin.SYSTEM_RELEASE);
              assertThat(
                      qualityDecisionUnitRepository.countByTenantIdAndDecisionId(
                          TENANT_ID, decision.getId()))
                  .isEqualTo(2);
            });
    assertThat(
            stockUnitRepository.findByTenantIdAndBatchIdInAndIsActiveTrue(
                TENANT_ID, List.of(batch.getId())))
        .extracting(unit -> unit.getQualityDisposition())
        .containsOnly(QualityDisposition.RELEASED);

    var lots =
        availabilityQueryService.lots(
            null, false, null, batch.getId(), null, false, PageRequest.of(0, 20));
    assertThat(lots).hasSize(1);
    StockAvailabilityDtos.Lot lot = lots.getContent().get(0);
    assertThat(lot.physicalSource()).isEqualTo(StockAvailabilityDtos.PhysicalSource.PIECES);
    assertThat(lot.physical().metres()).isEqualByComparingTo("4.0");
    assertThat(lot.physical().pieceCount()).isEqualTo(2);
    assertThat(lot.qualityBreakdown())
        .singleElement()
        .satisfies(row -> assertThat(row.grade()).isNull());
    assertThat(
            batchService
                .reserve(
                    batch.getId(),
                    ReserveRequest.builder()
                        .quantity(BigDecimal.ONE)
                        .referenceId(UUID.randomUUID())
                        .referenceType("WORK_ORDER")
                        .build())
                .getUnit())
        .isEqualTo("M");
  }

  private GoodsReceiptConfirmedEvent.ReceiptItemData item(
      String barcode, String netWeight, String length, String lengthUnit) {
    return new GoodsReceiptConfirmedEvent.ReceiptItemData(
        UUID.randomUUID(),
        barcode,
        new BigDecimal(netWeight),
        null,
        new BigDecimal(length),
        lengthUnit);
  }
}
