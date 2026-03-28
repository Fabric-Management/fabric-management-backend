package com.fabricmanagement.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.organization.app.OrganizationCertificationService;
import com.fabricmanagement.platform.organization.dto.AddOrganizationCertificationRequest;
import com.fabricmanagement.platform.organization.dto.OrganizationCertificationDto;
import com.fabricmanagement.platform.organization.dto.UpdateOrganizationCertificationRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/organizations/{organizationId}/certifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Organization Certifications", description = "Manage certifications for organizations")
public class OrganizationCertificationController {

  private final OrganizationCertificationService certificationService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "List organization certifications",
      description = "Lists all active certifications for an organization")
  public ResponseEntity<ApiResponse<List<OrganizationCertificationDto>>> list(
      @Parameter(description = "Organization ID") @PathVariable UUID organizationId) {
    List<OrganizationCertificationDto> list =
        certificationService.findByOrganizationId(organizationId);
    return ResponseEntity.ok(ApiResponse.success(list, "Found " + list.size() + " certifications"));
  }

  @GetMapping("/{certificationId}")
  @PreAuthorize("isAuthenticated()")
  @Operation(
      summary = "Get organization certification",
      description = "Gets a specific certification for an organization")
  public ResponseEntity<ApiResponse<OrganizationCertificationDto>> get(
      @Parameter(description = "Organization ID") @PathVariable UUID organizationId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId) {
    OrganizationCertificationDto dto =
        certificationService.findById(organizationId, certificationId);
    return ResponseEntity.ok(ApiResponse.success(dto));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Add certification", description = "Adds a certification to an organization")
  public ResponseEntity<ApiResponse<OrganizationCertificationDto>> add(
      @Parameter(description = "Organization ID") @PathVariable UUID organizationId,
      @Valid @RequestBody AddOrganizationCertificationRequest request) {
    OrganizationCertificationDto created = certificationService.add(organizationId, request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(created, "Certification added successfully"));
  }

  @PutMapping("/{certificationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(
      summary = "Update certification",
      description = "Updates an organization certification")
  public ResponseEntity<ApiResponse<OrganizationCertificationDto>> update(
      @Parameter(description = "Organization ID") @PathVariable UUID organizationId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId,
      @Valid @RequestBody UpdateOrganizationCertificationRequest request) {
    OrganizationCertificationDto updated =
        certificationService.update(organizationId, certificationId, request);
    return ResponseEntity.ok(ApiResponse.success(updated, "Certification updated successfully"));
  }

  @DeleteMapping("/{certificationId}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(
      summary = "Delete certification",
      description = "Soft-deletes an organization certification")
  public ResponseEntity<ApiResponse<Void>> delete(
      @Parameter(description = "Organization ID") @PathVariable UUID organizationId,
      @Parameter(description = "Certification record ID") @PathVariable UUID certificationId) {
    certificationService.delete(organizationId, certificationId);
    return ResponseEntity.ok(ApiResponse.success(null, "Certification deleted successfully"));
  }
}
