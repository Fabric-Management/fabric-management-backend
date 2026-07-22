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
            ManualQualityReasonCode.OTHER);
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
