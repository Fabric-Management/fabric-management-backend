package com.fabricmanagement.production.quality.decision.app;

import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import java.util.Set;
import java.util.UUID;

public record QualityDecisionCommand(
    UUID batchId,
    QualityDecisionScope scope,
    QualityDecisionOutcome outcome,
    QualityReasonCode reasonCode,
    String remarks,
    Set<UUID> stockUnitIds,
    UUID supersedesDecisionId) {}
