package com.fabricmanagement.production.quality.result.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.production.quality.result.domain.FiberTestEvaluation.Parameter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** One or more comparable measurements in a newly recorded fibre test are outside their limits. */
@Getter
public class FiberMeasurementOutOfLimitsEvent extends DomainEvent {

  private static final String TYPE = "FIBER_MEASUREMENT_OUT_OF_LIMITS";

  private final UUID resultId;
  private final UUID batchId;
  private final String batchCode;
  private final Parameter firstParameter;
  private final int additionalCount;

  public FiberMeasurementOutOfLimitsEvent(
      UUID tenantId,
      UUID resultId,
      UUID batchId,
      String batchCode,
      Parameter firstParameter,
      int additionalCount) {
    this(
        null,
        tenantId,
        TYPE,
        null,
        null,
        resultId,
        batchId,
        batchCode,
        firstParameter,
        additionalCount);
  }

  @JsonCreator
  public FiberMeasurementOutOfLimitsEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("resultId") UUID resultId,
      @JsonProperty("batchId") UUID batchId,
      @JsonProperty("batchCode") String batchCode,
      @JsonProperty("firstParameter") Parameter firstParameter,
      @JsonProperty("additionalCount") int additionalCount) {
    super(eventId, tenantId, eventType != null ? eventType : TYPE, occurredAt, correlationId);
    this.resultId = resultId;
    this.batchId = batchId;
    this.batchCode = batchCode;
    this.firstParameter = firstParameter;
    this.additionalCount = additionalCount;
  }
}
