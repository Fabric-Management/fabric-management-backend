package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.common.platform.company.app.CompanyAddressAssignmentService;
import com.fabricmanagement.common.platform.company.domain.CompanyAddress;
import com.fabricmanagement.common.platform.company.dto.CompanyAddressDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/companies/{companyId}/addresses")
@RequiredArgsConstructor
@Slf4j
public class CompanyAddressController {

  private final CompanyAddressAssignmentService companyAddressAssignmentService;
  private final AddressService addressService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CompanyAddressDto>>> getCompanyAddresses(
      @PathVariable UUID companyId) {
    log.debug("Getting company addresses: companyId={}", companyId);

    List<CompanyAddressDto> addresses =
        companyAddressAssignmentService.getCompanyAddresses(companyId).stream()
            .map(CompanyAddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> getPrimaryAddress(
      @PathVariable UUID companyId) {
    log.debug("Getting primary address: companyId={}", companyId);

    CompanyAddress primaryAddress =
        companyAddressAssignmentService
            .getPrimaryAddress(companyId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(CompanyAddressDto.from(primaryAddress)));
  }

  @GetMapping("/headquarters")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> getHeadquarters(
      @PathVariable UUID companyId) {
    log.debug("Getting headquarters: companyId={}", companyId);

    CompanyAddress hqAddress =
        companyAddressAssignmentService
            .getHeadquarters(companyId)
            .orElseThrow(() -> new IllegalArgumentException("No headquarters found"));

    return ResponseEntity.ok(ApiResponse.success(CompanyAddressDto.from(hqAddress)));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<CompanyAddressDto>> assignAddress(
      @PathVariable UUID companyId, @Valid @RequestBody AssignAddressRequest request) {
    log.info(
        "Assigning address to company: companyId={}, addressId={}, isPrimary={}, isHQ={}",
        companyId,
        request.getAddressId(),
        request.getIsPrimary(),
        request.getIsHeadquarters());

    CompanyAddress companyAddress =
        companyAddressAssignmentService.assignAddress(
            companyId, request.getAddressId(), request.getIsPrimary(), request.getIsHeadquarters());

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Address assigned successfully"));
  }

  @PostMapping("/create-and-assign")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> createAndAssignAddress(
      @PathVariable UUID companyId,
      @Valid @RequestBody CreateAddressRequest createRequest,
      @RequestParam(defaultValue = "false") Boolean isPrimary,
      @RequestParam(defaultValue = "false") Boolean isHeadquarters) {
    log.info(
        "Creating and assigning address to company: companyId={}, type={}",
        companyId,
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

    CompanyAddress companyAddress =
        companyAddressAssignmentService.assignAddress(
            companyId, address.getId(), isPrimary, isHeadquarters);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> setAsPrimary(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Setting primary address: companyId={}, addressId={}", companyId, addressId);

    CompanyAddress companyAddress =
        companyAddressAssignmentService.setAsPrimary(companyId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Primary address set successfully"));
  }

  @PutMapping("/{addressId}/headquarters")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> setAsHeadquarters(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Setting headquarters: companyId={}, addressId={}", companyId, addressId);

    CompanyAddress companyAddress =
        companyAddressAssignmentService.setAsHeadquarters(companyId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Headquarters set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Removing address from company: companyId={}, addressId={}", companyId, addressId);

    companyAddressAssignmentService.removeAddress(companyId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }
}
