package com.fabricmanagement.production.core.stockunit.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class StockUnitCutPlanTest {
  private static final UUID ACTOR = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

  private BigDecimal n(String value) {
    return new BigDecimal(value);
  }

  private StockUnitCutPlan plan() {
    UUID lot = UUID.randomUUID();
    var sources =
        IntStream.range(0, 17)
            .mapToObj(
                index ->
                    new StockUnitCutPlan.Source(
                        UUID.randomUUID(),
                        lot,
                        0L,
                        n("125"),
                        n("30"),
                        "KG",
                        "ROLL-" + index,
                        null,
                        0L))
            .toList();
    return StockUnitCutPlan.prepare(
        "SALES_ORDER",
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "exact-poplin",
        n("2050"),
        sources.getLast().stockUnitId(),
        sources,
        ACTOR,
        NOW);
  }

  private StockUnitCutPlan.Actual measured(
      String delivered, String remaining, String loss, String reason) {
    return new StockUnitCutPlan.Actual(
        n(delivered), n(remaining), n(loss), n("12"), n("18"), n("0"), reason);
  }

  @Test
  void sourceDeliveryAndRemainderRemainDistinctWithoutPhysicalOutputs() {
    var plan = plan();
    assertThat(plan.sourceQuantity()).isEqualByComparingTo("2125");
    assertThat(plan.getDeliveryQuantity()).isEqualByComparingTo("2050");
    assertThat(plan.getCutDeliveryQuantity()).isEqualByComparingTo("50");
    assertThat(plan.remainderQuantity()).isEqualByComparingTo("75");
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.PREPARED);
    assertThat(plan.getActual()).isNull();
    assertThat(plan.getDeliveryStockUnitId()).isNull();
    assertThat(plan.getRemainderStockUnitId()).isNull();
  }

  @Test
  void reviewDoesNotRecordACutAndTheSamePlannerMayReview() {
    var plan = plan();
    plan.approve(ACTOR, NOW.plusSeconds(1));
    assertThat(plan.getReviewedBy()).isEqualTo(ACTOR);
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.APPROVED);
    assertThat(plan.getActual()).isNull();
    assertThat(plan.getRecordedAt()).isNull();
  }

  @Test
  void mismatchedDeliveryIsDisputedAndCorrectionRetainsThePhysicalRecordTime() {
    var plan = plan();
    plan.approve(ACTOR, NOW);
    plan.record(measured("49", "76", "0", null), ACTOR, NOW.plusSeconds(2));
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThatThrownBy(() -> plan.complete(UUID.randomUUID(), UUID.randomUUID(), ACTOR, NOW))
        .isInstanceOf(
            com.fabricmanagement.production.core.stockunit.domain.exception
                .StockUnitCutPlanException.class);
    plan.correct(
        measured("50", "75", "0", "Measurement corrected after rechecking"),
        ACTOR,
        NOW.plusSeconds(3));
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.RECORDED);
    assertThat(plan.getRecordedAt()).isEqualTo(NOW.plusSeconds(2));
    assertThat(plan.getDeliveryQuantity()).isEqualByComparingTo("2050");
  }

  @Test
  void anUnexplainedDifferenceCannotBecomeAssumedWaste() {
    var plan = plan();
    plan.approve(ACTOR, NOW);
    plan.record(measured("50", "70", "0", null), ACTOR, NOW);
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThat(plan.conserved(plan.getActual())).isFalse();
    assertThatThrownBy(() -> plan.correct(measured("50", "70", "5", null), ACTOR, NOW))
        .hasMessageContaining("explanation");
    plan.correct(measured("50", "70", "5", "Measured trimming loss"), ACTOR, NOW);
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.RECORDED);
  }

  @Test
  void massIsMeasuredIndependentlyAndMustAlsoBeConserved() {
    var plan = plan();
    plan.approve(ACTOR, NOW);
    plan.record(
        new StockUnitCutPlan.Actual(n("50"), n("75"), n("0"), n("10"), n("15"), n("0"), null),
        ACTOR,
        NOW);
    assertThat(plan.getState()).isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThat(plan.conserved(plan.getActual())).isFalse();
  }

  @Test
  void cancellationAfterRecordingKeepsPhysicalWorkOpen() {
    var uncut = plan();
    uncut.cancel("Buyer cancelled");
    assertThat(uncut.getState()).isEqualTo(StockUnitCutPlanState.CANCELLED);
    var cut = plan();
    cut.approve(ACTOR, NOW);
    cut.record(measured("50", "75", "0", null), ACTOR, NOW);
    cut.cancel("Buyer cancelled after the cut");
    assertThat(cut.getState()).isEqualTo(StockUnitCutPlanState.DISPUTED);
    assertThat(cut.getRecordedAt()).isEqualTo(NOW);
    assertThat(cut.getActual().remainderLength()).isEqualByComparingTo("75");
    assertThatThrownBy(
            () -> cut.transfer("SALES_ORDER", UUID.randomUUID(), UUID.randomUUID(), "other"))
        .hasMessageContaining("physical work");
  }

  @Test
  void duplicateSourceAndNonCutSelectionsAreRefused() {
    var plan = plan();
    var source = plan.getSources().getFirst();
    assertThatThrownBy(
            () ->
                StockUnitCutPlan.prepare(
                    "QUOTE",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "terms",
                    n("50"),
                    source.stockUnitId(),
                    List.of(source, source),
                    ACTOR,
                    NOW))
        .hasMessageContaining("Distinct");
    assertThatThrownBy(
            () ->
                StockUnitCutPlan.prepare(
                    "QUOTE",
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "terms",
                    n("125"),
                    source.stockUnitId(),
                    List.of(source),
                    ACTOR,
                    NOW))
        .hasMessageContaining("positive remainder");
  }
}
