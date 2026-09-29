package com.fabricmanagement.sales.orderintake.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * When the goods are expected at the customer, from carrier information or an authorised person's
 * sourced record (SOI A07-b). Without such a record the arrival stays unknown; a readiness date is
 * never shown as a delivery date.
 */
@Entity
@Table(name = "order_arrival_estimate", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderArrivalEstimate extends BaseEntity {

  @Schema(name = "ArrivalEstimateSource", enumAsRef = true)
  public enum Source {
    CARRIER,
    AUTHORISED_RECORD
  }

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "earliest_on", nullable = false, updatable = false)
  private LocalDate earliestOn;

  @Column(name = "latest_on", nullable = false, updatable = false)
  private LocalDate latestOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, updatable = false, length = 30)
  private Source source;

  @Column(name = "source_reference", nullable = false, updatable = false, length = 500)
  private String sourceReference;

  @Column(name = "recorded_by", nullable = false, updatable = false)
  private UUID recordedBy;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt;

  @Column(name = "superseded_at")
  private Instant supersededAt;

  public static OrderArrivalEstimate record(
      UUID salesOrderId,
      LocalDate earliestOn,
      LocalDate latestOn,
      Source source,
      String sourceReference,
      UUID recordedBy,
      Instant recordedAt) {
    if (salesOrderId == null || earliestOn == null || latestOn == null || source == null) {
      throw new IllegalArgumentException("Order, dates and source are required");
    }
    if (latestOn.isBefore(earliestOn)) {
      throw new IllegalArgumentException("The latest date cannot precede the earliest");
    }
    if (sourceReference == null || sourceReference.isBlank()) {
      throw new IllegalArgumentException("Name the source: carrier reference or who said so");
    }
    if (recordedBy == null || recordedAt == null) {
      throw new IllegalArgumentException("Recorder and time are required");
    }
    OrderArrivalEstimate estimate = new OrderArrivalEstimate();
    estimate.salesOrderId = salesOrderId;
    estimate.earliestOn = earliestOn;
    estimate.latestOn = latestOn;
    estimate.source = source;
    estimate.sourceReference = sourceReference.trim();
    estimate.recordedBy = recordedBy;
    estimate.recordedAt = recordedAt;
    return estimate;
  }

  public void supersede(Instant at) {
    if (supersededAt == null) {
      this.supersededAt = at;
    }
  }

  @Override
  protected String getModuleCode() {
    return "OAE";
  }
}
