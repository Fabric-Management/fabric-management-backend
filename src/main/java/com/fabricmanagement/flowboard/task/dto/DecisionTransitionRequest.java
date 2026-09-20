package com.fabricmanagement.flowboard.task.dto;

import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(
    name = "DecisionTransitionRequest",
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record DecisionTransitionRequest(
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Action action,
    @NotNull @Min(0) @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "0")
        Long expectedVersion,
    @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID idempotencyKey,
    @NotNull @Valid @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        ConfirmProductionCoverPayload payload) {
  public DecisionTransitionRequest {
    java.util.Objects.requireNonNull(action, "Decision action is required");
    java.util.Objects.requireNonNull(expectedVersion, "Expected task version is required");
    java.util.Objects.requireNonNull(idempotencyKey, "Idempotency key is required");
    java.util.Objects.requireNonNull(payload, "Decision payload is required");
  }

  @Schema(name = "DecisionTransitionAction")
  public enum Action {
    CONFIRM_PRODUCTION_COVER
  }

  @JsonAnySetter
  public void rejectUnknownProperty(String name, Object value) {
    throw new IllegalArgumentException("Unknown decision-transition field: " + name);
  }
}
