package com.fabricmanagement.order.sales.dto;

import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.order.sales.domain.ModuleType;
import com.fabricmanagement.order.sales.domain.OrderStatus;
import com.fabricmanagement.order.sales.domain.OrderType;
import com.fabricmanagement.order.sales.domain.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** DTO for SalesOrder entity — includes embedded SalesOrderLine list. */
@Data
@Builder
public class SalesOrderDto {
  private UUID id;
  private String uid;
  private UUID tradingPartnerId;
  private TradingPartnerDto tradingPartner;
  private String orderNumber;
  private String customerReference;
  private OrderType orderType;
  private OrderStatus status;
  private LocalDate orderDate;
  private LocalDate requestedDeliveryDate;
  private LocalDate promisedDeliveryDate;
  private LocalDate actualDeliveryDate;
  private BigDecimal totalAmount;
  private BigDecimal taxAmount;
  private BigDecimal discountAmount;
  private BigDecimal grandTotal;
  private String currency;
  private String shippingAddress;
  private String billingAddress;
  private String shippingMethod;
  private String notes;
  private Map<String, Object> metadata;
  private Boolean isActive;
  private Instant createdAt;
  private Instant updatedAt;

  // ── Faz 2 ────────────────────────────────────────────────────────────────
  private ModuleType moduleType;
  private LocalDate deadline;
  private UUID quoteId;
  private UUID sampleRequestId;

  /** Embedded order lines — populated by SalesOrderService.findById (not findAll for perf). */
  @Builder.Default private List<SalesOrderLineResponse> lines = Collections.emptyList();

  /** Create DTO from entity (no lines — used for list queries). */
  public static SalesOrderDto from(SalesOrder order) {
    return from(order, null, Collections.emptyList());
  }

  /** Create DTO from entity with partner info (no lines — used for list queries). */
  public static SalesOrderDto from(SalesOrder order, TradingPartnerDto partner) {
    return from(order, partner, Collections.emptyList());
  }

  /** Create DTO from entity with partner info and embedded lines. */
  public static SalesOrderDto from(
      SalesOrder order, TradingPartnerDto partner, List<SalesOrderLineResponse> lines) {
    return SalesOrderDto.builder()
        .id(order.getId())
        .uid(order.getUid())
        .tradingPartnerId(order.getTradingPartnerId())
        .tradingPartner(partner)
        .orderNumber(order.getOrderNumber())
        .customerReference(order.getCustomerReference())
        .orderType(order.getOrderType())
        .status(order.getStatus())
        .orderDate(order.getOrderDate())
        .requestedDeliveryDate(order.getRequestedDeliveryDate())
        .promisedDeliveryDate(order.getPromisedDeliveryDate())
        .actualDeliveryDate(order.getActualDeliveryDate())
        .totalAmount(order.getTotalAmount())
        .taxAmount(order.getTaxAmount())
        .discountAmount(order.getDiscountAmount())
        .grandTotal(order.getGrandTotal())
        .currency(order.getCurrency())
        .shippingAddress(order.getShippingAddress())
        .billingAddress(order.getBillingAddress())
        .shippingMethod(order.getShippingMethod())
        .notes(order.getNotes())
        .metadata(order.getMetadata())
        .isActive(order.getIsActive())
        .createdAt(order.getCreatedAt())
        .updatedAt(order.getUpdatedAt())
        .moduleType(order.getModuleType())
        .deadline(order.getDeadline())
        .quoteId(order.getQuoteId())
        .sampleRequestId(order.getSampleRequestId())
        .lines(lines != null ? lines : Collections.emptyList())
        .build();
  }
}
