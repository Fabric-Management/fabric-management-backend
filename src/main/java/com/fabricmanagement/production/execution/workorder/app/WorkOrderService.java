package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.DocumentNumberGenerator;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.platform.tenant.api.facade.TenantFacade;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchSourceType;
import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.ProductionDashboardResponse;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderFilterRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.ProductionRecordRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderConsumptionRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderSpecification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class WorkOrderService {

  private final WorkOrderRepository workOrderRepository;
  private final WorkOrderConsumptionRepository workOrderConsumptionRepository;
  private final ProductionRecordRepository productionRecordRepository;
  private final BatchRepository batchRepository;
  private final com.fabricmanagement.production.execution.workorder.app.adapter
          .TradingPartnerAdapter
      tradingPartnerAdapter;
  private final DomainEventPublisher domainEventPublisher;
  private final ApprovalPort approvalPort;
  private final TenantFacade tenantFacade;
  private final DocumentNumberGenerator documentNumberGenerator;

  /**
   * Paginated, filterable listing of WorkOrders for the current tenant.
   *
   * @param filter optional filter criteria (all fields nullable)
   * @param pageable page, size, sort
   */
  public PagedResponse<WorkOrderResponse> listWorkOrders(
      WorkOrderFilterRequest filter, Pageable pageable) {

    UUID tenantId = TenantContext.requireTenantId();
    Specification<WorkOrder> spec = WorkOrderSpecification.build(tenantId, filter);
    Page<WorkOrder> page = workOrderRepository.findAll(spec, pageable);
    return PagedResponse.from(page, WorkOrderResponse::from);
  }

  /**
   * Returns aggregate production dashboard for the current tenant.
   *
   * <p>Two DB queries:
   *
   * <ol>
   *   <li>Status breakdown (JPQL GROUP BY)
   *   <li>Aggregate stats: overdue, yield, cost (native SQL, single row)
   * </ol>
   */
  public ProductionDashboardResponse getProductionDashboard() {
    UUID tenantId = TenantContext.requireTenantId();
    Instant now = Instant.now();

    String dashboardCurrency =
        tenantFacade.findById(tenantId).map(t -> t.getSettings().getCurrency()).orElse("TRY");

    // Query 1: Status counts
    List<WorkOrderRepository.StatusCountProjection> statusCounts =
        workOrderRepository.countByStatus(tenantId);

    Map<String, Long> statusBreakdown = new java.util.LinkedHashMap<>();
    for (WorkOrderStatus status : WorkOrderStatus.values()) {
      statusBreakdown.put(status.name(), 0L);
    }
    for (var sc : statusCounts) {
      statusBreakdown.put(sc.getStatus().name(), sc.getCount());
    }

    // Query 2: Aggregate stats (currency-filtered)
    WorkOrderRepository.DashboardStatsProjection stats =
        workOrderRepository.getDashboardStats(tenantId, now, dashboardCurrency);

    return ProductionDashboardResponse.of(
        statusBreakdown,
        stats.getOverdueCount() != null ? stats.getOverdueCount() : 0L,
        stats.getTotalPlannedCost(),
        stats.getTotalActualCost(),
        stats.getAvgYield(),
        stats.getCompletedCount() != null ? stats.getCompletedCount() : 0L,
        dashboardCurrency);
  }

  /** Retrieves a work order by its UUID. */
  public WorkOrderResponse getWorkOrder(UUID id) {
    WorkOrder workOrder = findEntityById(id);
    return mapToResponse(workOrder);
  }

  /** Creates a new work order in DRAFT state. */
  @Transactional
  public WorkOrderResponse createWorkOrder(WorkOrderRequest request) {
    WorkOrder workOrder =
        WorkOrder.builder()
            .workOrderNumber(generateWorkOrderNumber())
            .recipeId(request.getRecipeId())
            .tradingPartnerId(request.getTradingPartnerId())
            .salesOrderLineId(request.getSalesOrderLineId())
            .fulfillmentType(request.getFulfillmentType())
            .plannedQty(request.getPlannedQty())
            .unit(request.getUnit())
            .unitCost(request.getUnitCost())
            .currency(request.getCurrency())
            .deadline(request.getDeadline())
            .notes(request.getNotes())
            .attachments(
                request.getAttachments() == null ? java.util.List.of() : request.getAttachments())
            .status(WorkOrderStatus.DRAFT)
            .moduleType(
                com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType
                    .GENERIC)
            .build();

    applySupplierSnapshot(workOrder);

    WorkOrder saved = workOrderRepository.save(workOrder);
    return mapToResponse(saved);
  }

  /**
   * Creates a DRAFT WorkOrder from a {@link CreateWorkOrderRequest}.
   *
   * <p>Used internally by {@code SalesOrderRuleEngine}. LocalDate deadline is converted to midnight
   * UTC Instant.
   */
  @Transactional
  public WorkOrderResponse createWorkOrder(
      com.fabricmanagement.production.execution.workorder.dto.CreateWorkOrderRequest request) {
    java.time.Instant deadlineInstant =
        request.getDeadline() != null
            ? request.getDeadline().atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
            : null;

    com.fabricmanagement.production.execution.workorder.domain.FulfillmentType fulfillmentType =
        request.getFulfillmentType() != null
            ? request.getFulfillmentType()
            : com.fabricmanagement.production.execution.workorder.domain.FulfillmentType.INTERNAL;

    WorkOrder workOrder =
        WorkOrder.builder()
            .workOrderNumber(generateWorkOrderNumber())
            .recipeId(request.getRecipeId())
            .tradingPartnerId(request.getTradingPartnerId())
            .salesOrderLineId(request.getSalesOrderLineId())
            .fulfillmentType(fulfillmentType)
            .plannedQty(request.getPlannedQty())
            .unit(request.getUnit())
            .unitCost(request.getUnitCost())
            .currency(request.getCurrency())
            .deadline(deadlineInstant)
            .notes(request.getNotes())
            .attachments(
                request.getAttachments() == null ? java.util.List.of() : request.getAttachments())
            .status(WorkOrderStatus.DRAFT)
            .moduleType(
                com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType
                    .GENERIC)
            .build();

    applySupplierSnapshot(workOrder);

    WorkOrder saved = workOrderRepository.save(workOrder);
    return mapToResponse(saved);
  }

  /** Creates a WorkOrder from a SalesOrderLine snapshot published in an event. */
  @Transactional
  public void createFromSalesOrderLine(
      UUID tenantId,
      UUID salesOrderId,
      com.fabricmanagement.production.execution.workorder.dto.IncomingSalesOrderLine line) {

    List<WorkOrder> existingForLine =
        workOrderRepository.findByTenantIdAndSalesOrderLineIdAndIsActiveTrueOrderByCreatedAtAsc(
            tenantId, line.lineId());

    Optional<WorkOrder> draftForLine =
        existingForLine.stream().filter(wo -> wo.getStatus() == WorkOrderStatus.DRAFT).findFirst();

    if (!existingForLine.isEmpty() && draftForLine.isEmpty()) {
      log.debug(
          "Work order(s) already exist for sales order line {} (no DRAFT) — skipping duplicate"
              + " createFromSalesOrderLine",
          line.lineId());
      return;
    }

    WorkOrder workOrder;
    if (draftForLine.isPresent()) {
      // SalesOrderRuleEngine already created a DRAFT WO in the confirm transaction; promote it
      // instead of inserting a second row for the same line.
      workOrder = draftForLine.get();
      if (workOrder.getSalesOrderId() == null) {
        workOrder.setSalesOrderId(salesOrderId);
      }
      if (workOrder.getProductCode() == null && line.productCode() != null) {
        workOrder.setProductCode(line.productCode());
      }
    } else {
      workOrder =
          WorkOrder.createFromSalesOrderLine(
              tenantId,
              salesOrderId,
              line.lineId(),
              line.productCode(),
              line.quantity(),
              line.unit(),
              line.requestedDeliveryDate(),
              generateWorkOrderNumber(tenantId));
      workOrder = workOrderRepository.save(workOrder);
    }

    // Business Logic: Use ApprovalPort for approval enforcement
    boolean needsApproval =
        approvalPort.requiresApproval(
            tenantId,
            com.fabricmanagement.platform.user.domain.SystemUser.ID,
            "WORK_ORDER",
            workOrder.getId(),
            48); // 48 hours to expire

    workOrder.setStatus(
        needsApproval ? WorkOrderStatus.PENDING_APPROVAL : WorkOrderStatus.APPROVED);

    workOrder = workOrderRepository.save(workOrder);
    log.info(
        "WorkOrder {} for SalesOrder {} line {} is now {}",
        workOrder.getWorkOrderNumber(),
        salesOrderId,
        line.lineId(),
        workOrder.getStatus());

    if (workOrder.getStatus() == WorkOrderStatus.APPROVED) {
      domainEventPublisher.publish(
          new WorkOrderApprovedEvent(
              tenantId,
              workOrder.getId(),
              workOrder.getWorkOrderNumber(),
              workOrder.getModuleType(),
              workOrder.getOutputProductId(),
              workOrder.getPlannedQty(),
              workOrder.getTradingPartnerId(),
              SystemUser.ID));
    }
    // Note: If needsApproval is true, ApprovalGuardService already published ApprovalPendingEvent.
    // We remove the redundant WorkOrderPendingApprovalEvent.
  }

  /**
   * Transitions a WorkOrder to a new status. Validates against the full state machine defined in
   * WorkOrderStatus.
   */
  @Transactional
  public WorkOrderResponse changeStatus(UUID id, WorkOrderStatus newStatus) {
    WorkOrder workOrder = findEntityById(id);

    if (!workOrder.getStatus().canTransitionTo(newStatus)) {
      throw new WorkOrderDomainException(
          String.format(
              "Invalid work order status transition: %s → %s (work order: %s)",
              workOrder.getStatus(), newStatus, workOrder.getWorkOrderNumber()));
    }

    workOrder.setStatus(newStatus);
    WorkOrder saved = workOrderRepository.save(workOrder);
    if (newStatus == WorkOrderStatus.APPROVED) {
      domainEventPublisher.publish(
          new WorkOrderApprovedEvent(
              saved.getTenantId(),
              saved.getId(),
              saved.getWorkOrderNumber(),
              saved.getModuleType(),
              saved.getOutputProductId(),
              saved.getPlannedQty(),
              saved.getTradingPartnerId(),
              TenantContext.getCurrentUserId()));
    }
    return mapToResponse(saved);
  }

  /**
   * Starts production for a WorkOrder: 1. Validates status and required fields (recipeId,
   * outputProductId). 2. Sets the outputProductId on the WorkOrder. 3. Transitions to IN_PROGRESS.
   *
   * <p>Lot creation is handled separately via {@link ProductionLotService#openLot}. Input
   * consumption is handled via {@link WorkOrderConsumptionService#consumeFromStockUnit}.
   */
  @Transactional
  public WorkOrderResponse startProduction(UUID id, StartProductionRequest request) {
    WorkOrder workOrder = findEntityById(id);

    if (!workOrder.getStatus().canTransitionTo(WorkOrderStatus.IN_PROGRESS)) {
      throw new WorkOrderDomainException(
          String.format(
              "Cannot start production for work order in status: %s (work order: %s)",
              workOrder.getStatus(), workOrder.getWorkOrderNumber()));
    }

    if (workOrder.getRecipeId() == null) {
      throw new WorkOrderDomainException(
          "Work order has no associated recipe: " + workOrder.getWorkOrderNumber());
    }

    if (request.getOutputProductId() == null) {
      throw new WorkOrderDomainException("Output product ID is required to start production.");
    }

    // Transition to IN_PROGRESS
    workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
    workOrder.setOutputProductId(request.getOutputProductId());
    workOrder = workOrderRepository.save(workOrder);

    // Phase 2 refactoring: Lot creation is now handled by ProductionLotService.
    // The start-production action only changes the status.

    return mapToResponse(workOrder);
  }

  @Transactional
  public WorkOrderResponse completeWorkOrder(UUID workOrderId) {
    UUID tenantId = TenantContext.requireTenantId();
    UUID actorId = TenantContext.getCurrentUserId();

    WorkOrder workOrder = findEntityById(workOrderId);

    // Rule 1: Must be IN_PROGRESS
    if (workOrder.getStatus() != WorkOrderStatus.IN_PROGRESS) {
      throw new WorkOrderDomainException(
          "Cannot complete WorkOrder. Current status must be IN_PROGRESS, but is: "
              + workOrder.getStatus());
    }

    // Rule 2: At least one consumption
    BigDecimal totalConsumed =
        workOrderConsumptionRepository.sumConsumedWeightByWorkOrderId(tenantId, workOrderId);
    if (totalConsumed == null || totalConsumed.compareTo(BigDecimal.ZERO) == 0) {
      throw new WorkOrderDomainException("Cannot complete: no consumption records found");
    }

    // Rule 3: At least one output
    BigDecimal totalOutput =
        productionRecordRepository.sumOutputWeightByWorkOrderId(tenantId, workOrderId);
    if (totalOutput == null || totalOutput.compareTo(BigDecimal.ZERO) == 0) {
      throw new WorkOrderDomainException("Cannot complete: no output records found");
    }

    // Calculate yield
    BigDecimal yieldPct =
        totalOutput.multiply(new BigDecimal("100")).divide(totalConsumed, 2, RoundingMode.HALF_UP);

    // Domain method handles transition
    workOrder.complete(totalOutput, yieldPct, actorId);
    workOrderRepository.save(workOrder);

    // Auto-close open lots
    List<Batch> openLots =
        batchRepository.findByTenantIdAndSourceIdAndSourceTypeAndStatusAndIsActiveTrue(
            tenantId, workOrderId, BatchSourceType.INTERNAL_PRODUCTION, BatchStatus.AVAILABLE);
    openLots.forEach(
        lot -> {
          lot.setStatus(BatchStatus.DEPLETED);
          batchRepository.save(lot);
        });

    // Publish
    domainEventPublisher.publish(
        new WorkOrderCompletedEvent(
            tenantId,
            workOrderId,
            workOrder.getWorkOrderNumber(),
            workOrder.getPlannedQty(),
            totalOutput,
            totalConsumed,
            yieldPct,
            workOrder.getCompletedAt(),
            actorId));

    return mapToResponse(workOrder);
  }

  private WorkOrder findEntityById(UUID id) {
    UUID tenantId = TenantContext.requireTenantId();
    return workOrderRepository
        .findByIdAndTenantIdAndIsActiveTrue(id, tenantId)
        .orElseThrow(() -> new WorkOrderDomainException("Work order not found with id: " + id));
  }

  private String generateWorkOrderNumber() {
    return generateWorkOrderNumber(TenantContext.requireTenantId());
  }

  private String generateWorkOrderNumber(UUID tenantId) {
    // Note on granularity: Daily granularity (YYYYMMDD) is chosen for WO/PO/SC to align with
    // SalesOrder,
    // simplify date-based substring searches across the platform, and easily support 99k docs/day.
    return documentNumberGenerator.generate(tenantId, "WORK_ORDER", "WO", LocalDate.now(), 5);
  }

  private void applySupplierSnapshot(WorkOrder workOrder) {
    if (workOrder.getTradingPartnerId() != null) {
      var certs = tradingPartnerAdapter.getCertifications(workOrder.getTradingPartnerId());
      if (certs != null && !certs.isEmpty()) {
        var cert = certs.get(0);
        if (cert.getCertification() != null) {
          workOrder.setSupplierCertificationCode(cert.getCertification().certificationCode());
        }
        workOrder.setSupplierLicenseNo(cert.getLicenseNo());
        workOrder.setSupplierLicenseValidUntil(cert.getValidUntil());
      }
    }
  }

  private WorkOrderResponse mapToResponse(WorkOrder workOrder) {
    return WorkOrderResponse.from(workOrder);
  }
}
