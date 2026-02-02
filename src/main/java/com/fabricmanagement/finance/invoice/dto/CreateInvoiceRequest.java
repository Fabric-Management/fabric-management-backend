package com.fabricmanagement.finance.invoice.dto;

import com.fabricmanagement.finance.invoice.domain.InvoiceType;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import lombok.Data;

/** Request DTO for creating a new invoice. */
@Data
public class CreateInvoiceRequest {

  /**
   * Trading partner ID (customer for AR, vendor for AP). Can be either a TradingPartner.id or
   * legacy Company.id - resolved by TradingPartnerResolver.
   */
  @NotNull(message = "Partner ID is required")
  private UUID partnerId;

  /** Reference to related order. */
  private String orderReference;

  /** External reference (customer PO number, etc.). */
  private String externalReference;

  /** Invoice type. */
  private InvoiceType invoiceType = InvoiceType.SALES;

  /** Issue date. */
  @NotNull(message = "Issue date is required")
  private LocalDate issueDate;

  /** Due date. */
  @NotNull(message = "Due date is required")
  private LocalDate dueDate;

  /** Subtotal (before tax and discount). */
  @NotNull(message = "Subtotal is required")
  private BigDecimal subtotal;

  /** Tax amount. */
  private BigDecimal taxAmount;

  /** Discount amount. */
  private BigDecimal discountAmount;

  /** Currency code. */
  private String currency = "TRY";

  /** Tax rate percentage. */
  private BigDecimal taxRate;

  /** Billing address. */
  private String billingAddress;

  /** Notes. */
  private String notes;

  /** Additional metadata. */
  private Map<String, Object> metadata;
}
