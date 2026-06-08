package com.fabricmanagement.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.organization.api.facade.OrganizationAddressFacade;
import com.fabricmanagement.platform.organization.dto.AddressDeletionImpactDto;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/common/organizations/{organizationId}/addresses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Organization Address", description = "Organization Address operations")
public class OrganizationAddressController {

  private final OrganizationAddressFacade facade;

  @GetMapping
  public ResponseEntity<ApiResponse<List<OrganizationAddressDto>>> getOrganizationAddresses(
      @PathVariable UUID organizationId) {
    log.debug("Getting organization addresses: organizationId={}", organizationId);
    return ResponseEntity.ok(ApiResponse.success(facade.getOrganizationAddresses(organizationId)));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> getPrimaryAddress(
      @PathVariable UUID organizationId) {
    log.debug("Getting primary address: organizationId={}", organizationId);
    return ResponseEntity.ok(ApiResponse.success(facade.getPrimaryAddress(organizationId)));
  }

  @GetMapping("/headquarters")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> getHeadquarters(
      @PathVariable UUID organizationId) {
    log.debug("Getting headquarters: organizationId={}", organizationId);
    return ResponseEntity.ok(ApiResponse.success(facade.getHeadquarters(organizationId)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> assignAddress(
      @PathVariable UUID organizationId, @Valid @RequestBody AssignAddressRequest request) {
    log.info(
        "Assigning address to organization: organizationId={}, addressId={}, isPrimary={}, isHQ={}",
        organizationId,
        request.getAddressId(),
        request.getIsPrimary(),
        request.getIsHeadquarters());
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.assignAddress(organizationId, request), "Address assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> createAndAssignAddress(
      @PathVariable UUID organizationId,
      @Valid @RequestBody CreateAddressRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isPrimary,
      @RequestParam(defaultValue = "false") Boolean isHeadquarters) {
    log.info(
        "Creating and assigning address to organization: organizationId={}, type={}",
        organizationId,
        createRequest.getAddressType());
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.createAndAssignAddress(organizationId, createRequest, isPrimary, isHeadquarters),
            "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> setAsPrimary(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info("Setting primary address: organizationId={}, addressId={}", organizationId, addressId);
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.setAsPrimary(organizationId, addressId), "Primary address set successfully"));
  }

  @PutMapping("/{addressId}/headquarters")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> setAsHeadquarters(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info("Setting headquarters: organizationId={}, addressId={}", organizationId, addressId);
    return ResponseEntity.ok(
        ApiResponse.success(
            facade.setAsHeadquarters(organizationId, addressId), "Headquarters set successfully"));
  }

  @GetMapping("/{addressId}/deletion-impact")
  public ResponseEntity<ApiResponse<AddressDeletionImpactDto>> getAddressDeletionImpact(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.debug(
        "Getting deletion impact: organizationId={}, addressId={}", organizationId, addressId);
    return ResponseEntity.ok(
        ApiResponse.success(facade.getAddressDeletionImpact(organizationId, addressId)));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info(
        "Safe-removing address from organization: organizationId={}, addressId={}",
        organizationId,
        addressId);
    facade.safeRemoveAddress(organizationId, addressId);
    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }
}
