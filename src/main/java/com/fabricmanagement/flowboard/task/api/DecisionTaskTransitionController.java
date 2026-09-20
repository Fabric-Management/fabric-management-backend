package com.fabricmanagement.flowboard.task.api;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.flowboard.task.app.*;
import com.fabricmanagement.flowboard.task.domain.*;
import com.fabricmanagement.flowboard.task.dto.*;
import com.fabricmanagement.sales.salesorder.app.*;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverFingerprint;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/flowboard/tasks")
@Tag(
    name = "FlowBoard \u2014 Decision Tasks",
    description = "Governed task transitions for order-cover decisions")
public class DecisionTaskTransitionController {
  private final OrderCoverTaskResolver orderCoverTasks;
  private final OrderCoverQueryService query;
  private final TaskTransitionExecutor transitions;
  private final com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator
      permissions;

  public DecisionTaskTransitionController(
      OrderCoverTaskResolver orderCoverTasks,
      OrderCoverQueryService query,
      TaskTransitionExecutor transitions,
      com.fabricmanagement.common.infrastructure.security.SpELPermissionEvaluator permissions) {
    this.orderCoverTasks = orderCoverTasks;
    this.query = query;
    this.transitions = transitions;
    this.permissions = permissions;
  }

  @PostMapping("/{taskId}/transitions")
  public ResponseEntity<ApiResponse<DecisionTransitionResult>> transitionDecisionTask(
      @PathVariable UUID taskId, @Valid @RequestBody DecisionTransitionRequest request) {
    assertReadGrants();
    UUID actor = actor();
    UUID orderId = orderCoverTasks.requireOrderCoverSubject(taskId);
    query.assertReadable(orderId, actor);
    String fingerprint = OrderCoverFingerprint.of(request.payload());
    TaskTransitionResult transition =
        transitions.execute(
            new TaskActionCommand(
                taskId,
                actor,
                request.idempotencyKey().toString(),
                request.action().name(),
                fingerprint,
                request.expectedVersion(),
                request.payload()));
    if (transition.outcome() == TaskTransitionOutcome.REJECTED_BUSINESS)
      throw new OrderCoverConflictException(
          transition.rejectionMessage(), "COVER_PRECONDITION_FAILED");
    var receipt = query.result(orderId, transition.resultId(), actor);
    List<UUID> remaining =
        transition.remainingSubjects().stream()
            .filter(subject -> "SALES_ORDER_LINE".equals(subject.type()))
            .map(TaskSubject::id)
            .toList();
    return ResponseEntity.ok(
        ApiResponse.success(
            new DecisionTransitionResult(
                receipt,
                transition.taskId(),
                transition.taskVersion(),
                transition.taskState(),
                remaining,
                transition.replayed())));
  }

  private UUID actor() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.getDetails() instanceof AuthenticatedUserContext context)
      return context.userId();
    throw new NotFoundException("Authenticated user context not found");
  }

  private void assertReadGrants() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (!permissions.can(authentication, "flowboard", "read")
        || !permissions.can(authentication, "sales", "read"))
      throw new NotFoundException("Decision task not found");
  }
}
