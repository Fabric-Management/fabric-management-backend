package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a fiber test result receives an approval decision (APPROVED, REJECTED,
 * CONDITIONAL_ACCEPT).
 *
 * <p>Listeners (e.g. BatchStatusUpdateListener) use this to transition the associated batch from
 * PENDING_QC to AVAILABLE (on APPROVED/CONDITIONAL_ACCEPT) or QC_REJECTED (on REJECTED).
 */
@Getter
public class FiberTestResultApprovedEvent extends DomainEvent {

  private final UUID batchId;
  private final UUID stockUnitId;
  private final TestApprovalStatus approvalStatus;
  private final UUID actorId;

  public FiberTestResultApprovedEvent(
      UUID tenantId,
      UUID batchId,
      UUID stockUnitId,
      TestApprovalStatus approvalStatus,
      UUID actorId) {
    super(tenantId, "FIBER_TEST_RESULT_APPROVED");
    this.batchId = batchId;
    this.stockUnitId = stockUnitId;
    this.approvalStatus = approvalStatus;
    this.actorId = actorId;
  }

  @JsonCreator
  public FiberTestResultApprovedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("stockUnitId") UUID stockUnitId,
      @JsonProperty("approvalStatus") TestApprovalStatus approvalStatus,
      @JsonProperty("actorId") UUID actorId) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "FIBER_TEST_RESULT_APPROVED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.stockUnitId = stockUnitId;
    this.approvalStatus = approvalStatus;
    this.actorId = actorId;
  }
}
