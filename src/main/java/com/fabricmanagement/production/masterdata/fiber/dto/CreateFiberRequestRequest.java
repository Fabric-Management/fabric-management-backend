package com.fabricmanagement.production.masterdata.fiber.dto;

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

  @Size(max = 2000)
  private String description;
}
