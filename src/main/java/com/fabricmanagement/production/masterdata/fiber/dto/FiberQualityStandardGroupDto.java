package com.fabricmanagement.production.masterdata.fiber.dto;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Response DTO for GET all: profiles grouped by iso_code_id. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberQualityStandardGroupDto {

  private UUID isoCodeId;
  private List<FiberQualityStandardDto> profiles;
}
