package com.fabricmanagement.production.quality.decision.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QualityDecisionReasonPolicyTest {

  @Test
  void exposesOnlyOrderedManualReasonsAndKeepsSystemReasonsPrivate() {
    assertThat(QualityDecisionReasonPolicy.manualReasons(QualityDecisionOutcome.RELEASED))
        .isEmpty();
    assertThat(QualityDecisionReasonPolicy.manualReasons(QualityDecisionOutcome.QUARANTINED))
        .containsExactly(
            ManualQualityReasonCode.SUSPECTED_DAMAGE,
            ManualQualityReasonCode.AWAITING_LAB,
            ManualQualityReasonCode.SUPPLIER_DISPUTE,
            ManualQualityReasonCode.SHADE_CHECK,
            ManualQualityReasonCode.OTHER);
    assertThat(QualityDecisionReasonPolicy.manualReasons(QualityDecisionOutcome.NONCONFORMING))
        .containsExactly(
            ManualQualityReasonCode.DAMAGE,
            ManualQualityReasonCode.STAIN,
            ManualQualityReasonCode.SHADE_VARIATION,
            ManualQualityReasonCode.SHORT_LENGTH,
            ManualQualityReasonCode.MEASURE_MISMATCH,
            ManualQualityReasonCode.OUT_OF_SPECIFICATION,
            ManualQualityReasonCode.OTHER);
  }

  @Test
  void outOfSpecificationIsAManualNonconformingReason() {
    QualityReasonCode reason = QualityReasonCode.OUT_OF_SPECIFICATION;
    assertThat(ManualQualityReasonCode.OUT_OF_SPECIFICATION.toDomainCode()).isEqualTo(reason);
    assertThat(QualityDecisionReasonPolicy.manualReasons(QualityDecisionOutcome.NONCONFORMING))
        .contains(ManualQualityReasonCode.OUT_OF_SPECIFICATION);
    assertThat(QualityDecisionReasonPolicy.remarksRequired(reason)).isFalse();
    assertThat(
            QualityDecisionReasonPolicy.remarksRequired(
                ManualQualityReasonCode.OUT_OF_SPECIFICATION))
        .isFalse();
    for (QualityDecisionOrigin origin : QualityDecisionOrigin.values()) {
      for (QualityDecisionOutcome outcome : QualityDecisionOutcome.values()) {
        assertThat(QualityDecisionReasonPolicy.reasonAllowed(origin, outcome, reason))
            .as("%s decides %s", origin, outcome)
            .isEqualTo(
                origin == QualityDecisionOrigin.MANUAL
                    && outcome == QualityDecisionOutcome.NONCONFORMING);
      }
    }
  }

  @Test
  void validatesManualAndSystemReasonsFromTheSameCatalog() {
    assertThat(
            QualityDecisionReasonPolicy.reasonAllowed(
                QualityDecisionOrigin.MANUAL, QualityDecisionOutcome.RELEASED, null))
        .isTrue();
    assertThat(
            QualityDecisionReasonPolicy.reasonAllowed(
                QualityDecisionOrigin.MANUAL,
                QualityDecisionOutcome.QUARANTINED,
                QualityReasonCode.AWAITING_LAB))
        .isTrue();
    assertThat(
            QualityDecisionReasonPolicy.reasonAllowed(
                QualityDecisionOrigin.MANUAL,
                QualityDecisionOutcome.QUARANTINED,
                QualityReasonCode.SYSTEM_QC_REJECTED))
        .isFalse();
    assertThat(
            QualityDecisionReasonPolicy.reasonAllowed(
                QualityDecisionOrigin.SYSTEM_RELEASE,
                QualityDecisionOutcome.RELEASED,
                QualityReasonCode.SYSTEM_QC_PASSED))
        .isTrue();
    assertThat(QualityDecisionReasonPolicy.remarksRequired(QualityReasonCode.OTHER)).isTrue();
  }
}
