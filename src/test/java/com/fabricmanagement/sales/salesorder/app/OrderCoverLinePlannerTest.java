package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.salesorder.domain.OrderCoverCaseLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.requirement.RequirementProfileSnapshot;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderCoverLinePlannerTest {
  private static final UUID LINE_ID = UUID.randomUUID();

  @Test
  void goldenTablePinsThePreExtractionStructuralDecisionOrder() {
    var selectable = assessment(true, true, true, SalesOrderLineStatus.PENDING, "10", false, false);
    assertThat(selectable.selectable()).isTrue();
    assertThat(selectable.productionQuantity()).isEqualByComparingTo("10");
    assertThat(selectable.rationaleRequiredIfSelected()).isFalse();

    assertBlock(
        assessment(false, true, true, SalesOrderLineStatus.PENDING, "10", false, false),
        OrderCoverLinePlanner.BlockCode.LINE_NOT_OPEN);
    assertBlock(
        assessment(true, false, true, SalesOrderLineStatus.PENDING, "10", false, false),
        OrderCoverLinePlanner.BlockCode.REQUIREMENT_COMPLETENESS_UNKNOWN);
    assertBlock(
        assessment(true, true, true, SalesOrderLineStatus.COMPLETED, "10", false, false),
        OrderCoverLinePlanner.BlockCode.LINE_ALREADY_FULFILLED);
    assertBlock(
        assessment(true, true, true, SalesOrderLineStatus.PENDING, "10", true, false),
        OrderCoverLinePlanner.BlockCode.ACTIVE_RESERVATION_EXISTS);
    assertBlock(
        assessment(true, true, true, SalesOrderLineStatus.PENDING, "10", false, true),
        OrderCoverLinePlanner.BlockCode.ACTIVE_PRODUCTION_EXISTS);
  }

  @Test
  void incompleteReasonsAreStructuralAndRationaleAggregatesAcrossTheSelection() {
    RequirementProfileSnapshot profile = mock(RequirementProfileSnapshot.class);
    when(profile.complete()).thenReturn(false);
    when(profile.incompleteReasons())
        .thenReturn(List.of("UNSPECIFIED:WIDTH", "UNRESOLVED_SPEC:GSM"));
    var incomplete =
        OrderCoverLinePlanner.assess(
            scope(true),
            line(profile, SalesOrderLineStatus.PENDING, "10"),
            evidenceKnown("1"),
            false,
            false);
    assertThat(incomplete.blockCode())
        .isEqualTo(OrderCoverLinePlanner.BlockCode.REQUIREMENT_INCOMPLETE);
    assertThat(incomplete.incompleteReasons())
        .containsExactly("UNSPECIFIED:WIDTH", "UNRESOLVED_SPEC:GSM");

    var known = assessment(true, true, true, SalesOrderLineStatus.PENDING, "10", false, false);
    var unknown =
        OrderCoverLinePlanner.assess(
            scope(true),
            line(completeProfile(), SalesOrderLineStatus.PENDING, "10"),
            evidenceUnknown(),
            false,
            false);
    assertThat(OrderCoverLinePlanner.assessSelection(List.of(known, unknown)).rationaleRequired())
        .isTrue();
  }

  @Test
  void fullOpenQuantityIsPlannedInsteadOfTheEvidenceShortfall() {
    SalesOrderLine line = line(completeProfile(), SalesOrderLineStatus.PENDING, "80");
    var assessment =
        OrderCoverLinePlanner.assess(scope(true), line, evidenceKnown("60"), false, false);
    assertThat(assessment.selectable()).isTrue();
    assertThat(assessment.productionQuantity()).isEqualByComparingTo("80");
    assertThat(assessment.rationaleRequiredIfSelected()).isFalse();
  }

  @Test
  void zeroAndUnknownShortfallsBothRequireRationale() {
    SalesOrderLine line = line(completeProfile(), SalesOrderLineStatus.PENDING, "80");
    assertThat(
            OrderCoverLinePlanner.assess(scope(true), line, evidenceKnown("0"), false, false)
                .rationaleRequiredIfSelected())
        .isTrue();
    assertThat(
            OrderCoverLinePlanner.assess(scope(true), line, evidenceUnknown(), false, false)
                .rationaleRequiredIfSelected())
        .isTrue();
  }

  private static OrderCoverLinePlanner.LineAssessment assessment(
      boolean open,
      boolean complete,
      boolean evidencePresent,
      SalesOrderLineStatus status,
      String quantity,
      boolean reservation,
      boolean production) {
    RequirementProfileSnapshot profile = complete ? completeProfile() : null;
    return OrderCoverLinePlanner.assess(
        scope(open),
        line(profile, status, quantity),
        evidencePresent ? evidenceKnown("1") : null,
        reservation,
        production);
  }

  private static void assertBlock(
      OrderCoverLinePlanner.LineAssessment assessment, OrderCoverLinePlanner.BlockCode code) {
    assertThat(assessment.selectable()).isFalse();
    assertThat(assessment.blockCode()).isEqualTo(code);
    assertThat(assessment.productionQuantity()).isNull();
  }

  private static OrderCoverCaseLine scope(boolean unresolved) {
    OrderCoverCaseLine scope = mock(OrderCoverCaseLine.class);
    when(scope.unresolved()).thenReturn(unresolved);
    return scope;
  }

  private static SalesOrderLine line(
      RequirementProfileSnapshot profile, SalesOrderLineStatus status, String requested) {
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getRequirementProfileSnapshot()).thenReturn(profile);
    when(line.getLineStatus()).thenReturn(status);
    when(line.getRequestedQty()).thenReturn(new BigDecimal(requested));
    when(line.getShippedQty()).thenReturn(BigDecimal.ZERO);
    return line;
  }

  private static RequirementProfileSnapshot completeProfile() {
    RequirementProfileSnapshot profile = mock(RequirementProfileSnapshot.class);
    when(profile.complete()).thenReturn(true);
    return profile;
  }

  private static OrderCoverEvidenceDto.Line evidenceKnown(String shortfall) {
    return evidence(OrderCoverEvidenceDto.Quantity.known(new BigDecimal(shortfall), "m"));
  }

  private static OrderCoverEvidenceDto.Line evidenceUnknown() {
    return evidence(OrderCoverEvidenceDto.Quantity.unknown("m", "SUITABILITY_EVIDENCE_UNKNOWN"));
  }

  private static OrderCoverEvidenceDto.Line evidence(OrderCoverEvidenceDto.Quantity shortfall) {
    var requested = OrderCoverEvidenceDto.Quantity.known(BigDecimal.TEN, "m");
    return new OrderCoverEvidenceDto.Line(
        LINE_ID,
        1,
        null,
        requested,
        requested,
        requested,
        shortfall,
        OrderCoverEvidenceDto.Suitability.EXACT,
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  @Test
  void livePortsAreConsultedLazilyAndInTheSettlementOrder() {
    List<String> calls = new ArrayList<>();
    SalesOrderLine line = line(completeProfile(), SalesOrderLineStatus.PENDING, "10");

    var structural =
        OrderCoverLinePlanner.assess(
            scope(false),
            line,
            evidenceKnown("1"),
            () -> calls.add("reservation") && false,
            () -> calls.add("production") && false);
    assertBlock(structural, OrderCoverLinePlanner.BlockCode.LINE_NOT_OPEN);
    assertThat(calls).as("no port call after a structural block").isEmpty();

    var reserved =
        OrderCoverLinePlanner.assess(
            scope(true),
            line,
            evidenceKnown("1"),
            () -> calls.add("reservation"),
            () -> calls.add("production") && false);
    assertBlock(reserved, OrderCoverLinePlanner.BlockCode.ACTIVE_RESERVATION_EXISTS);
    assertThat(calls)
        .as("production is not consulted after a reservation block")
        .containsExactly("reservation");

    calls.clear();
    var produced =
        OrderCoverLinePlanner.assess(
            scope(true),
            line,
            evidenceKnown("1"),
            () -> calls.add("reservation") && false,
            () -> calls.add("production"));
    assertBlock(produced, OrderCoverLinePlanner.BlockCode.ACTIVE_PRODUCTION_EXISTS);
    assertThat(calls).containsExactly("reservation", "production");
  }
}
