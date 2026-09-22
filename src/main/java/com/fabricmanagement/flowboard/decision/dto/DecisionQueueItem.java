package com.fabricmanagement.flowboard.decision.dto;

import com.fabricmanagement.flowboard.task.domain.Priority;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionAssignment;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionCapability;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverDetail.DecisionSubjectRef;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(name = "DecisionQueueItem")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DecisionQueueItem(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Kind kind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID taskId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0") long taskVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionSubjectRef subject,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionAssignment assignment,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Priority priority,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, format = "date")
        LocalDate dueDate,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant projectedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) State state,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionVerdictCode verdictCode,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DecisionCapability> actions,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String detailHref) {

  @Schema(name = "DecisionKind")
  public enum Kind {
    ORDER_COVER
  }

  @Schema(name = "DecisionState")
  public enum State {
    OPEN,
    WAITING
  }
}
