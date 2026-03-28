package com.fabricmanagement.platform.organization.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Option DTO for organization/company type dropdowns.
 *
 * <p>Returned by GET /api/common/company-types and /api/common/company-types/tenant. Frontend
 * expects: value, label, description?, category?, isTenant?, suggestedOS?.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyTypeOptionDto {

  /** Enum name (e.g. SPINNER, VERTICAL_MILL). */
  private String value;

  /** Display name (e.g. Spinner, Vertical Mill). */
  private String label;

  private String description;

  /** Category for grouping (e.g. TENANT). */
  private String category;

  /** Whether this type can be used for tenant signup. */
  private Boolean isTenant;

  /** Recommended OS codes for this type. */
  private List<String> suggestedOS;
}
