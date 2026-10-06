package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** A line's finished width with its unit. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineWidthValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderLineWidthValue(
    @DecimalMin(value = "0.01", message = "Finished width must be positive")
        @Digits(integer = 6, fraction = 2, message = "Widths have at most two decimals")
        @Schema(minimum = "0.01", description = "One of the product's defined finished widths")
        BigDecimal value,
    @jakarta.validation.constraints.Pattern(
            regexp = "CM|IN|cm|in",
            message = "Width unit must be CM or IN")
        @Schema(allowableValues = {"CM", "IN"})
        String unit) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLineWidthValue property: " + name);
  }
}
