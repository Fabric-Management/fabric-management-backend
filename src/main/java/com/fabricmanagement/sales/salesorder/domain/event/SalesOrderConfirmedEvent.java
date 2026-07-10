package com.fabricmanagement.sales.salesorder.domain.event;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;

/**
 * Satış siparişi onaylandığında yayınlanır.
 *
 * <p>SmartTaskGeneratorListener bu eventi dinler ve PLANNING task oluşturur. StockControlEngine
 * tetiklenebilir (yeterli stok analizi).
 */
@Getter
public class SalesOrderConfirmedEvent extends DomainEvent {

  private final UUID salesOrderId;
  private final String orderNumber;
  private final UUID customerId;
  private final String customerName;
  private final BigDecimal totalQuantity;
  private final String unit;
  private final LocalDate requestedDeliveryDate;
  private final java.util.List<SalesOrderLineSnapshot> lines;

  public record SalesOrderLineSnapshot(
      UUID lineId,
      UUID productId,
      String productCode,
      BigDecimal quantity,
      String unit,
      LocalDate requestedDeliveryDate) {}

  public SalesOrderConfirmedEvent(
      UUID tenantId,
      UUID salesOrderId,
      String orderNumber,
      UUID customerId,
      String customerName,
      BigDecimal totalQuantity,
      String unit,
      LocalDate requestedDeliveryDate,
      java.util.List<SalesOrderLineSnapshot> lines) {
    super(tenantId, "SalesOrderConfirmed");
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.customerId = customerId;
    this.customerName = customerName;
    this.totalQuantity = totalQuantity;
    this.unit = unit;
    this.requestedDeliveryDate = requestedDeliveryDate;
    this.lines = lines != null ? lines : java.util.Collections.emptyList();
  }

  @JsonCreator
  public SalesOrderConfirmedEvent(
      @JsonProperty("eventId") UUID eventId,
      @JsonProperty("tenantId") UUID tenantId,
      @JsonProperty("eventType") String eventType,
      @JsonProperty("occurredAt") Instant occurredAt,
      @JsonProperty("correlationId") String correlationId,
      @JsonProperty("salesOrderId") UUID salesOrderId,
      @JsonProperty("orderNumber") String orderNumber,
      @JsonProperty("customerId") UUID customerId,
      @JsonProperty("customerName") String customerName,
      @JsonProperty("totalQuantity") BigDecimal totalQuantity,
      @JsonProperty("unit") String unit,
      @JsonProperty("requestedDeliveryDate") LocalDate requestedDeliveryDate,
      @JsonProperty("lines") java.util.List<SalesOrderLineSnapshot> lines) {
    super(
        eventId,
        tenantId,
        eventType != null ? eventType : "SalesOrderConfirmed",
        occurredAt,
        correlationId);
    this.salesOrderId = salesOrderId;
    this.orderNumber = orderNumber;
    this.customerId = customerId;
    this.customerName = customerName;
    this.totalQuantity = totalQuantity;
    this.unit = unit;
    this.requestedDeliveryDate = requestedDeliveryDate;
    this.lines = lines != null ? lines : java.util.Collections.emptyList();
  }
}
