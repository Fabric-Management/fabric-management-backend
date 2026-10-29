package com.fabricmanagement.production.core.batch.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/**
 * Published when a finishing step is done and the lot is back (FABRIC-3b). Carries what a cost or
 * notification listener needs without reading the step again: the metres that went and came back,
 * the colour the step gave and what the firm charged.
 */
@Getter
public class BatchFinishingReceivedEvent extends DomainEvent {
  private final UUID batchId;
  private final UUID stepId;
  private final UUID processId;
  private final UUID partnerId;
  private final String firmJobNumber;
  private final BigDecimal sentMetres;
  private final BigDecimal returnedMetres;
  private final UUID colourId;
  private final BigDecimal costAmount;
  private final String costCurrency;

  @JsonCreator
  public BatchFinishingReceivedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("stepId") UUID stepId,
      @JsonProperty("processId") UUID processId,
      @JsonProperty("partnerId") UUID partnerId,
      @JsonProperty("firmJobNumber") String firmJobNumber,
      @JsonProperty("sentMetres") BigDecimal sentMetres,
      @JsonProperty("returnedMetres") BigDecimal returnedMetres,
      @JsonProperty("colourId") UUID colourId,
      @JsonProperty("costAmount") BigDecimal costAmount,
      @JsonProperty("costCurrency") String costCurrency) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "BATCH_FINISHING_RECEIVED",
        occurredAt,
        correlationId);
    this.batchId = batchId;
    this.stepId = stepId;
    this.processId = processId;
    this.partnerId = partnerId;
    this.firmJobNumber = firmJobNumber;
    this.sentMetres = sentMetres;
    this.returnedMetres = returnedMetres;
    this.colourId = colourId;
    this.costAmount = costAmount;
    this.costCurrency = costCurrency;
  }

  public BatchFinishingReceivedEvent(
      UUID tenantId,
      UUID batchId,
      UUID stepId,
      UUID processId,
      UUID partnerId,
      String firmJobNumber,
      BigDecimal sentMetres,
      BigDecimal returnedMetres,
      UUID colourId,
      BigDecimal costAmount,
      String costCurrency) {
    super(tenantId, "BATCH_FINISHING_RECEIVED");
    this.batchId = batchId;
    this.stepId = stepId;
    this.processId = processId;
    this.partnerId = partnerId;
    this.firmJobNumber = firmJobNumber;
    this.sentMetres = sentMetres;
    this.returnedMetres = returnedMetres;
    this.colourId = colourId;
    this.costAmount = costAmount;
    this.costCurrency = costCurrency;
  }
}
