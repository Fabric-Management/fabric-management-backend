package com.fabricmanagement.sales.salesorder.dto;

import com.fabricmanagement.flowboard.task.domain.TaskActionPayload;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "ConfirmProductionCoverPayload",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record ConfirmProductionCoverPayload(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID caseId,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID evidenceId,
    @Min(1) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
        long evidenceRevision,
    @NotEmpty
        @Size(max = 100)
        @ArraySchema(
            schema = @Schema(format = "uuid"),
            arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
            maxItems = 100)
        List<@NotNull UUID> lineIds,
    @Size(max = 1000)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 1000)
        String rationale,
    @Size(max = 1000)
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true, maxLength = 1000)
        String note)
    implements TaskActionPayload {
  public ConfirmProductionCoverPayload {
    lineIds = lineIds == null ? List.of() : List.copyOf(lineIds);
    if (new HashSet<>(lineIds).size() != lineIds.size())
      throw new IllegalArgumentException("Selected line IDs must be unique");
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown production-cover field: " + name);
  }
}
