package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/** The customer's contact person for the order; blank parts are empty. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderContactValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderContactValue(
    @Size(max = 120, message = "Contact name is too long") @Schema(maxLength = 120) String name,
    @Size(max = 254, message = "Contact e-mail is too long")
        @Email(message = "Contact e-mail is not valid")
        @Schema(maxLength = 254)
        String email,
    @Size(max = 30, message = "Contact phone is too long") @Schema(maxLength = 30) String phone,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "WhatsApp needs a phone number; true without one is refused")
        Boolean whatsapp) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderContactValue property: " + name);
  }
}
