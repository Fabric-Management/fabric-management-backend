package com.fabricmanagement.production.execution.workorder.api.controller;

import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.StartProductionRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

  @GetMapping("/{id}")
  public WorkOrderResponse getWorkOrder(@PathVariable UUID id) {
    return workOrderService.getWorkOrder(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public WorkOrderResponse createWorkOrder(@RequestBody @Valid WorkOrderRequest request) {
    return workOrderService.createWorkOrder(request);
  }

  /**
   * Transitions the work order to a new status. The transition must be valid per the
   * WorkOrderStatus state machine. Returns the updated work order.
   */
  @PatchMapping("/{id}/status")
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
  public WorkOrderResponse startProduction(
      @PathVariable UUID id, @RequestBody @Valid StartProductionRequest request) {
    return workOrderService.startProduction(id, request);
  }
}
