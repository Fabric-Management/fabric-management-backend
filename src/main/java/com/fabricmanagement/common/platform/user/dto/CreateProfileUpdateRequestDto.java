package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO for creating a profile update request. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProfileUpdateRequestDto {

  /** Category of profile being updated. */
  @NotNull(message = "Profile category is required")
  private ProfileCategory profileCategory;

  /**
   * Requested changes in JSON format. Example: {"firstName": "John", "lastName": "Smith",
   * "personalPhone": "+1234567890"}
   */
  private JsonNode requestedChanges;

  /** Reason provided by user for the request. */
  private String reason;
}
