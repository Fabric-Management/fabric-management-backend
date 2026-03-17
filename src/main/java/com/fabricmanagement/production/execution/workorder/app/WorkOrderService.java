package com.fabricmanagement.production.execution.workorder.app;

import com.fabricmanagement.production.execution.batch.app.BatchService;
import com.fabricmanagement.production.execution.batch.dto.BlendParentRequest;
import com.fabricmanagement.production.execution.batch.dto.CreateBlendedBatchRequest;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.exception.WorkOrderDomainException;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.production.execution.workorder.infra.repository.WorkOrderRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService {

  private final WorkOrderRepository workOrderRepository;
  private final BatchService batchService;

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
            .build();

    // TODO: supplier snapshot — when tradingPartnerId is present, load TradingPartner and
    // snapshot: supplierCertificationCode, supplierLicenseNo, supplierLicenseValidUntil

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
            .build();

    WorkOrder saved = workOrderRepository.save(workOrder);
    return mapToResponse(saved);
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
    return mapToResponse(saved);
  }

  /**
   * Starts production for a WorkOrder: 1. Validates status and required fields. 2. Transitions to
   * IN_PROGRESS. 3. Consumes specified parent batches, creates the output batch via BatchService.
   * 4. BatchLineage is recorded automatically by BatchService.createBlendedBatch.
   *
   * <p>The caller must provide the exact consumptionPercentage per batch in the request, matching
   * the recipe's component ratios (e.g. 60% + 40%). Total must equal 100.
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

    if (request.getOutputMaterialId() == null) {
      throw new WorkOrderDomainException("Output material ID is required to start production.");
    }

    // Transition to IN_PROGRESS
    workOrder.setStatus(WorkOrderStatus.IN_PROGRESS);
    workOrder = workOrderRepository.save(workOrder);

    // Prepare Blend Parent list — consumptionPercentage must come from caller (recipe-based ratios)
    List<BlendParentRequest> parentBatches = new ArrayList<>();
    for (StartProductionRequest.WorkOrderConsumptionDto consumption : request.getConsumptions()) {
      parentBatches.add(
          BlendParentRequest.builder()
              .parentBatchId(consumption.getBatchId())
              .consumedQuantity(consumption.getQuantity())
              .consumptionPercentage(consumption.getConsumptionPercentage())
              .build());
    }

    // BatchService handles consumption + lineage atomically
    CreateBlendedBatchRequest blendReq =
        CreateBlendedBatchRequest.builder()
            .batchCode(workOrder.getWorkOrderNumber() + "-OUT")
            .materialId(request.getOutputMaterialId())
            .materialType(request.getOutputMaterialType())
            .quantity(workOrder.getPlannedQty())
            .unit(workOrder.getUnit())
            .locationId(request.getOutputLocationId())
            .remarks(request.getRemarks())
            .parents(parentBatches)
            .build();

    batchService.createBlendedBatch(blendReq);

    return mapToResponse(workOrder);
  }

  private WorkOrder findEntityById(UUID id) {
    return workOrderRepository
        .findById(id)
        .orElseThrow(() -> new WorkOrderDomainException("Work order not found with id: " + id));
  }

  private String generateWorkOrderNumber() {
    // Format: WO-{YEAR}-{8-char random suffix}
    // 8-char HEX ~ 4.3 billion possibilities; DB unique constraint provides absolute safety.
    String year = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy"));
    String uniqueExt = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
    return String.format("WO-%s-%s", year, uniqueExt);
  }

  private WorkOrderResponse mapToResponse(WorkOrder workOrder) {
    return WorkOrderResponse.builder()
        .id(workOrder.getId())
        .uid(workOrder.getUid())
        .workOrderNumber(workOrder.getWorkOrderNumber())
        .recipeId(workOrder.getRecipeId())
        .tradingPartnerId(workOrder.getTradingPartnerId())
        .salesOrderLineId(workOrder.getSalesOrderLineId())
        .fulfillmentType(workOrder.getFulfillmentType())
        .fulfillmentId(workOrder.getFulfillmentId())
        .plannedQty(workOrder.getPlannedQty())
        .unit(workOrder.getUnit())
        .unitCost(workOrder.getUnitCost())
        .currency(workOrder.getCurrency())
        .plannedCost(workOrder.getPlannedCost())
        .plannedCostCurrency(workOrder.getPlannedCostCurrency())
        .status(workOrder.getStatus())
        .deadline(workOrder.getDeadline())
        .notes(workOrder.getNotes())
        .attachments(workOrder.getAttachments())
        .supplierCertificationCode(workOrder.getSupplierCertificationCode())
        .supplierLicenseNo(workOrder.getSupplierLicenseNo())
        .supplierLicenseValidUntil(workOrder.getSupplierLicenseValidUntil())
        .build();
  }
}
