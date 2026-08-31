package com.fabricmanagement.product.fiber.dto;

import com.fabricmanagement.product.fiber.domain.MaterialSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request for submitting a new fiber request (tenant → platform). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateFiberRequestRequest {

  @NotBlank(message = "ISO code is required")
  @Size(max = 20)
  private String isoCode;

  @NotBlank(message = "Fiber name is required")
  @Size(max = 255)
  private String fiberName;

  @NotBlank(message = "Fiber type (category code) is required")
  @Size(max = 50)
  private String fiberType;

  /** Optional for a genuinely new code; required when requesting a variant of an existing code. */
  private MaterialSource materialSource;

  @Size(max = 2000)
  private String description;
}
