package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/** One leasable key of a sales order (CEDIT-07 §4): a key, and the line it is on for line keys. */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderEditLeaseKey",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    description =
        "A header key has no lineId; a line key and line (the whole line) need the id of an active"
            + " line of this order.")
public record SalesOrderEditLeaseKey(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesOrderEditLeaseField key,
    @Schema(
            requiredMode = Schema.RequiredMode.NOT_REQUIRED,
            nullable = true,
            format = "uuid",
            description = "The line, for line keys and line; absent or null for header keys.")
        UUID lineId) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderEditLeaseKey property: " + name);
  }
}
