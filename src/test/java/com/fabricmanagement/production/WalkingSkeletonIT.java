package com.fabricmanagement.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fabricmanagement.approval.app.ApprovalRequestService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnit;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSourceType;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.execution.workorder.app.ProductionLotService;
import com.fabricmanagement.production.execution.workorder.app.ProductionRecordService;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderConsumptionService;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.OpenProductionLotRequest;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("End-to-End Production Flow (Walking Skeleton)")
class WalkingSkeletonIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    try (var conn =
        java.sql.DriverManager.getConnection(
            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
      conn.createStatement()
          .execute(
              "CREATE ROLE fabric_app LOGIN NOSUPERUSER NOCREATEDB NOBYPASSRLS PASSWORD 'test'");
    } catch (java.sql.SQLException e) {
      throw new RuntimeException("Failed to create fabric_app role", e);
    }

    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "fabric_app");
    registry.add("spring.datasource.password", () -> "test");
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");

    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
  }

  @Autowired private TradingPartnerService tradingPartnerService;
  @Autowired private SalesOrderService salesOrderService;
  @Autowired private WorkOrderRepository workOrderRepository;
  @Autowired private WorkOrderService workOrderService;
  @Autowired private ApprovalRequestRepository approvalRequestRepository;
  @Autowired private ApprovalRequestService approvalRequestService;
  @Autowired private BatchRepository batchRepository;
  @Autowired private FiberRepository fiberRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private ProductionLotService productionLotService;
  @Autowired private WorkOrderConsumptionService workOrderConsumptionService;
  @Autowired private ProductionRecordService productionRecordService;
  @Autowired private StockUnitRepository stockUnitRepository;
  @Autowired private TransactionTemplate transactionTemplate;

  private UUID tenantId;
  private UUID adminUserId;
  private final String testRunSuffix = UUID.randomUUID().toString().substring(0, 8);

  @BeforeEach
  void setUp() {
    // SYSTEM_TENANT_ID is defined in V010__SEEDS.sql
    tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000");
    TenantContext.setCurrentTenantId(tenantId);

    // Find a proper admin user to approve requests
    User adminUser =
        userRepository.findAll().stream()
            .filter(
                u ->
                    tenantId.equals(u.getTenantId())
                        && u.getIsActive()
                        && !SystemUser.ID.equals(u.getId()))
            .findFirst()
            .orElseThrow();
    adminUserId = adminUser.getId();
    TenantContext.setCurrentUserId(adminUserId);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("Full Chain: SalesOrder -> WorkOrder -> Approval -> Batch")
  void endToEndWalkingSkeletonFlow() {
    // ----------------------------------------------------------------------------------
    // 1. SETUP: Create Customer Partner
    // ----------------------------------------------------------------------------------
    TradingPartnerDto partner =
        tradingPartnerService.createPartner(
            CreateTradingPartnerRequest.builder()
                .taxId("WSTAX" + testRunSuffix)
                .companyName("Walking Skeleton Co")
                .partnerType(PartnerType.CUSTOMER)
                .country("TUR")
                .build());

    // ----------------------------------------------------------------------------------
    // 2. RUN: Create & Confirm SalesOrder
    // ----------------------------------------------------------------------------------
    CreateSalesOrderRequest req = new CreateSalesOrderRequest();
    req.setPartnerId(partner.getId());
    req.setOrderDate(LocalDate.now());
    req.setRequestedDeliveryDate(LocalDate.now().plusDays(10));

    // Add exactly one line
    SalesOrderLineRequest lineReq =
        SalesOrderLineRequest.builder()
            .productDesc("Premium WS Fabric")
            .requestedQty(new BigDecimal("1000.00"))
            .unit("KG")
            .unitPrice(new BigDecimal("10.50"))
            .currency("GBP")
            .build();
    req.setLines(List.of(lineReq));

    SalesOrderDto createdOrder = salesOrderService.createOrder(req);
    SalesOrderDto confirmedOrder = salesOrderService.confirmOrder(createdOrder.getId());
    assertThat(confirmedOrder.getStatus().name()).isEqualTo("CONFIRMED");

    // We must manually publish the SalesOrderConfirmedEvent since confirmOrder only
    // changes status
    // and relies on SalesOrderService publishing it
    // Wait, SalesOrderService.confirmOrder DOES publish SalesOrderConfirmedEvent.
    // And it's handled
    // async.
    // So the listener has probably run already or is running.

    // ----------------------------------------------------------------------------------
    // 3. WAIT & ASSERT: WorkOrder created in PENDING_APPROVAL due to Policy
    // ----------------------------------------------------------------------------------
    UUID lineId = confirmedOrder.getLines().getFirst().getId();
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Optional<WorkOrder> woOpt =
                  workOrderRepository.findAll().stream()
                      .filter(
                          wo ->
                              tenantId.equals(wo.getTenantId())
                                  && lineId.equals(wo.getSalesOrderLineId()))
                      .findFirst();
              assertThat(woOpt).isPresent();
              assertThat(woOpt.get().getStatus()).isEqualTo(WorkOrderStatus.PENDING_APPROVAL);
            });
    WorkOrder workOrder =
        workOrderRepository.findAll().stream()
            .filter(
                wo -> tenantId.equals(wo.getTenantId()) && lineId.equals(wo.getSalesOrderLineId()))
            .findFirst()
            .get();

    // ----------------------------------------------------------------------------------
    // 4. WAIT & ASSERT: ApprovalRequest is PENDING and then Approve it
    // ----------------------------------------------------------------------------------
    await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Optional<ApprovalRequest> reqOpt =
                  approvalRequestRepository
                      .findByTenantIdAndEntityTypeAndEntityIdAndStatusAndDeletedAtIsNull(
                          tenantId,
                          ApprovalEntityType.WORK_ORDER,
                          workOrder.getId(),
                          ApprovalRequestStatus.PENDING);
              assertThat(reqOpt).isPresent();
            });
    ApprovalRequest approvalRequest =
        approvalRequestRepository
            .findByTenantIdAndEntityTypeAndEntityIdAndStatusAndDeletedAtIsNull(
                tenantId,
                ApprovalEntityType.WORK_ORDER,
                workOrder.getId(),
                ApprovalRequestStatus.PENDING)
            .get();

    // Manager approve
    approvalRequestService.approveRequest(tenantId, approvalRequest.getId(), adminUserId);

    // ----------------------------------------------------------------------------------
    // 5. WAIT & ASSERT: WorkOrder is APPROVED via Event Listener
    // ----------------------------------------------------------------------------------
    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              WorkOrder updatedWo = workOrderRepository.findById(workOrder.getId()).get();
              assertThat(updatedWo.getStatus()).isEqualTo(WorkOrderStatus.APPROVED);
            });

    // ----------------------------------------------------------------------------------
    // 6. SETUP: Production prerequisites (Recipe & Input Batch)
    // ----------------------------------------------------------------------------------
    // Mock Recipe ID directly into DB
    UUID mockRecipeId = UUID.randomUUID();
    WorkOrder woReadyForProd = workOrderRepository.findById(workOrder.getId()).get();
    woReadyForProd.setRecipeId(mockRecipeId);
    workOrderRepository.save(woReadyForProd);
    // State machine: APPROVED → SENT → IN_PROGRESS (startProduction requires SENT or equivalent
    // path).
    workOrderService.changeStatus(woReadyForProd.getId(), WorkOrderStatus.SENT);

    // A batch's product_id is a prod_product PK (fk_exec_batch_product → prod_product.id, repointed
    // by V20260709110000). It is NOT a prod_fiber PK: the two are independent id spaces, and a
    // fibre
    // reaches its product through prod_fiber.product_id. This test used to pass fibre ids and the
    // FK
    // accepted them only because it pointed at the wrong table.
    //
    // R__001 seeds fibres into the Template Tenant (golden clone source), not the System Tenant.
    // Fiber.product is a lazy @OneToOne, so resolve the product ids inside a transaction; touching
    // the proxy afterwards would throw LazyInitializationException.
    List<UUID> seededFiberProductIds =
        transactionTemplate.execute(
            status ->
                fiberRepository
                    .findByTenantIdAndIsActiveTrue(TenantContext.TEMPLATE_TENANT_ID)
                    .stream()
                    .map(fiber -> fiber.getProduct().getId())
                    .toList());

    assertThat(seededFiberProductIds)
        .as("R__001 fiber seeds for template tenant must be present")
        .hasSizeGreaterThanOrEqualTo(2);
    UUID fiberProductId1 = seededFiberProductIds.get(0);
    UUID fiberProductId2 = seededFiberProductIds.get(1);
    // The output batch is typed YARN in the domain while its product_id remains one of the seeded
    // fibre products; the FK constrains the table, not the product type.
    UUID outputProductId =
        seededFiberProductIds.size() > 2 ? seededFiberProductIds.get(2) : fiberProductId1;

    Batch rawBatch1 =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                fiberProductId1,
                ProductType.FIBER,
                "RAW-FIBER1-" + testRunSuffix,
                null,
                new BigDecimal("1000.00"),
                "KG",
                null,
                null,
                null,
                null,
                null,
                null,
                BatchSourceType.INITIAL_STOCK,
                null,
                null));
    rawBatch1.transitionStatus(BatchStatus.AVAILABLE, adminUserId);
    rawBatch1 = batchRepository.save(rawBatch1);

    StockUnit su1 =
        StockUnit.create(
            tenantId,
            rawBatch1.getId(),
            ProductType.FIBER,
            "SU-RAW-1-" + testRunSuffix,
            null,
            PackageType.BALE,
            new BigDecimal("600.00"),
            null,
            "KG",
            null,
            StockUnitSourceType.GOODS_RECEIPT,
            rawBatch1.getId());
    su1 = stockUnitRepository.save(su1);

    Batch rawBatch2 =
        Batch.create(
            new CreateBatchCommand(
                tenantId,
                fiberProductId2,
                ProductType.FIBER,
                "RAW-FIBER2-" + testRunSuffix,
                null,
                new BigDecimal("1000.00"),
                "KG",
                null,
                null,
                null,
                null,
                null,
                null,
                BatchSourceType.INITIAL_STOCK,
                null,
                null));
    rawBatch2.transitionStatus(BatchStatus.AVAILABLE, adminUserId);
    rawBatch2 = batchRepository.save(rawBatch2);

    StockUnit su2 =
        StockUnit.create(
            tenantId,
            rawBatch2.getId(),
            ProductType.FIBER,
            "SU-RAW-2-" + testRunSuffix,
            null,
            PackageType.BALE,
            new BigDecimal("400.00"),
            null,
            "KG",
            null,
            StockUnitSourceType.GOODS_RECEIPT,
            rawBatch2.getId());
    su2 = stockUnitRepository.save(su2);

    // ----------------------------------------------------------------------------------
    // 7. RUN: Start Production and Execute Production Steps (Phase 2)
    // ----------------------------------------------------------------------------------
    StartProductionRequest startProdReq =
        StartProductionRequest.builder()
            .outputProductId(outputProductId)
            .outputProductType(ProductType.YARN)
            .remarks("Walking Skeleton Yield")
            .build();

    WorkOrderResponse woInProgress =
        workOrderService.startProduction(woReadyForProd.getId(), startProdReq);
    assertThat(woInProgress.status()).isEqualTo(WorkOrderStatus.IN_PROGRESS);

    // Step 7.1: Open Production Lot
    var lotResponse =
        productionLotService.openLot(
            woInProgress.id(),
            new OpenProductionLotRequest(null, ProductType.YARN, "Walking Skeleton Output Lot"));

    // Step 7.2: Consume StockUnits
    workOrderConsumptionService.consumeFromStockUnit(
        woInProgress.id(), su1.getId(), new BigDecimal("600.00"));
    workOrderConsumptionService.consumeFromStockUnit(
        woInProgress.id(), su2.getId(), new BigDecimal("400.00"));

    // Step 7.3: Create output StockUnit and Record Production
    StockUnit outputSu =
        StockUnit.create(
            tenantId,
            lotResponse.id(),
            ProductType.YARN,
            "SU-YARN-OUT-1-" + testRunSuffix,
            null,
            PackageType.BOBBIN,
            new BigDecimal("1000.00"),
            null,
            "KG",
            null,
            StockUnitSourceType.PRODUCTION,
            lotResponse.id());
    outputSu = stockUnitRepository.save(outputSu);

    productionRecordService.recordProduction(
        woInProgress.id(), outputSu.getId(), "Output Recorded");

    // ----------------------------------------------------------------------------------
    // 8. ASSERT: Output Batch exists with proper quantity
    // ----------------------------------------------------------------------------------
    transactionTemplate.executeWithoutResult(
        status -> {
          Batch outputBatch = batchRepository.findByIdAndTenantId(lotResponse.id(), tenantId).get();
          assertThat(outputBatch.getProductType()).isEqualTo(ProductType.YARN);
          assertThat(outputBatch.getQuantity()).isEqualByComparingTo(new BigDecimal("1000.00"));
        });

    var summary = productionRecordService.getProductionSummary(woInProgress.id());
    assertThat(summary.yieldPercentage()).isEqualByComparingTo(new BigDecimal("100.00"));
  }
}
