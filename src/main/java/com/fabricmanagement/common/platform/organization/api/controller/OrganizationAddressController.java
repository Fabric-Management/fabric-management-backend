package com.fabricmanagement.common.platform.organization.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.common.platform.organization.app.OrganizationAddressAssignmentService;
import com.fabricmanagement.common.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.common.platform.organization.dto.OrganizationAddressDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/organizations/{organizationId}/addresses")
@RequiredArgsConstructor
@Slf4j
public class OrganizationAddressController {

  private final OrganizationAddressAssignmentService organizationAddressAssignmentService;
  private final AddressService addressService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<OrganizationAddressDto>>> getOrganizationAddresses(
      @PathVariable UUID organizationId) {
    log.debug("Getting organization addresses: organizationId={}", organizationId);

    List<OrganizationAddressDto> addresses =
        organizationAddressAssignmentService.getOrganizationAddresses(organizationId).stream()
            .map(OrganizationAddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> getPrimaryAddress(
      @PathVariable UUID organizationId) {
    log.debug("Getting primary address: organizationId={}", organizationId);

    OrganizationAddress primaryAddress =
        organizationAddressAssignmentService
            .getPrimaryAddress(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(OrganizationAddressDto.from(primaryAddress)));
  }

  @GetMapping("/headquarters")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> getHeadquarters(
      @PathVariable UUID organizationId) {
    log.debug("Getting headquarters: organizationId={}", organizationId);

    OrganizationAddress hqAddress =
        organizationAddressAssignmentService
            .getHeadquarters(organizationId)
            .orElseThrow(() -> new IllegalArgumentException("No headquarters found"));

    return ResponseEntity.ok(ApiResponse.success(OrganizationAddressDto.from(hqAddress)));
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

    OrganizationAddress organizationAddress =
        organizationAddressAssignmentService.assignAddress(
            organizationId,
            request.getAddressId(),
            request.getIsPrimary(),
            request.getIsHeadquarters());

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationAddressDto.from(organizationAddress), "Address assigned successfully"));
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

    Address address =
        addressService.createAddress(
            createRequest.getStreetAddress(),
            createRequest.getCity(),
            createRequest.getState(),
            createRequest.getPostalCode(),
            createRequest.getCountry(),
            createRequest.getAddressType(),
            createRequest.getLabel());

    OrganizationAddress organizationAddress =
        organizationAddressAssignmentService.assignAddress(
            organizationId, address.getId(), isPrimary, isHeadquarters);

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationAddressDto.from(organizationAddress),
            "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> setAsPrimary(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info("Setting primary address: organizationId={}, addressId={}", organizationId, addressId);

    OrganizationAddress organizationAddress =
        organizationAddressAssignmentService.setAsPrimary(organizationId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationAddressDto.from(organizationAddress), "Primary address set successfully"));
  }

  @PutMapping("/{addressId}/headquarters")
  public ResponseEntity<ApiResponse<OrganizationAddressDto>> setAsHeadquarters(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info("Setting headquarters: organizationId={}, addressId={}", organizationId, addressId);

    OrganizationAddress organizationAddress =
        organizationAddressAssignmentService.setAsHeadquarters(organizationId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            OrganizationAddressDto.from(organizationAddress), "Headquarters set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID organizationId, @PathVariable UUID addressId) {
    log.info(
        "Removing address from organization: organizationId={}, addressId={}",
        organizationId,
        addressId);

    organizationAddressAssignmentService.removeAddress(organizationId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }
}
