package com.fabricmanagement.production.masterdata.fiber.dto;

import com.fabricmanagement.production.masterdata.material.dto.MaterialAttributeDto;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Catalog summary DTO: categories, ISO codes, attributes, certifications, and fibers in one
 * response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiberCatalogSummaryDto {

  private List<FiberCategoryDto> categories;
  private List<FiberIsoCodeDto> isoCodes;
  private List<MaterialAttributeDto> attributes;
  private List<FiberCertificationDto> certifications;
  private List<FiberDto> fibers;
}
