package com.fabricmanagement.production.execution.inventory.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

/** Tedarikçi iade oranı eşiği aştı — CRITICAL. Kalite ve tedarik yöneticisine anında bildirim. */
@Getter
public class ReturnRateExceededEvent extends DomainEvent {

  private final UUID supplierId;
  private final String supplierName;
  private final BigDecimal returnRate; // %
  private final BigDecimal thresholdRate; // %
  private final int periodDays;

  @JsonCreator
  public ReturnRateExceededEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("supplierId") UUID supplierId,
      @JsonProperty("supplierName") String supplierName,
      @JsonProperty("returnRate") BigDecimal returnRate,
      @JsonProperty("thresholdRate") BigDecimal thresholdRate,
      @JsonProperty("periodDays") int periodDays) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "RETURN_RATE_EXCEEDED",
        occurredAt,
        correlationId);
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.returnRate = returnRate;
    this.thresholdRate = thresholdRate;
    this.periodDays = periodDays;
  }

  public ReturnRateExceededEvent(
      UUID tenantId,
      UUID supplierId,
      String supplierName,
      BigDecimal returnRate,
      BigDecimal thresholdRate,
      int periodDays) {
    super(tenantId, "RETURN_RATE_EXCEEDED");
    this.supplierId = supplierId;
    this.supplierName = supplierName;
    this.returnRate = returnRate;
    this.thresholdRate = thresholdRate;
    this.periodDays = periodDays;
  }
}
