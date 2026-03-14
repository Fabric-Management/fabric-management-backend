package com.fabricmanagement.production.masterdata.fiber.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for rejecting a fiber request (platform). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectFiberRequestRequest {

  @NotBlank(message = "Review note is required")
  @Size(min = 10, message = "Review note must be at least 10 characters")
  private String reviewNote;
}
