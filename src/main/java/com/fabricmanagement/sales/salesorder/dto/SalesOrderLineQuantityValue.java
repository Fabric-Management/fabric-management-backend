package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** A line's requested quantity; 12.5 and 12.500 are the same quantity. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineQuantityValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderLineQuantityValue(
    @DecimalMin(value = "0.001", message = "Quantity must be greater than zero")
        @Digits(integer = 12, fraction = 3, message = "Quantities have at most three decimals")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0.001")
        BigDecimal requestedQty,
    @Size(max = 20, message = "Unit is too long")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 20)
        String unit) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLineQuantityValue property: " + name);
  }
}
