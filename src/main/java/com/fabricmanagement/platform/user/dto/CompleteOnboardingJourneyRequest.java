package com.fabricmanagement.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    description =
        "Complete frozen accepted evidence. Company journey requires the most recent"
            + " accepted review operation ID and all six steps; no mutable profile payload is accepted.")
public record CompleteOnboardingJourneyRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull UUID operationId,
    UUID reviewOperationId) {
  @JsonAnySetter
  public void rejectUnknown(String field, Object ignored) {
    throw new IllegalArgumentException("Unknown onboarding completion field");
  }
}
