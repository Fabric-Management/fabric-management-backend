package com.fabricmanagement.production.quality.decision.dto;

import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOrigin;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionOutcome;
import com.fabricmanagement.production.quality.decision.domain.QualityDecisionScope;
import com.fabricmanagement.production.quality.decision.domain.QualityReasonCode;
import java.time.Instant;
import java.util.UUID;

public record QualityDecisionDto(
    UUID id,
    UUID batchId,
    QualityDecisionScope scope,
    QualityDecisionOutcome outcome,
    QualityReasonCode reasonCode,
    String remarks,
    UUID actorId,
    QualityDecisionOrigin origin,
    UUID sourceEventId,
    UUID supersedesDecisionId,
    Instant decidedAt,
    long sequence) {}
