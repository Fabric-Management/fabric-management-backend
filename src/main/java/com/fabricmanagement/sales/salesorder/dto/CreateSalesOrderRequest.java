package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

/** Request DTO for creating a new sales order. */
@Data
public class CreateSalesOrderRequest {

  /**
   * Trading partner ID (customer for sales, supplier for purchase). Can be either a
   * TradingPartner.id or legacy Company.id - resolved by TradingPartnerResolver.
   */
  @NotNull(message = "Partner ID is required")
  private UUID partnerId;

  /** Customer's purchase order reference. */
  private String customerReference;

  /** Order type. */
  private OrderType orderType = OrderType.SALES;

  /** Order date. */
  @NotNull(message = "Order date is required")
  private LocalDate orderDate;

  /** Requested delivery date. */
  private LocalDate requestedDeliveryDate;

  /** Promised delivery date. */
  private LocalDate promisedDeliveryDate;

  /** Total amount (before tax). */
  private BigDecimal totalAmount;

  /** Tax amount. */
  private BigDecimal taxAmount;

  /** Discount amount. */
  private BigDecimal discountAmount;

  /** Currency code. */
  private String currency = "TRY";

  /** Shipping address. */
  private String shippingAddress;

  /** Billing address. */
  private String billingAddress;

  /** Shipping method. */
  private String shippingMethod;

  /** Notes. */
  private String notes;

  /** Additional metadata. */
  private Map<String, Object> metadata;

  // ── Faz 2 additions ─────────────────────────────────────────────────────

  /** Production module type for this order (FIBER / YARN / FABRIC / DYE_FINISHING). */
  private ModuleType moduleType;

  /** Customer-requested production and delivery deadline. */
  private LocalDate deadline;

  /** FK to Quote — when order originated from a converted quote. */
  private UUID quoteId;

  /** FK to SampleRequest — when order originated from a sample request. */
  private UUID sampleRequestId;

  /**
   * Order lines to create together with the order. Can be empty — lines can be added separately via
   * the line API. Each line is validated via {@code @Valid}.
   */
  @Valid private List<SalesOrderLineRequest> lines = new ArrayList<>();
}
