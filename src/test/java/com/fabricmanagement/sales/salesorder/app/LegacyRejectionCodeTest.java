package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class LegacyRejectionCodeTest {
  @Test
  void pinsEveryExistingSalesDomainRejectionString() {
    assertThat(LegacyRejectionCode.of(block(OrderCoverLinePlanner.BlockCode.LINE_NOT_OPEN)))
        .isEqualTo("LINE_NOT_OPEN");
    assertThat(
            LegacyRejectionCode.of(
                block(OrderCoverLinePlanner.BlockCode.REQUIREMENT_COMPLETENESS_UNKNOWN)))
        .isEqualTo("REQUIREMENT_COMPLETENESS_UNKNOWN");
    assertThat(
            LegacyRejectionCode.of(
                new OrderCoverLinePlanner.LineAssessment(
                    false,
                    OrderCoverLinePlanner.BlockCode.REQUIREMENT_INCOMPLETE,
                    List.of("UNSPECIFIED:WIDTH", "UNRESOLVED_SPEC:GSM"),
                    null,
                    false)))
        .isEqualTo("UNSPECIFIED:WIDTH,UNRESOLVED_SPEC:GSM");
    assertThat(
            LegacyRejectionCode.of(block(OrderCoverLinePlanner.BlockCode.LINE_ALREADY_FULFILLED)))
        .isEqualTo("LINE_ALREADY_FULFILLED");
    assertThat(
            LegacyRejectionCode.of(
                block(OrderCoverLinePlanner.BlockCode.ACTIVE_RESERVATION_EXISTS)))
        .isEqualTo("ACTIVE_RESERVATION_EXISTS");
    assertThat(
            LegacyRejectionCode.of(block(OrderCoverLinePlanner.BlockCode.ACTIVE_PRODUCTION_EXISTS)))
        .isEqualTo("ACTIVE_PRODUCTION_EXISTS");
  }

  private static OrderCoverLinePlanner.LineAssessment block(OrderCoverLinePlanner.BlockCode code) {
    return new OrderCoverLinePlanner.LineAssessment(false, code, List.of(), null, false);
  }
}
