package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import io.swagger.v3.oas.annotations.media.Schema;
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
  private Long version;
  private UUID tradingPartnerId;
  private TradingPartnerDto tradingPartner;
  private String orderNumber;
  private String customerReference;
  private OrderType orderType;
  private OrderStatus status;
  private OrderStatus statusBeforeHold;
  private String rejectionReason;
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

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
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

  // No from(order, partner) overload: it silently substituted an empty line list, and createOrder
  // used it to answer a request that had just persisted lines. A caller holding a partner is
  // answering a single-order query and should pass the lines explicitly, even if that is
  // Collections.emptyList().

  /** Create DTO from entity with partner info and embedded lines. */
  public static SalesOrderDto from(
      SalesOrder order, TradingPartnerDto partner, List<SalesOrderLineResponse> lines) {
    return SalesOrderDto.builder()
        .id(order.getId())
        .uid(order.getUid())
        .version(order.getVersion())
        .tradingPartnerId(order.getTradingPartnerId())
        .tradingPartner(partner)
        .orderNumber(order.getOrderNumber())
        .customerReference(order.getCustomerReference())
        .orderType(order.getOrderType())
        .status(order.getStatus())
        .statusBeforeHold(order.getStatusBeforeHold())
        .rejectionReason(order.getRejectionReason())
        .orderDate(order.getOrderDate())
        .requestedDeliveryDate(order.getRequestedDeliveryDate())
        .promisedDeliveryDate(order.getPromisedDeliveryDate())
        .actualDeliveryDate(order.getActualDeliveryDate())
        .totalAmount(
            order.getTotals() != null ? order.getTotals().getTotalAmount().getAmount() : null)
        .taxAmount(order.getTotals() != null ? order.getTotals().getTaxAmount().getAmount() : null)
        .discountAmount(
            order.getTotals() != null ? order.getTotals().getDiscountAmount().getAmount() : null)
        .grandTotal(order.getGrandTotal() != null ? order.getGrandTotal().getAmount() : null)
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
