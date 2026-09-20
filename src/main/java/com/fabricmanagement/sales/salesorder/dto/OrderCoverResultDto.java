package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;

@Schema(name = "OrderCoverResult")
@JsonInclude(JsonInclude.Include.ALWAYS)
public record OrderCoverResultDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID caseId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long caseRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID actorId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ActorKind actorKind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String policyKey,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) String rationale,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1") long evidenceRevision,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<Line> lines,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true) UUID supersedesResultId) {
  @Schema(name = "OrderCoverLineResult")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Line(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Outcome outcome,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) OrderCoverEvidenceDto.Quantity quantity,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
          OrderCoverEvidenceDto.Suitability suitabilityAtDecision,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID requirementProfileId,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
          int requirementProfileVersion,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<DecisionResultRef> downstream) {}

  @Schema(name = "DecisionResultRef")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record DecisionResultRef(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DecisionResultType type,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
          String accessibleHref) {}

  @Schema(name = "DecisionActorKind")
  public enum ActorKind {
    USER,
    SYSTEM
  }

  @Schema(name = "OrderCoverOutcome")
  public enum Outcome {
    STOCK,
    MAKE_TO_ORDER
  }

  @Schema(name = "DecisionResultType")
  public enum DecisionResultType {
    ORDER_COVER_RESULT,
    WORK_ORDER,
    TASK
  }

  public static OrderCoverResultDto from(
      OrderCoverResult result, List<OrderCoverLineResult> lines) {
    return new OrderCoverResultDto(
        result.getId(),
        result.getCaseId(),
        result.getCaseRevision(),
        result.getActorId(),
        ActorKind.valueOf(result.getActorKind()),
        result.getPolicyKey(),
        result.getRationale(),
        result.getRecordedAt(),
        result.getEvidenceId(),
        result.getEvidenceRevision(),
        lines.stream()
            .map(
                line ->
                    new Line(
                        line.getLineId(),
                        Outcome.valueOf(line.getOutcome()),
                        OrderCoverEvidenceDto.Quantity.known(line.getQuantity(), line.getUnit()),
                        line.getSuitabilityAtDecision(),
                        line.getRequirementProfileId(),
                        line.getRequirementProfileVersion(),
                        List.of(
                            new DecisionResultRef(
                                DecisionResultType.WORK_ORDER, line.getWorkOrderId(), null))))
            .toList(),
        result.getSupersedesResultId());
  }
}
