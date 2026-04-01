package com.fabricmanagement.production.execution.workorder.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.PagedResponse;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderConsumptionService;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderCostRecalculationService;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderOutputService;
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.ConsumeFromStockUnitRequest;
import com.fabricmanagement.production.execution.workorder.dto.ProductionDashboardResponse;
import com.fabricmanagement.production.execution.workorder.dto.RecordOutputRequest;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderConsumptionResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderConsumptionSummaryResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderFilterRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderOutputResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderOutputSummaryResponse;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/production/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

  private final WorkOrderService workOrderService;
  private final WorkOrderConsumptionService workOrderConsumptionService;
  private final WorkOrderOutputService workOrderOutputService;
  private final WorkOrderCostRecalculationService workOrderCostRecalculationService;

  @Operation(
      summary = "Production dashboard summary",
      description =
          "Aggregate overview: status breakdown, overdue alerts, "
              + "cost variance (PLANNED vs ACTUAL), and yield performance.")
  @GetMapping("/dashboard")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public ProductionDashboardResponse getProductionDashboard() {
    return workOrderService.getProductionDashboard();
  }

  @Operation(
      summary = "List WorkOrders with optional filtering",
      description =
          "Returns a paginated list of WorkOrders for the current tenant. "
              + "All filter parameters are optional. Combine with Pageable for sorting.")
  @GetMapping
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public PagedResponse<WorkOrderResponse> listWorkOrders(
      @Parameter(description = "Filter by status") @RequestParam(required = false)
          WorkOrderStatus status,
      @Parameter(description = "Filter by customer/supplier") @RequestParam(required = false)
          UUID tradingPartnerId,
      @Parameter(description = "Filter by sales order") @RequestParam(required = false)
          UUID salesOrderId,
      @Parameter(description = "Filter by recipe") @RequestParam(required = false) UUID recipeId,
      @Parameter(description = "Partial match on WO number or product code")
          @RequestParam(required = false)
          String searchText,
      @Parameter(description = "Deadline range start (ISO 8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant deadlineFrom,
      @Parameter(description = "Deadline range end (ISO 8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant deadlineTo,
      @Parameter(description = "Created date range start (ISO 8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdFrom,
      @Parameter(description = "Created date range end (ISO 8601)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant createdTo,
      Pageable pageable) {

    WorkOrderFilterRequest filter =
        new WorkOrderFilterRequest(
            status,
            tradingPartnerId,
            salesOrderId,
            recipeId,
            searchText,
            deadlineFrom,
            deadlineTo,
            createdFrom,
            createdTo);

    return workOrderService.listWorkOrders(filter, pageable);
  }

  @GetMapping("/{id}")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public WorkOrderResponse getWorkOrder(@PathVariable UUID id) {
    return workOrderService.getWorkOrder(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public WorkOrderResponse createWorkOrder(@RequestBody @Valid WorkOrderRequest request) {
    return workOrderService.createWorkOrder(request);
  }

  /**
   * Transitions the work order to a new status. The transition must be valid per the
   * WorkOrderStatus state machine. Returns the updated work order.
   */
  @PatchMapping("/{id}/status")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public WorkOrderResponse changeStatus(
      @PathVariable UUID id, @RequestParam WorkOrderStatus status) {
    return workOrderService.changeStatus(id, status);
  }

  /**
   * Starts production for a work order: - Transitions status to IN_PROGRESS - Consumes the
   * specified parent batches - Creates the output batch (with BatchLineage recorded automatically)
   *
   * <p>consumptions[].consumptionPercentage must match the recipe's component ratios and all
   * percentages must sum to exactly 100.
   */
  @PostMapping("/{id}/start-production")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public WorkOrderResponse startProduction(
      @PathVariable UUID id, @RequestBody @Valid StartProductionRequest request) {
    return workOrderService.startProduction(id, request);
  }

  @PostMapping("/{id}/consume-stock-unit")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public ResponseEntity<ApiResponse<WorkOrderConsumptionResponse>> consumeStockUnit(
      @PathVariable UUID id, @Valid @RequestBody ConsumeFromStockUnitRequest request) {
    var response =
        workOrderConsumptionService.consumeFromStockUnit(
            id, request.stockUnitId(), request.amount());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}/consumptions")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public PagedResponse<WorkOrderConsumptionResponse> getConsumptions(
      @PathVariable UUID id, Pageable pageable) {
    return workOrderConsumptionService.getConsumptionsPaged(id, pageable);
  }

  @GetMapping("/{id}/consumption-summary")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public ResponseEntity<ApiResponse<WorkOrderConsumptionSummaryResponse>> getConsumptionSummary(
      @PathVariable UUID id) {
    var response = workOrderConsumptionService.getConsumptionSummary(id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{id}/record-output")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public ResponseEntity<ApiResponse<WorkOrderOutputResponse>> recordOutput(
      @PathVariable UUID id, @Valid @RequestBody RecordOutputRequest request) {
    var response = workOrderOutputService.recordOutput(id, request.stockUnitId(), request.notes());
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @GetMapping("/{id}/outputs")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public PagedResponse<WorkOrderOutputResponse> getOutputs(
      @PathVariable UUID id, Pageable pageable) {
    return workOrderOutputService.getOutputsPaged(id, pageable);
  }

  @GetMapping("/{id}/output-summary")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'READ')")
  public ResponseEntity<ApiResponse<WorkOrderOutputSummaryResponse>> getOutputSummary(
      @PathVariable UUID id) {
    var response = workOrderOutputService.getOutputSummary(id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  @PostMapping("/{id}/complete")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public ResponseEntity<ApiResponse<WorkOrderResponse>> completeWorkOrder(@PathVariable UUID id) {
    var response = workOrderService.completeWorkOrder(id);
    return ResponseEntity.ok(ApiResponse.success(response));
  }

  /**
   * Manually triggers cost recalculation for a COMPLETED WorkOrder.
   *
   * <p>Use this when automatic cost calculation failed at completion time (e.g. price list was not
   * configured). Idempotent — safe to call multiple times.
   *
   * @param id the WorkOrder UUID
   * @return updated WorkOrderResponse with recalculated actualCost
   */
  @PostMapping("/{id}/recalculate-cost")
  @PreAuthorize("@productionAccessService.hasPermission(authentication, 'WORK_ORDER', 'WRITE')")
  public ResponseEntity<ApiResponse<WorkOrderResponse>> recalculateCost(@PathVariable UUID id) {
    var response = workOrderCostRecalculationService.recalculateActualCost(id);
    return ResponseEntity.ok(
        ApiResponse.success(response, "Actual cost recalculated successfully"));
  }
}
