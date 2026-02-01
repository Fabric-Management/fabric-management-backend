package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.domain.CompanyCategory;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyTypeDto;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Company type reference data — public endpoints for dropdowns and signup forms.
 *
 * <p>Base path: /api/common/company-types. No authentication required.
 */
@RestController
@RequestMapping("/api/common/company-types")
@RequiredArgsConstructor
@Slf4j
public class CompanyTypeController {

  /** Get all company types. */
  @GetMapping
  public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getAllTypes() {
    log.debug("Getting all company types");
    List<CompanyTypeDto> types = Stream.of(CompanyType.values()).map(CompanyTypeDto::from).toList();
    return ResponseEntity.ok(ApiResponse.success(types));
  }

  /** Get tenant company types only (for self-service signup). */
  @GetMapping("/tenant")
  public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getTenantTypes() {
    log.debug("Getting tenant company types only");
    List<CompanyTypeDto> tenantTypes =
        CompanyType.getByCategory(CompanyCategory.TENANT).stream()
            .map(CompanyTypeDto::from)
            .toList();
    return ResponseEntity.ok(
        ApiResponse.success(tenantTypes, "Found " + tenantTypes.size() + " tenant company types"));
  }

  /** Get company types by category. */
  @GetMapping("/category/{category}")
  public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getByCategory(
      @PathVariable String category) {
    log.debug("Getting company types by category: {}", category);
    CompanyCategory companyCategory = CompanyCategory.valueOf(category.toUpperCase());
    List<CompanyTypeDto> types =
        CompanyType.getByCategory(companyCategory).stream().map(CompanyTypeDto::from).toList();
    return ResponseEntity.ok(
        ApiResponse.success(
            types, "Found " + types.size() + " company types in category " + category));
  }
}
