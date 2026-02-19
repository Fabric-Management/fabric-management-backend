package com.fabricmanagement.common.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.organization.domain.OrganizationType;
import com.fabricmanagement.common.platform.organization.dto.CompanyTypeOptionDto;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API for organization/company type options.
 *
 * <p>Serves dropdown options for signup and onboarding. Path is /api/common/company-types for
 * backward compatibility with frontend (company → organization rename).
 */
@RestController
@RequestMapping("/api/common/company-types")
@Slf4j
public class CompanyTypeController {

  /**
   * All organization types (including EXTERNAL_PARTNER).
   *
   * <p>GET /api/common/company-types
   */
  @GetMapping
  public ResponseEntity<ApiResponse<List<CompanyTypeOptionDto>>> getAllTypes() {
    log.debug("Getting all company/organization types");
    List<CompanyTypeOptionDto> types = mapTypes(Arrays.asList(OrganizationType.values()));
    return ResponseEntity.ok(ApiResponse.success(types));
  }

  /**
   * Tenant-eligible organization types only (excludes EXTERNAL_PARTNER).
   *
   * <p>Used by signup and onboarding. GET /api/common/company-types/tenant
   */
  @GetMapping("/tenant")
  public ResponseEntity<ApiResponse<List<CompanyTypeOptionDto>>> getTenantTypes() {
    log.debug("Getting tenant company/organization types");
    List<OrganizationType> tenantTypes =
        Arrays.stream(OrganizationType.values()).filter(t -> !t.isExternalPartner()).toList();
    List<CompanyTypeOptionDto> types = mapTypes(tenantTypes);
    return ResponseEntity.ok(ApiResponse.success(types));
  }

  private static List<CompanyTypeOptionDto> mapTypes(List<OrganizationType> organizationTypes) {
    return organizationTypes.stream()
        .map(CompanyTypeController::toOption)
        .collect(Collectors.toList());
  }

  private static CompanyTypeOptionDto toOption(OrganizationType type) {
    return CompanyTypeOptionDto.builder()
        .value(type.name())
        .label(enumToLabel(type.name()))
        .description(descriptionFor(type))
        .category("TENANT")
        .isTenant(!type.isExternalPartner())
        .suggestedOS(Arrays.asList(type.getSuggestedOS()))
        .build();
  }

  private static String enumToLabel(String enumName) {
    if (enumName == null || enumName.isBlank()) {
      return enumName;
    }
    return Arrays.stream(enumName.split("_"))
        .map(word -> word.isEmpty() ? word : word.charAt(0) + word.substring(1).toLowerCase())
        .collect(Collectors.joining(" "));
  }

  private static String descriptionFor(OrganizationType type) {
    return switch (type) {
      case SPINNER -> "Yarn producer from fiber";
      case WEAVER -> "Fabric producer (shuttle looms)";
      case KNITTER -> "Fabric producer (knitting machines)";
      case DYER_FINISHER -> "Dyeing and finishing";
      case VERTICAL_MILL -> "Integrated mill (spinning + weaving/knitting + dyeing)";
      case GARMENT_MANUFACTURER -> "Garment/apparel manufacturer";
      case EXTERNAL_PARTNER -> "External trading partner";
    };
  }
}
