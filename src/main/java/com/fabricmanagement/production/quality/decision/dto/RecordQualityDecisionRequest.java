package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.quality.decision.app.QualityDecisionCommand;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record RecordQualityDecisionRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QualityDecisionScope scope,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) QualityDecisionOutcome outcome,
    QualityReasonCode reasonCode,
    @Size(max = 2000) String remarks,
    Set<@NotNull UUID> stockUnitIds,
    UUID supersedesDecisionId) {

  public QualityDecisionCommand toCommand(UUID batchId) {
    return new QualityDecisionCommand(
        batchId,
        scope,
        outcome,
        reasonCode,
        remarks,
        stockUnitIds == null ? Set.of() : Set.copyOf(stockUnitIds),
        supersedesDecisionId);
  }
}
