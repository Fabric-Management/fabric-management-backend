package com.fabricmanagement.production.quality.decision.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.production.execution.batch.domain.BatchStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class QualityDecisionEligibilityTest {

  @Test
  void evaluatesFullLotBatchReasonsInDeterministicOrder() {
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.FULL_LOT,
                BatchStatus.RESERVED,
                BigDecimal.ONE,
                BigDecimal.ONE))
        .isEqualTo(QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_RESERVED));
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.SELECTED_UNITS,
                BatchStatus.DESTROYED,
                BigDecimal.ZERO,
                BigDecimal.ZERO))
        .isEqualTo(
            QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_STATUS_BLOCKED));
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.FULL_LOT,
                BatchStatus.IN_PROGRESS,
                BigDecimal.ZERO,
                BigDecimal.ONE))
        .isEqualTo(QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_CONSUMED));
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.FULL_LOT,
                BatchStatus.IN_PROGRESS,
                BigDecimal.ZERO,
                BigDecimal.ZERO))
        .isEqualTo(
            QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_STATUS_BLOCKED));
  }

  @Test
  void selectedUnitsAllowsConsumedInProgressBatchButNotReservation() {
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.SELECTED_UNITS,
                BatchStatus.IN_PROGRESS,
                BigDecimal.ZERO,
                BigDecimal.ONE))
        .isEqualTo(QualityDecisionCapability.permitted());
    assertThat(
            QualityDecisionEligibility.evaluatePopulation(
                QualityDecisionScope.SELECTED_UNITS, 2, 0))
        .isEqualTo(
            QualityDecisionCapability.blocked(QualityDecisionBlockedReason.NO_ELIGIBLE_UNITS));
    assertThat(
            QualityDecisionEligibility.evaluateBatch(
                QualityDecisionScope.SELECTED_UNITS,
                BatchStatus.IN_PROGRESS,
                BigDecimal.ONE,
                BigDecimal.ONE))
        .isEqualTo(QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_RESERVED));
  }

  @Test
  void evaluatesPopulationForBothDecisionScopes() {
    assertThat(QualityDecisionEligibility.evaluatePopulation(QualityDecisionScope.FULL_LOT, 0, 0))
        .isEqualTo(
            QualityDecisionCapability.blocked(QualityDecisionBlockedReason.NO_ELIGIBLE_UNITS));
    assertThat(QualityDecisionEligibility.evaluatePopulation(QualityDecisionScope.FULL_LOT, 2, 1))
        .isEqualTo(
            QualityDecisionCapability.blocked(
                QualityDecisionBlockedReason.INELIGIBLE_ACTIVE_UNITS));
    assertThat(QualityDecisionEligibility.evaluatePopulation(QualityDecisionScope.FULL_LOT, 2, 2))
        .isEqualTo(QualityDecisionCapability.permitted());
    assertThat(
            QualityDecisionEligibility.evaluatePopulation(
                QualityDecisionScope.SELECTED_UNITS, 2, 1))
        .isEqualTo(QualityDecisionCapability.permitted());
  }

  @Test
  void combinesBatchAndPopulationWithoutLosingBatchReason() {
    var batch =
        QualityDecisionCapability.blocked(QualityDecisionBlockedReason.BATCH_STATUS_BLOCKED);
    var population = QualityDecisionCapability.permitted();

    assertThat(QualityDecisionEligibility.combine(batch, population)).isEqualTo(batch);
    assertThat(
            QualityDecisionEligibility.combine(QualityDecisionCapability.permitted(), population))
        .isEqualTo(population);
  }
}
