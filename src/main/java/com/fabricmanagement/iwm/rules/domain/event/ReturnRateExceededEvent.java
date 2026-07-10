package com.fabricmanagement.iwm.rules.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ReturnRateExceededEvent extends DomainEvent {
  private final UUID tradingPartnerId;
  private final BigDecimal currentRate;
  private final BigDecimal thresholdRate;
  private final Integer windowDays;

  @JsonCreator
  public ReturnRateExceededEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("tradingPartnerId") UUID tradingPartnerId,
      @JsonProperty("currentRate") BigDecimal currentRate,
      @JsonProperty("thresholdRate") BigDecimal thresholdRate,
      @JsonProperty("windowDays") Integer windowDays) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RETURN_RATE_EXCEEDED",
        occurredAt,
        correlationId);
    this.tradingPartnerId = tradingPartnerId;
    this.currentRate = currentRate;
    this.thresholdRate = thresholdRate;
    this.windowDays = windowDays;
  }

  @Builder
  public ReturnRateExceededEvent(
      UUID tenantId,
      UUID tradingPartnerId,
      BigDecimal currentRate,
      BigDecimal thresholdRate,
      Integer windowDays) {
    super(tenantId, "RETURN_RATE_EXCEEDED");
    this.tradingPartnerId = tradingPartnerId;
    this.currentRate = currentRate;
    this.thresholdRate = thresholdRate;
    this.windowDays = windowDays;
  }
}
