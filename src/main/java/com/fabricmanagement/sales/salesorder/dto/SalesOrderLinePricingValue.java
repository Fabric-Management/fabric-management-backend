package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/** A line's agreed price in its own currency; an omitted part is empty, zero is a value. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLinePricingValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderLinePricingValue(
    @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO code")
        @Schema(description = "Agreed sales currency of the line (ISO 4217)", example = "GBP")
        String currency,
    @DecimalMin(value = "0", message = "Unit price cannot be negative")
        @Digits(integer = 14, fraction = 4, message = "Prices have at most four decimals")
        @Schema(minimum = "0")
        BigDecimal unitPrice,
    @DecimalMin(value = "0", message = "Discount cannot be negative")
        @Digits(integer = 14, fraction = 4, message = "Amounts have at most four decimals")
        @Schema(minimum = "0")
        BigDecimal discountAmount,
    @DecimalMin(value = "0", message = "Tax cannot be negative")
        @Digits(integer = 14, fraction = 4, message = "Amounts have at most four decimals")
        @Schema(minimum = "0")
        BigDecimal taxAmount) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLinePricingValue property: " + name);
  }
}
