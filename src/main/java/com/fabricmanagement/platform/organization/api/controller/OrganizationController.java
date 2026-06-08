package com.fabricmanagement.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.CreateOrganizationRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for Organization management.
 *
 * <p>Handles internal organizational structure (departments, hierarchy). For external partners, use
 * TradingPartnerController.
 */
@RestController
@RequestMapping("/api/v1/common/organizations")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Organization", description = "Organization operations")
public class OrganizationController {

  private final OrganizationService organizationService;

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationDto>> createOrganization(
      @Valid @RequestBody CreateOrganizationRequest request) {
    log.info("Creating organization: {}", request.getName());

    OrganizationDto created = organizationService.createOrganization(request);

    return ResponseEntity.ok(ApiResponse.success(created, "Organization created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<OrganizationDto>> getOrganization(@PathVariable UUID id) {
    log.debug("Getting organization: id={}", id);

    OrganizationDto org =
        organizationService
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + id));

    return ResponseEntity.ok(ApiResponse.success(org));
  }

  @GetMapping
  public ResponseEntity<ApiResponse<List<OrganizationDto>>> getAllOrganizations() {
    log.debug("Getting all organizations");

    List<OrganizationDto> organizations = organizationService.getAllActive();

    return ResponseEntity.ok(ApiResponse.success(organizations));
  }

  @GetMapping("/root")
  public ResponseEntity<ApiResponse<OrganizationDto>> getRootOrganization() {
    log.debug("Getting root organization");

    OrganizationDto root =
        organizationService
            .getRootOrganization()
            .orElseThrow(() -> new NotFoundException("Root organization not found"));

    return ResponseEntity.ok(ApiResponse.success(root));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<OrganizationDto>>> getOrganizationsByType(
      @PathVariable String type) {
    log.debug("Getting organizations by type: {}", type);
    OrganizationType orgType = OrganizationType.valueOf(type.toUpperCase());
    List<OrganizationDto> organizations = organizationService.getByType(orgType);
    return ResponseEntity.ok(ApiResponse.success(organizations));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<OrganizationDto>> updateOrganization(
      @PathVariable UUID id,
      @RequestParam String name,
      @RequestParam(required = false) String taxId,
      @RequestParam(required = false) String legalName) {
    log.info("Updating organization: id={}", id);

    OrganizationDto updated = organizationService.updateOrganization(id, name, taxId, legalName);

    return ResponseEntity.ok(ApiResponse.success(updated, "Organization updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deactivateOrganization(@PathVariable UUID id) {
    log.info("Deactivating organization: id={}", id);
    organizationService.deactivateOrganization(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Organization deactivated successfully"));
  }

  // ========================================
  // HIERARCHY ENDPOINTS
  // ========================================

  @GetMapping("/{id}/children")
  public ResponseEntity<ApiResponse<List<OrganizationDto>>> getChildren(@PathVariable UUID id) {
    log.debug("Getting children of organization: id={}", id);
    List<OrganizationDto> children = organizationService.getChildren(id);
    return ResponseEntity.ok(ApiResponse.success(children));
  }
}
