package com.fabricmanagement.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

@Schema(
    additionalProperties = Schema.AdditionalPropertiesValue.FALSE,
    description =
        "Optimistic command. Same operation ID and canonical typed payload replay the"
            + " original result after fresh authorisation. Changed payload/version or another actor conflicts.")
public record SaveOnboardingStepRequest(
    @NotNull @PositiveOrZero Long expectedVersion,
    @NotNull UUID operationId,
    @NotNull @Valid OnboardingStepPayload payload) {
  @JsonAnySetter
  public void rejectUnknown(String field, Object ignored) {
    throw new IllegalArgumentException("Unknown onboarding command field");
  }
}
