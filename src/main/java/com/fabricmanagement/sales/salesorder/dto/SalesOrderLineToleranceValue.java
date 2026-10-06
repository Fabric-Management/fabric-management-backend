package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

/** The quantity tolerance agreed for a line, in percent; zero is a value. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderLineToleranceValue",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderLineToleranceValue(
    @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        @Digits(integer = 3, fraction = 2, message = "Tolerances have at most two decimals")
        @Schema(minimum = "0", maximum = "100")
        BigDecimal upPct,
    @DecimalMin(value = "0", message = "A tolerance cannot be negative")
        @DecimalMax(value = "100", message = "A tolerance cannot exceed 100 percent")
        @Digits(integer = 3, fraction = 2, message = "Tolerances have at most two decimals")
        @Schema(minimum = "0", maximum = "100")
        BigDecimal downPct) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderLineToleranceValue property: " + name);
  }
}
