package com.fabricmanagement.production.core.stockunit.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitCutPlanException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A plan owns complete sources; it does not manufacture future stock. All lengths are metres. */
@Entity
@Table(name = "stock_unit_cut_plan", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockUnitCutPlan extends BaseEntity {
  @Column(nullable = false)
  private String targetType;

  @Column(nullable = false)
  private UUID targetId;

  @Column(nullable = false)
  private UUID targetLineId;

  @Column(nullable = false)
  private UUID productId;

  @Column(nullable = false)
  private String targetFingerprint;

  @Column(nullable = false, precision = 15, scale = 3)
  private BigDecimal deliveryQuantity;

  @Column(nullable = false)
  private UUID cutSourceId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private List<Source> sources;

  @Column(nullable = false, precision = 15, scale = 3)
  private BigDecimal cutDeliveryQuantity;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private StockUnitCutPlanState state;

  @Column(nullable = false)
  private UUID preparedBy;

  @Column(nullable = false)
  private Instant preparedAt;

  private UUID reviewedBy;
  private Instant reviewedAt;
  private UUID recordedBy;
  private Instant recordedAt;
  private UUID verifiedBy;
  private Instant verifiedAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Actual actual;

  private UUID deliveryStockUnitId;
  private UUID remainderStockUnitId;

  @Column(length = 1000)
  private String dispositionReason;

  public record Source(
      UUID stockUnitId,
      UUID batchId,
      Long version,
      BigDecimal length,
      BigDecimal weight,
      String weightUnit,
      String serialNumber,
      UUID qualityGradeId,
      Long measurementRevision) {}

  /** Actual masses are measured, never inferred from a length ratio. */
  public record Actual(
      BigDecimal deliveryLength,
      BigDecimal remainderLength,
      BigDecimal lossLength,
      BigDecimal deliveryWeight,
      BigDecimal remainderWeight,
      BigDecimal lossWeight,
      String reason) {}

  public static StockUnitCutPlan prepare(
      String type,
      UUID target,
      UUID line,
      UUID product,
      String fingerprint,
      BigDecimal quantity,
      UUID cutSource,
      List<Source> sources,
      UUID actor,
      Instant at) {
    if (sources == null
        || sources.isEmpty()
        || sources.stream().map(Source::stockUnitId).distinct().count() != sources.size())
      throw StockUnitCutPlanException.invalid(
          "SOURCES_INVALID", "Distinct measured source rolls are required");
    var cut =
        sources.stream()
            .filter(row -> row.stockUnitId().equals(cutSource))
            .findFirst()
            .orElseThrow(
                () ->
                    StockUnitCutPlanException.invalid(
                        "SOURCE_REQUIRED", "The cut source must be selected"));
    BigDecimal whole =
        sources.stream()
            .filter(row -> !row.stockUnitId().equals(cutSource))
            .map(Source::length)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal delivered = quantity.subtract(whole);
    if (delivered.signum() <= 0 || delivered.compareTo(cut.length()) >= 0)
      throw StockUnitCutPlanException.invalid(
          "CUT_NOT_REQUIRED", "Choose whole rolls and one source with a positive remainder");
    var plan = new StockUnitCutPlan();
    plan.targetType = Objects.requireNonNull(type);
    plan.targetId = Objects.requireNonNull(target);
    plan.targetLineId = Objects.requireNonNull(line);
    plan.productId = Objects.requireNonNull(product);
    plan.targetFingerprint = Objects.requireNonNull(fingerprint);
    plan.deliveryQuantity = quantity;
    plan.cutSourceId = cutSource;
    plan.sources = List.copyOf(sources);
    plan.cutDeliveryQuantity = delivered;
    plan.preparedBy = Objects.requireNonNull(actor);
    plan.preparedAt = Objects.requireNonNull(at);
    plan.state = StockUnitCutPlanState.PREPARED;
    return plan;
  }

  public Source cutSource() {
    return sources.stream()
        .filter(row -> row.stockUnitId().equals(cutSourceId))
        .findFirst()
        .orElseThrow();
  }

  public BigDecimal sourceQuantity() {
    return sources.stream().map(Source::length).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal remainderQuantity() {
    return cutSource().length().subtract(cutDeliveryQuantity);
  }

  public void approve(UUID actor, Instant at) {
    require(StockUnitCutPlanState.PREPARED);
    reviewedBy = actor;
    reviewedAt = at;
    state = StockUnitCutPlanState.APPROVED;
  }

  public void record(Actual measured, UUID actor, Instant at) {
    require(StockUnitCutPlanState.APPROVED);
    validateMeasurements(measured);
    actual = measured;
    recordedBy = actor;
    recordedAt = at;
    state =
        measured.deliveryLength().compareTo(cutDeliveryQuantity) == 0 && conserved(measured)
            ? StockUnitCutPlanState.RECORDED
            : StockUnitCutPlanState.DISPUTED;
  }

  public void correct(Actual measured, UUID actor, Instant at) {
    require(StockUnitCutPlanState.DISPUTED);
    validateMeasurements(measured);
    actual = measured;
    // The original physical record and recorder remain immutable evidence.
    if (measured.deliveryLength().compareTo(cutDeliveryQuantity) == 0 && conserved(measured))
      state = StockUnitCutPlanState.RECORDED;
  }

  private void validateMeasurements(Actual measured) {
    if (measured == null
        || !positive(measured.deliveryLength())
        || !positive(measured.deliveryWeight())
        || !nonNegative(measured.remainderLength())
        || !nonNegative(measured.lossLength())
        || !nonNegative(measured.remainderWeight())
        || !nonNegative(measured.lossWeight())
        || (measured.remainderLength().signum() == 0) != (measured.remainderWeight().signum() == 0))
      throw StockUnitCutPlanException.invalid(
          "MEASUREMENTS_INVALID",
          "Record positive delivery measurements and explicit non-negative remainder and loss measurements");
    if ((measured.lossLength().signum() > 0 || measured.lossWeight().signum() > 0)
        && (measured.reason() == null || measured.reason().isBlank()))
      throw StockUnitCutPlanException.invalid(
          "LOSS_REASON_REQUIRED", "Actual loss needs an explicit explanation");
  }

  private static boolean positive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private static boolean nonNegative(BigDecimal value) {
    return value != null && value.signum() >= 0;
  }

  public boolean conserved(Actual measured) {
    return cutSource()
                .length()
                .compareTo(
                    measured
                        .deliveryLength()
                        .add(measured.remainderLength())
                        .add(measured.lossLength()))
            == 0
        && cutSource()
                .weight()
                .compareTo(
                    measured
                        .deliveryWeight()
                        .add(measured.remainderWeight())
                        .add(measured.lossWeight()))
            == 0;
  }

  public void complete(UUID delivery, UUID remainder, UUID actor, Instant at) {
    require(StockUnitCutPlanState.RECORDED);
    if (!conserved(actual) || actual.deliveryLength().compareTo(cutDeliveryQuantity) != 0)
      throw StockUnitCutPlanException.conflict(
          "CONSERVATION_MISMATCH", "Resolve the actual measurements before verification");
    deliveryStockUnitId = delivery;
    remainderStockUnitId = remainder;
    verifiedBy = actor;
    verifiedAt = at;
    state = StockUnitCutPlanState.COMPLETED;
  }

  public void cancel(String reason) {
    if (state == StockUnitCutPlanState.COMPLETED || state == StockUnitCutPlanState.CANCELLED)
      return;
    dispositionReason = reason;
    state = recordedAt == null ? StockUnitCutPlanState.CANCELLED : StockUnitCutPlanState.DISPUTED;
  }

  public void transfer(String type, UUID document, UUID line, String fingerprint) {
    if (recordedAt != null)
      throw StockUnitCutPlanException.conflict(
          "ALREADY_CUT", "Recorded physical work cannot change its commercial target");
    targetType = type;
    targetId = document;
    targetLineId = line;
    targetFingerprint = fingerprint;
  }

  private void require(StockUnitCutPlanState expected) {
    if (state != expected)
      throw StockUnitCutPlanException.conflict(
          "STATE_CHANGED", "The cut plan no longer permits this action");
  }

  @Override
  protected String getModuleCode() {
    return "CUTP";
  }
}
