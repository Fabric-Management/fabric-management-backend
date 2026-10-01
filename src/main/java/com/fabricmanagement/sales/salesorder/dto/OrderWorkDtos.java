package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.OrderWorkEventType;
import com.fabricmanagement.sales.salesorder.domain.OrderWorkKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Who is responsible for planning, warehouse or shipping work on an order, and what you may do. */
public final class OrderWorkDtos {

  private OrderWorkDtos() {}

  /** An action on order work; the backend says whether the current user may take it and why not. */
  @Schema(name = "OrderWorkAction", enumAsRef = true)
  public enum Action {
    CLAIM,
    ASSIGN,
    RELEASE,
    START_EVALUATION,
    PROPOSE,
    COMPLETE,
    RETURN,
    REOPEN,
    CONFIRM_READINESS,
    CONFIRM_GREIGE_COVER,
    WITHDRAW_GREIGE_COVER,
    RECORD_ARRIVAL,
    EVALUATE_REQUEST,
    PROPOSE_REVISION
  }

  @Schema(name = "OrderWorkCapability")
  public record Capability(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean allowed,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason) {

    public static Capability of(Action action, String reason) {
      return new Capability(action, reason == null, reason);
    }
  }

  @Schema(name = "OrderWorkAssignmentView")
  public record AssignmentView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderWorkKind kind,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String departmentCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant routedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID assigneeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String assigneeName,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant assignedAt,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description = "The current user is the responsible person")
          boolean mine,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              description =
                  "Someone is responsible, or an active member of the routed team could take"
                      + " it; false means the work is waiting with nobody able to take it")
          boolean teamCanTake) {}

  @Schema(name = "AssignOrderWork")
  public record Assign(@NotNull UUID assigneeId, @NotBlank @Size(max = 1000) String reason) {}

  @Schema(name = "ReleaseOrderWork")
  public record Release(@NotBlank @Size(max = 1000) String reason) {}

  @Schema(name = "OrderWorkCandidate")
  public record Candidate(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName) {}

  @Schema(name = "OrderWorkEventView")
  public record EventView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderWorkEventType type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String departmentCode,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID fromAssigneeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID toAssigneeId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String reason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID actorId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant occurredAt) {}

  /** The work with its responsibility, history and the current user's actions. */
  @Schema(name = "OrderWorkView")
  public record WorkView(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID orderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AssignmentView assignment,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Capability> actions,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<EventView> history) {}
}
