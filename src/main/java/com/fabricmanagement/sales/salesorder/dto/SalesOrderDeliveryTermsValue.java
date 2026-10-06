package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** The delivery term of a safe-edit instruction; compared after the domain's own normalisation. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderDeliveryTermsValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderDeliveryTermsValue(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryTerm term,
    @Size(max = 200, message = "The named place is too long")
        @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            maxLength = 200,
            description = "Place named with the term (under C-terms the destination)")
        String place,
    @Schema(description = "Incoterms edition; the current edition when omitted")
        IncotermsVersion incotermsVersion,
    @Schema(
            description =
                "Proposed when omitted; agreement by the customer comes only from approval")
        DeliveryTermStatus status,
    @Size(max = 200, message = "The contract reference is too long")
        @Schema(maxLength = 200, description = "The contract that fixed the term")
        String contractReference) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderDeliveryTermsValue property: " + name);
  }
}
