package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for overriding batch status (e.g. QC_REJECTED → AVAILABLE). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OverrideStatusRequest {

  @NotBlank(message = "Reason is required")
  @Size(min = 10, message = "Reason must be at least 10 characters")
  private String reason;
}
