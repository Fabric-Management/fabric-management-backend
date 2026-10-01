package com.fabricmanagement.sales.salesorder.api.controller;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.sales.salesorder.app.OrderFlowService;
import com.fabricmanagement.sales.salesorder.dto.OrderFlowDtos;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Sales → planning → customer approval: sales hands over and withdraws, planning evaluates. */
@RestController
@RequestMapping("/api/v1/sales/order-flow")
@RequiredArgsConstructor
@Tag(name = "Order Flow", description = "Sales hands the draft to planning; planning evaluates")
public class OrderFlowController {

  private final OrderFlowService service;

  @GetMapping("/{orderId}")
  @PreAuthorize("@auth.can(authentication, 'sales', 'read')")
  @Operation(operationId = "getOrderFlow", summary = "The order's flow stage, proposal and history")
  public ResponseEntity<ApiResponse<OrderFlowDtos.FlowView>> getOrderFlow(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(service.view(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/submit")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(operationId = "submitOrderToPlanning", summary = "Hand the draft to planning")
  public ResponseEntity<ApiResponse<OrderFlowDtos.FlowView>> submitOrderToPlanning(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(service.submit(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/withdraw")
  @PreAuthorize("@auth.can(authentication, 'sales', 'write')")
  @Operation(
      operationId = "withdrawOrderToDraft",
      summary = "Take the order back to the draft; the reason is required once planning started")
  public ResponseEntity<ApiResponse<OrderFlowDtos.FlowView>> withdrawOrderToDraft(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderFlowDtos.Reason request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.withdraw(orderId, request.reason(), actor(authentication))));
  }

  @GetMapping("/planning-queue")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(operationId = "listOrdersWithPlanning", summary = "Orders waiting for or in planning")
  public ResponseEntity<ApiResponse<List<OrderFlowDtos.QueueItem>>> listOrdersWithPlanning(
      Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(service.planningQueue(actor(authentication))));
  }

  @PostMapping("/{orderId}/claim")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'claim')"
          + " and @auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "claimOrderEvaluation",
      summary = "Take an unassigned order routed to your planning team and start evaluating it")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> claimOrderEvaluation(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(service.claim(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/planning-assignment")
  @PreAuthorize("@auth.can(authentication, 'production', 'assign')")
  @Operation(
      operationId = "assignOrderPlanner",
      summary = "Assign or reassign the order's planning to a team member, with a reason")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> assignOrderPlanner(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderWorkDtos.Assign request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.assignPlanner(orderId, request, actor(authentication))));
  }

  @PostMapping("/{orderId}/planning-release")
  @PreAuthorize(
      "@auth.can(authentication, 'production', 'write')"
          + " or @auth.can(authentication, 'production', 'assign')")
  @Operation(
      operationId = "releaseOrderPlanner",
      summary = "Give the order's planning back to the team's queue, with a reason")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> releaseOrderPlanner(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderWorkDtos.Release request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.releasePlanner(orderId, request.reason(), actor(authentication))));
  }

  @GetMapping("/{orderId}/planning-candidates")
  @PreAuthorize("@auth.can(authentication, 'production', 'assign')")
  @Operation(
      operationId = "listOrderPlannerCandidates",
      summary = "Team members the order's planning may be assigned to")
  public ResponseEntity<ApiResponse<List<OrderWorkDtos.Candidate>>> listOrderPlannerCandidates(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.plannerCandidates(orderId, actor(authentication))));
  }

  @GetMapping("/{orderId}/planning-history")
  @PreAuthorize("@auth.can(authentication, 'production', 'read')")
  @Operation(
      operationId = "getOrderPlanningHistory",
      summary = "Routing, claims, assignments and releases of the order's planning")
  public ResponseEntity<ApiResponse<List<OrderWorkDtos.EventView>>> getOrderPlanningHistory(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.planningHistory(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/start-evaluation")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "startOrderEvaluation",
      summary = "The planner the order was assigned to starts evaluating it")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> startOrderEvaluation(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.startEvaluation(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/proposals")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "proposeOrderDelivery",
      summary = "Propose the date for the delivery term's event, valid until a set time")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> proposeOrderDelivery(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderFlowDtos.ProposeDelivery request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.propose(orderId, request, actor(authentication))));
  }

  @PostMapping("/{orderId}/complete")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "completeOrderPlanning",
      summary = "Finish planning with a current, valid proposal")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> completeOrderPlanning(
      @PathVariable UUID orderId, Authentication authentication) {
    return ResponseEntity.ok(ApiResponse.success(service.complete(orderId, actor(authentication))));
  }

  @PostMapping("/{orderId}/return")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "returnOrderToSales",
      summary = "Return the order to sales with a reason")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> returnOrderToSales(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderFlowDtos.Reason request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(
            service.returnToSales(orderId, request.reason(), actor(authentication))));
  }

  @PostMapping("/{orderId}/reopen")
  @PreAuthorize("@auth.can(authentication, 'production', 'write')")
  @Operation(
      operationId = "reopenOrderEvaluation",
      summary = "Reopen a finished evaluation with a reason before changing its basis")
  public ResponseEntity<ApiResponse<OrderFlowDtos.QueueItem>> reopenOrderEvaluation(
      @PathVariable UUID orderId,
      @Valid @RequestBody OrderFlowDtos.Reopen request,
      Authentication authentication) {
    return ResponseEntity.ok(
        ApiResponse.success(service.reopen(orderId, request.reason(), actor(authentication))));
  }

  private static UUID actor(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AuthenticatedUserContext context) {
      return context.userId();
    }
    throw new AccessDeniedException("Authenticated user context is required.");
  }
}
