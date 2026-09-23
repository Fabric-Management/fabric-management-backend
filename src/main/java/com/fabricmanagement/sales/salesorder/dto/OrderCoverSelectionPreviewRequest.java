package com.fabricmanagement.sales.salesorder.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "OrderCoverSelectionPreviewRequest",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record OrderCoverSelectionPreviewRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
    @Min(1) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        long evidenceRevision,
    @NotEmpty
        @Size(max = 100)
        @ArraySchema(
            schema = @Schema(format = "uuid"),
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            maxItems = 100)
        List<@NotNull UUID> lineIds) {
  public OrderCoverSelectionPreviewRequest {
    lineIds = lineIds == null ? List.of() : List.copyOf(lineIds);
    if (new HashSet<>(lineIds).size() != lineIds.size()) {
      throw new IllegalArgumentException("Selected line IDs must be unique");
    }
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown production-cover preview field: " + name);
  }
}
