package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.ModuleType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class UpdateSalesOrderRequest {

  @NotNull(message = "Version is required for optimistic locking")
  private Long version;

  private String customerReference;

  @NotNull(message = "Order date is required")
  private LocalDate orderDate;

  /** The date the customer asked for, for the event of the agreed delivery term. */
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

  private String shippingAddress;
  private String billingAddress;
  private String shippingMethod;
  private String notes;

  @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  private Map<String, Object> metadata;

  @Deprecated
  @Schema(description = "Deprecated: ignored; derived from line module types.", deprecated = true)
  private ModuleType moduleType;

  private LocalDate deadline;

  @Valid private List<UpdateSalesOrderLineRequest> lines = new ArrayList<>();
}
