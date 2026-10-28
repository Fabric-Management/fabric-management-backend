package com.fabricmanagement.platform.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.ZoneId;

@Schema(description = "Change the authenticated user's timezone without changing their language")
public record UpdateUserTimezonePreferenceRequest(
    @Schema(
            description = "IANA timezone identifier; the saved language is preserved",
            example = "Europe/London",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        @Size(min = 1, max = 50)
        String preferredTimezone) {
  @JsonIgnore
  @Schema(hidden = true)
  @AssertTrue(message = "Timezone must be a recognised IANA identifier")
  public boolean isTimezoneValid() {
    return preferredTimezone == null || ZoneId.getAvailableZoneIds().contains(preferredTimezone);
  }
}
