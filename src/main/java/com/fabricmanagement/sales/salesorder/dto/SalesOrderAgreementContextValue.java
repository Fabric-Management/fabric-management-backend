package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.sales.salesorder.domain.AgreementContext;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** Where the conversation that led to the order took place. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderAgreementContextValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderAgreementContextValue(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AgreementContext context,
    @Size(max = 500, message = "The description is too long")
        @Schema(maxLength = 500, description = "Description of an OTHER context")
        String note) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderAgreementContextValue property: " + name);
  }
}
