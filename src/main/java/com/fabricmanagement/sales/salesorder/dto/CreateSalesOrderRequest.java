package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.orderintake.dto.CustomerRequestDtos;
import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import com.fabricmanagement.sales.salesorder.domain.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

  @Schema(
      description =
          "Client-generated key of this create request. Repeating the request with the same key"
              + " (for example a retried autosave) returns the order it created instead of a"
              + " second one.")
  private UUID idempotencyKey;

  /** Customer's purchase order reference. */
  private String customerReference;

  /** Order type. */
  private OrderType orderType = OrderType.SALES;

  /** Order date. */
  @NotNull(message = "Order date is required")
  private LocalDate orderDate;

  /**
   * The date the customer asked for, for the event of the agreed delivery term. The committed date
   * is not entered here: it is recorded as a delivery commitment agreed with the customer.
   */
  private LocalDate requestedDeliveryDate;

  @Schema(description = "Incoterms rule agreed for the order; omit until one is agreed")
  private DeliveryTerm deliveryTerm;

  @Schema(
      description = "Place named with the term (under C-terms the destination)",
      maxLength = 200)
  @jakarta.validation.constraints.Size(max = 200, message = "The named place is too long")
  private String deliveryPlace;

  @Schema(description = "Incoterms edition; defaults to 2020 when a term is given")
  private IncotermsVersion incotermsVersion;

  @Schema(
      description =
          "Proposed (default) or agreed by a prior contract; agreement by the customer is set only"
              + " by the customer's approval")
  private DeliveryTermStatus deliveryTermStatus;

  @Schema(description = "The contract that fixed the term", maxLength = 200)
  @jakarta.validation.constraints.Size(max = 200, message = "The contract reference is too long")
  private String deliveryContractReference;

  /**
   * Agreed payment terms. Currencies, prices, discounts and taxes are agreed per line; the order
   * has no currency of its own.
   */
  @Schema(description = "Agreed payment terms", maxLength = 200)
  @jakarta.validation.constraints.Size(max = 200, message = "Payment terms are too long")
  private String paymentTerms;

  @Schema(description = "Where the conversation that led to the order took place")
  private AgreementContext agreementContext;

  @Schema(description = "Description of an OTHER context", maxLength = 500)
  @jakarta.validation.constraints.Size(max = 500, message = "The description is too long")
  private String agreementContextNote;

  @Schema(description = "Customer contact person for this order", maxLength = 120)
  @jakarta.validation.constraints.Size(max = 120, message = "Contact name is too long")
  private String contactName;

  @Schema(description = "Contact e-mail", maxLength = 254)
  @jakarta.validation.constraints.Size(max = 254, message = "Contact e-mail is too long")
  @jakarta.validation.constraints.Email(message = "Contact e-mail is not valid")
  private String contactEmail;

  @Schema(description = "Contact mobile phone", maxLength = 30)
  @jakarta.validation.constraints.Size(max = 30, message = "Contact phone is too long")
  private String contactPhone;

  @Schema(description = "Whether the contact may be notified on WhatsApp at this phone")
  private Boolean contactWhatsapp;

  /** Shipping address. */
  private String shippingAddress;

  /** Billing address. */
  private String billingAddress;

  /** Shipping method. */
  private String shippingMethod;

  /** Notes. */
  private String notes;

  /** Additional metadata. */
  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> metadata;

  // ── Faz 2 additions ─────────────────────────────────────────────────────

  /**
   * Deprecated: ignored on create. Order module type is derived from line module types at write
   * time.
   */
  @Deprecated
  @Schema(description = "Deprecated: ignored; derived from line module types.", deprecated = true)
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

  /** Customer-specific requests recorded atomically with the draft, before a product is known. */
  @Valid
  @Size(max = 100)
  @Schema(
      description =
          "Optional customer requests saved in the same transaction as the draft. "
              + "They remain requests until evaluated and approved; they are not unbound order lines.")
  private List<CustomerRequestDtos.@NotNull RequestInput> customRequests = new ArrayList<>();
}
