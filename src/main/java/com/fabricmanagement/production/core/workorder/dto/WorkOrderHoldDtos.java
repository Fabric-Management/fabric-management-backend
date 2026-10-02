package com.fabricmanagement.production.core.workorder.dto;

import com.fabricmanagement.production.core.workorder.domain.WorkOrderHold;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Work-order hold contracts (SOI D8, A12). */
public final class WorkOrderHoldDtos {

  private WorkOrderHoldDtos() {}

  @Schema(name = "ConfirmWorkOrderHoldRequest")
  public record ConfirmStop(@NotBlank @Size(max = 2000) String stopNote) {}

  @Schema(name = "ResumeWorkOrderHoldRequest")
  public record Resume(
      @Schema(description = "The customer change behind the hold is settled")
          boolean customerChangeSettled,
      @Schema(description = "Technical and material checks are complete") boolean checksCompleted,
      @NotBlank @Size(max = 2000) String note) {}

  @Schema(name = "WorkOrderHold")
  public record HoldDto(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID workOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID salesOrderId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID salesOrderLineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkOrderHold.Status status,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String requestReason,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID requestedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant requestedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String stopNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID confirmedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant confirmedAt,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String resumeNote,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID resumedBy,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) Instant resumedAt) {}
}
