package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * The user's decision on one conflict of the base's origin (CEDIT-02 §5.7), identified like the
 * conflict: its key and, for a line, the line's id or the new line's client id.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "SalesOrderEditResolution",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record SalesOrderEditResolution(
    @NotBlank(message = "Name the conflict's key")
        @Size(max = 40)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "paymentTerms")
        String key,
    @Schema(description = "The conflict's line, when it is about an existing line") UUID lineId,
    @Schema(description = "The conflict's new line, when it is about an added line")
        UUID clientLineId,
    @NotNull(message = "Choose how the conflict is decided")
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        SalesOrderEditResolutionChoice choice) {

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown SalesOrderEditResolution property: " + name);
  }
}
