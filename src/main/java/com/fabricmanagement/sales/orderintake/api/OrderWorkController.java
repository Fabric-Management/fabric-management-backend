package com.fabricmanagement.sales.orderintake.api;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.app.OrderWorkService;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import com.fabricmanagement.sales.salesorder.dto.OrderWorkDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Responsibility for the warehouse's ship-readiness work and shipping's arrival-estimate work on an
 * order: take it, assign or release it with a reason. Planning has its own endpoints in the order
 * flow, because taking an order there also starts the evaluation.
 */
@RestController
@RequestMapping("/api/v1/sales/order-intake/orders/{orderId}/work/{kind}")
@RequiredArgsConstructor
@Tag(name = "Order Work", description = "Who is responsible for warehouse and shipping work")
public class OrderWorkController {

  private final OrderWorkService work;

  @GetMapping
  @PreAuthorize("@auth.can(authentication, 'logistics', 'read')")
  @Operation(
      operationId = "getOrderLogisticsWork",
      summary = "Who is responsible for the work, its history and your actions")
  public ResponseEntity<ApiResponse<OrderWorkDtos.WorkView>> getOrderLogisticsWork(
      @PathVariable UUID orderId, @PathVariable OrderWorkKind kind) {
    return ResponseEntity.ok(
        ApiResponse.success(work.workView(orderId, logistics(kind), OrderIntakeActor.current())));
  }

  @PostMapping("/claim")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'claim')")
  @Operation(
      operationId = "claimOrderLogisticsWork",
      summary = "Take unassigned work routed to your team")
  public ResponseEntity<ApiResponse<OrderWorkDtos.WorkView>> claimOrderLogisticsWork(
      @PathVariable UUID orderId, @PathVariable OrderWorkKind kind) {
    UUID actor = OrderIntakeActor.current();
    work.claim(orderId, logistics(kind), actor);
    return ResponseEntity.ok(ApiResponse.success(work.workView(orderId, kind, actor)));
  }

  @PostMapping("/assignment")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'assign')")
  @Operation(
      operationId = "assignOrderLogisticsWork",
      summary = "Assign or reassign the work to a team member, with a reason")
  public ResponseEntity<ApiResponse<OrderWorkDtos.WorkView>> assignOrderLogisticsWork(
      @PathVariable UUID orderId,
      @PathVariable OrderWorkKind kind,
      @Valid @RequestBody OrderWorkDtos.Assign request) {
    UUID actor = OrderIntakeActor.current();
    work.assign(orderId, logistics(kind), request.assigneeId(), request.reason(), actor);
    return ResponseEntity.ok(ApiResponse.success(work.workView(orderId, kind, actor)));
  }

  @PostMapping("/release")
  @PreAuthorize(
      "@auth.can(authentication, 'logistics', 'prepare')"
          + " or @auth.can(authentication, 'logistics', 'write')"
          + " or @auth.can(authentication, 'logistics', 'assign')")
  @Operation(
      operationId = "releaseOrderLogisticsWork",
      summary = "Give the work back to the team's queue, with a reason")
  public ResponseEntity<ApiResponse<OrderWorkDtos.WorkView>> releaseOrderLogisticsWork(
      @PathVariable UUID orderId,
      @PathVariable OrderWorkKind kind,
      @Valid @RequestBody OrderWorkDtos.Release request) {
    UUID actor = OrderIntakeActor.current();
    work.release(orderId, logistics(kind), request.reason(), actor);
    return ResponseEntity.ok(ApiResponse.success(work.workView(orderId, kind, actor)));
  }

  @GetMapping("/candidates")
  @PreAuthorize("@auth.can(authentication, 'logistics', 'assign')")
  @Operation(
      operationId = "listOrderLogisticsWorkCandidates",
      summary = "Team members the work may be assigned to")
  public ResponseEntity<ApiResponse<List<OrderWorkDtos.Candidate>>>
      listOrderLogisticsWorkCandidates(
          @PathVariable UUID orderId, @PathVariable OrderWorkKind kind) {
    return ResponseEntity.ok(
        ApiResponse.success(work.candidates(orderId, logistics(kind), OrderIntakeActor.current())));
  }

  /** Planning work is not handled here; it is reported like a missing resource. */
  private static OrderWorkKind logistics(OrderWorkKind kind) {
    if (kind == OrderWorkKind.PLANNING) {
      throw new NotFoundException("No such order work");
    }
    return kind;
  }
}
