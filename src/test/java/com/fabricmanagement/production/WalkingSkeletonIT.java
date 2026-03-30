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
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.masterdata.fiber.domain.Fiber;
import com.fabricmanagement.production.masterdata.fiber.infra.repository.FiberRepository;
import com.fabricmanagement.production.masterdata.material.domain.MaterialType;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DisplayName("End-to-End Production Flow (Walking Skeleton)")
class WalkingSkeletonIT {

  @Autowired private TradingPartnerService tradingPartnerService;
  @Autowired private SalesOrderService salesOrderService;
  @Autowired private WorkOrderRepository workOrderRepository;
  @Autowired private WorkOrderService workOrderService;
  @Autowired private ApprovalRequestRepository approvalRequestRepository;
  @Autowired private ApprovalRequestService approvalRequestService;
  @Autowired private BatchRepository batchRepository;
  @Autowired private FiberRepository fiberRepository;
  @Autowired private UserRepository userRepository;

  private UUID tenantId;
  private UUID adminUserId;

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
                .taxId("WSTAX" + System.currentTimeMillis() % 100000)
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

    // Batches must reference real prod_fiber rows (fk_exec_batch_material → prod_fiber.id).
    List<Fiber> seededFibers = fiberRepository.findByTenantIdAndIsActiveTrue(tenantId);
    assertThat(seededFibers)
        .as("R__001 fiber seeds for system tenant must be present")
        .hasSizeGreaterThanOrEqualTo(2);
    UUID fiberId1 = seededFibers.get(0).getId();
    UUID fiberId2 = seededFibers.get(1).getId();
    // DB FK is prod_fiber only; output is typed YARN in domain while material_id stays a fiber PK.
    UUID outputMaterialId = seededFibers.size() > 2 ? seededFibers.get(2).getId() : fiberId1;

    Batch rawBatch1 =
        Batch.builder()
            .materialId(fiberId1)
            .materialType(MaterialType.FIBER)
            .batchCode("RAW-FIBER1-" + System.currentTimeMillis() % 1000)
            .quantity(new BigDecimal("1000.00"))
            .unit("KG")
            .status(BatchStatus.AVAILABLE)
            .build();
    rawBatch1.setTenantId(tenantId);
    rawBatch1 = batchRepository.save(rawBatch1);

    Batch rawBatch2 =
        Batch.builder()
            .materialId(fiberId2)
            .materialType(MaterialType.FIBER)
            .batchCode("RAW-FIBER2-" + System.currentTimeMillis() % 1000)
            .quantity(new BigDecimal("1000.00"))
            .unit("KG")
            .status(BatchStatus.AVAILABLE)
            .build();
    rawBatch2.setTenantId(tenantId);
    rawBatch2 = batchRepository.save(rawBatch2);

    // ----------------------------------------------------------------------------------
    // 7. RUN: Start Production (consumes batch, creates output batch)
    // ----------------------------------------------------------------------------------
    // No warehouse_location seed for system tenant in Flyway — null is allowed on
    // batch.location_id.
    UUID outputLocationId = null;

    StartProductionRequest.WorkOrderConsumptionDto consumption1 =
        StartProductionRequest.WorkOrderConsumptionDto.builder()
            .batchId(rawBatch1.getId())
            .quantity(new BigDecimal("600.00"))
            .consumptionPercentage(new BigDecimal("60.00"))
            .build();

    StartProductionRequest.WorkOrderConsumptionDto consumption2 =
        StartProductionRequest.WorkOrderConsumptionDto.builder()
            .batchId(rawBatch2.getId())
            .quantity(new BigDecimal("400.00"))
            .consumptionPercentage(new BigDecimal("40.00"))
            .build();

    StartProductionRequest startProdReq =
        StartProductionRequest.builder()
            .outputMaterialId(outputMaterialId)
            .outputMaterialType(MaterialType.YARN)
            .outputLocationId(outputLocationId)
            .consumptions(List.of(consumption1, consumption2))
            .remarks("Walking Skeleton Yield")
            .build();

    WorkOrderResponse woInProgress =
        workOrderService.startProduction(woReadyForProd.getId(), startProdReq);
    assertThat(woInProgress.getStatus()).isEqualTo(WorkOrderStatus.IN_PROGRESS);

    // ----------------------------------------------------------------------------------
    // 8. ASSERT: Output Batch exists with proper source ID mapping
    // ----------------------------------------------------------------------------------
    List<Batch> generatedBatches =
        batchRepository.findAll().stream()
            .filter(b -> woReadyForProd.getId().equals(b.getSourceId()))
            .toList();

    assertThat(generatedBatches).hasSize(1);
    Batch outputBatch = generatedBatches.getFirst();
    assertThat(outputBatch.getMaterialType()).isEqualTo(MaterialType.YARN);
    assertThat(outputBatch.getQuantity())
        .isEqualByComparingTo(new BigDecimal("1000.00")); // quantity based on plannedQty usually
  }
}
