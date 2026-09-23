package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;

@Schema(name = "OrderCoverDetail")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverDetail(
    @com.fasterxml.jackson.annotation.JsonProperty("case")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        OrderCoverCaseDto caseData,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionSubjectRef subject,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionAssignment assignment,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
        OrderCoverEvidenceDto evidence,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DecisionCapability> actions,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderCoverLineDecision> lines,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<OrderCoverResultDto> results) {
  public OrderCoverDetail {
    actions = List.copyOf(actions);
    lines = List.copyOf(lines);
    results = List.copyOf(results);
  }

  @Schema(name = "DecisionSubjectRef")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionSubjectRef(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionSubjectType type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String number,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String accessibleHref) {}

  @Schema(name = "DecisionAssignment")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionAssignment(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionAssignmentBucket bucket,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> directAssigneeIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> departmentIds,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String holderDisplay) {}

  @Schema(name = "DecisionCapability")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionCapability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionCapabilityAction action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DecisionBlockedReason reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          DecisionRouteTarget routesTo,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean needsApproval,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant evaluatedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          List<PermissionKey> requiredPermissions) {}

  @Schema(name = "DecisionBlockedReason")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionBlockedReason(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionBlockedReasonCode code,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String messageKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionReasonParameters parameters) {}

  @Schema(name = "DecisionReasonParameters")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionReasonParameters(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          PermissionKey requiredPermission,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, minimum = "0")
          Long currentTaskVersion) {}

  @Schema(name = "DecisionRouteTarget")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionRouteTarget(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionRouteKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String labelKey,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String accessibleHref) {}

  @Schema(name = "DecisionSubjectType")
  public enum DecisionSubjectType {
    SALES_ORDER
  }

  @Schema(name = "DecisionAssignmentBucket")
  public enum DecisionAssignmentBucket {
    MINE,
    DEPARTMENT,
    UNASSIGNED,
    WAITING
  }

  @Schema(name = "DecisionCapabilityAction")
  public enum DecisionCapabilityAction {
    CONFIRM_PRODUCTION_COVER,
    OPEN_CONTROL
  }

  @Schema(name = "DecisionBlockedReasonCode")
  public enum DecisionBlockedReasonCode {
    PERMISSION_DENIED,
    OUTSIDE_ROUTING_POOL,
    ASSIGNED_ELSEWHERE,
    UNASSIGNED,
    EVIDENCE_UNKNOWN,
    EVIDENCE_CHANGED,
    CASE_CLOSED,
    UNSUPPORTED_ROUTE
  }

  @Schema(name = "DecisionRouteKind")
  public enum DecisionRouteKind {
    USER,
    DEPARTMENT,
    SYSTEM
  }
}
