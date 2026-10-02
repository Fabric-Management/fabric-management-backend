package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fabricmanagement.sales.salesorder.domain.DeliveryEvent;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderFlowStage;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import io.swagger.v3.oas.annotations.media.Schema;
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

  /** Where the order stands in sales → planning → customer approval. */
  private OrderFlowStage flowStage;

  private OrderStatus statusBeforeHold;
  private String rejectionReason;
  private LocalDate orderDate;

  /** The delivery date the customer asked for, as they said it. */
  private LocalDate requestedDeliveryDate;

  /** The current agreed committed date for {@link #deliveryEvent}; see the commitment history. */
  private LocalDate committedOn;

  private DeliveryTerm deliveryTerm;
  private String deliveryPlace;
  private IncotermsVersion incotermsVersion;
  private DeliveryTermStatus deliveryTermStatus;
  private String deliveryContractReference;

  /** The event the order's dates refer to; null while no delivery term is agreed. */
  private DeliveryEvent deliveryEvent;

  private LocalDate actualDeliveryDate;

  /** Agreed payment terms. */
  private String paymentTerms;

  /** Where the conversation that led to the order took place; context, not acceptance. */
  private AgreementContext agreementContext;

  private String agreementContextNote;

  /** Customer contact person for this order, with how to reach them. */
  private String contactName;

  private String contactEmail;
  private String contactPhone;
  private boolean contactWhatsapp;

  /**
   * Totals per agreed currency, largest grand total first. Derived from the active, not-cancelled
   * lines; amounts in different currencies are never added together.
   */
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  @Builder.Default
  private List<SalesOrderCurrencyTotalDto> totals = Collections.emptyList();

  /** Lines without an agreed price; they are not part of {@link #totals}. */
  @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
  private int unpricedLineCount;

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
  public static SalesOrderDto from(SalesOrder order, OrderCurrencyTotals totals) {
    return from(order, null, Collections.emptyList(), totals);
  }

  // No from(order, partner) overload: it silently substituted an empty line list, and createOrder
  // used it to answer a request that had just persisted lines. A caller holding a partner is
  // answering a single-order query and should pass the lines explicitly, even if that is
  // Collections.emptyList().

  /** Create DTO from entity with partner info and embedded lines. */
  public static SalesOrderDto from(
      SalesOrder order,
      TradingPartnerDto partner,
      List<SalesOrderLineResponse> lines,
      OrderCurrencyTotals totals) {
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
        .flowStage(order.getFlowStage())
        .statusBeforeHold(order.getStatusBeforeHold())
        .rejectionReason(order.getRejectionReason())
        .orderDate(order.getOrderDate())
        .requestedDeliveryDate(order.getRequestedDeliveryDate())
        .committedOn(order.getCommittedOn())
        .deliveryTerm(order.getDeliveryTerm())
        .deliveryPlace(order.getDeliveryPlace())
        .incotermsVersion(order.getIncotermsVersion())
        .deliveryTermStatus(order.getDeliveryTermStatus())
        .deliveryContractReference(order.getDeliveryContractReference())
        .deliveryEvent(order.getDeliveryEvent())
        .actualDeliveryDate(order.getActualDeliveryDate())
        .paymentTerms(order.getPaymentTerms())
        .agreementContext(order.getAgreementContext())
        .agreementContextNote(order.getAgreementContextNote())
        .contactName(order.getContactName())
        .contactEmail(order.getContactEmail())
        .contactPhone(order.getContactPhone())
        .contactWhatsapp(order.isContactWhatsapp())
        .totals(totals.totals().stream().map(SalesOrderCurrencyTotalDto::from).toList())
        .unpricedLineCount(totals.unpricedLineCount())
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
