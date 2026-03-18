package com.fabricmanagement.costing.domain.event;

import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

/**
 * Published when an ACTUAL (or PLANNED) cost deviates significantly from the previous stage.
 *
 * <p>Subscribers: FlowBoard (creates a COSTING task), NotificationHub (HIGH priority alert).
 *
 * <p>Variance ratio = (currentTotal − previousTotal) / previousTotal. Positive = cost overrun;
 * Negative = cost saving.
 */
@Builder
public record CostVarianceDetectedEvent(
    UUID tenantId,
    UUID costCalculationId,
    CostEntityType entityType,
    UUID entityId,
    CostStage currentStage,
    CostStage previousStage,
    BigDecimal previousTotal,
    BigDecimal currentTotal,
    /** Fractional variance, e.g. 0.15 = 15 % over budget. */
    BigDecimal varianceRatio,
    String currency,
    Instant detectedAt) {}
