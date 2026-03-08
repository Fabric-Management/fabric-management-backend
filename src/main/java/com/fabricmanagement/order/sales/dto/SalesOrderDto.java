package com.fabricmanagement.order.sales.dto;

import com.fabricmanagement.common.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.order.sales.domain.OrderStatus;
import com.fabricmanagement.order.sales.domain.OrderType;
import com.fabricmanagement.order.sales.domain.SalesOrder;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Data;

/** DTO for SalesOrder entity. */
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

  /** Create DTO from entity. */
  public static SalesOrderDto from(SalesOrder order) {
    return from(order, null);
  }

  /** Create DTO from entity with partner info. */
  public static SalesOrderDto from(SalesOrder order, TradingPartnerDto partner) {
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
        .build();
  }
}
