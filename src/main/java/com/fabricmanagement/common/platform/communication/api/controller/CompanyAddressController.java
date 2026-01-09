package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressContactService;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.app.CompanyAddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressContact;
import com.fabricmanagement.common.platform.communication.domain.CompanyAddress;
import com.fabricmanagement.common.platform.communication.dto.AddressContactDto;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressContactRequest;
import com.fabricmanagement.common.platform.communication.dto.AssignAddressRequest;
import com.fabricmanagement.common.platform.communication.dto.CompanyAddressDto;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
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

  private final CompanyAddressService companyAddressService;
  private final AddressService addressService;
  private final AddressContactService addressContactService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<CompanyAddressDto>>> getCompanyAddresses(
      @PathVariable UUID companyId) {
    log.debug("Getting company addresses: companyId={}", companyId);

    List<CompanyAddressDto> addresses =
        companyAddressService.getCompanyAddresses(companyId).stream()
            .map(CompanyAddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @GetMapping("/primary")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> getPrimaryAddress(
      @PathVariable UUID companyId) {
    log.debug("Getting primary address: companyId={}", companyId);

    CompanyAddress primaryAddress =
        companyAddressService
            .getPrimaryAddress(companyId)
            .orElseThrow(() -> new IllegalArgumentException("No primary address found"));

    return ResponseEntity.ok(ApiResponse.success(CompanyAddressDto.from(primaryAddress)));
  }

  @GetMapping("/headquarters")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> getHeadquarters(
      @PathVariable UUID companyId) {
    log.debug("Getting headquarters: companyId={}", companyId);

    CompanyAddress hqAddress =
        companyAddressService
            .getHeadquarters(companyId)
            .orElseThrow(() -> new IllegalArgumentException("No headquarters address found"));

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
        companyAddressService.assignAddress(
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

    // Create address first
    Address address =
        addressService.createAddress(
            createRequest.getStreetAddress(),
            createRequest.getCity(),
            createRequest.getState(),
            createRequest.getPostalCode(),
            createRequest.getCountry(),
            createRequest.getAddressType(),
            createRequest.getLabel());

    // Assign to company
    CompanyAddress companyAddress =
        companyAddressService.assignAddress(companyId, address.getId(), isPrimary, isHeadquarters);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Address created and assigned successfully"));
  }

  @PutMapping("/{addressId}/primary")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> setAsPrimary(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Setting primary address: companyId={}, addressId={}", companyId, addressId);

    CompanyAddress companyAddress = companyAddressService.setAsPrimary(companyId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Primary address set successfully"));
  }

  @PutMapping("/{addressId}/headquarters")
  public ResponseEntity<ApiResponse<CompanyAddressDto>> setAsHeadquarters(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Setting headquarters: companyId={}, addressId={}", companyId, addressId);

    CompanyAddress companyAddress = companyAddressService.setAsHeadquarters(companyId, addressId);

    return ResponseEntity.ok(
        ApiResponse.success(
            CompanyAddressDto.from(companyAddress), "Headquarters set successfully"));
  }

  @DeleteMapping("/{addressId}")
  public ResponseEntity<ApiResponse<Void>> removeAddress(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.info("Removing address from company: companyId={}, addressId={}", companyId, addressId);

    companyAddressService.removeAddress(companyId, addressId);

    return ResponseEntity.ok(ApiResponse.success(null, "Address removed successfully"));
  }

  // =========================================================================
  // ADDRESS CONTACT ENDPOINTS
  // =========================================================================

  @GetMapping("/{addressId}/contacts")
  public ResponseEntity<ApiResponse<List<AddressContactDto>>> getAddressContacts(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.debug(
        "Getting contacts for company address: companyId={}, addressId={}", companyId, addressId);

    List<AddressContactDto> contacts =
        companyAddressService.getAddressContacts(companyId, addressId).stream()
            .map(AddressContactDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(contacts));
  }

  @GetMapping("/{addressId}/contacts/primary")
  public ResponseEntity<ApiResponse<AddressContactDto>> getPrimaryContact(
      @PathVariable UUID companyId, @PathVariable UUID addressId) {
    log.debug(
        "Getting primary contact for company address: companyId={}, addressId={}",
        companyId,
        addressId);

    AddressContact primaryContact =
        companyAddressService
            .getPrimaryContact(companyId, addressId)
            .orElseThrow(
                () -> new IllegalArgumentException("No primary contact found for this address"));

    return ResponseEntity.ok(ApiResponse.success(AddressContactDto.from(primaryContact)));
  }

  @PostMapping("/{addressId}/contacts")
  public ResponseEntity<ApiResponse<AddressContactDto>> assignContact(
      @PathVariable UUID companyId,
      @PathVariable UUID addressId,
      @Valid @RequestBody AssignAddressContactRequest request) {
    log.info(
        "Assigning contact to company address: companyId={}, addressId={}, contactId={}, isPrimary={}",
        companyId,
        addressId,
        request.getContactId(),
        request.getIsPrimary());

    // Verify address belongs to company
    companyAddressService.getCompanyAddresses(companyId).stream()
        .filter(ca -> ca.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this company"));

    AddressContact addressContact =
        addressContactService.assignContact(
            addressId, request.getContactId(), request.getIsPrimary(), request.getLabel());

    return ResponseEntity.ok(
        ApiResponse.success(
            AddressContactDto.from(addressContact), "Contact assigned to address successfully"));
  }

  @PutMapping("/{addressId}/contacts/{contactId}/primary")
  public ResponseEntity<ApiResponse<AddressContactDto>> setContactAsPrimary(
      @PathVariable UUID companyId, @PathVariable UUID addressId, @PathVariable UUID contactId) {
    log.info(
        "Setting primary contact: companyId={}, addressId={}, contactId={}",
        companyId,
        addressId,
        contactId);

    // Verify address belongs to company
    companyAddressService.getCompanyAddresses(companyId).stream()
        .filter(ca -> ca.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this company"));

    AddressContact addressContact = addressContactService.setAsPrimary(addressId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(
            AddressContactDto.from(addressContact), "Primary contact set successfully"));
  }

  @DeleteMapping("/{addressId}/contacts/{contactId}")
  public ResponseEntity<ApiResponse<Void>> removeContact(
      @PathVariable UUID companyId, @PathVariable UUID addressId, @PathVariable UUID contactId) {
    log.info(
        "Removing contact from company address: companyId={}, addressId={}, contactId={}",
        companyId,
        addressId,
        contactId);

    // Verify address belongs to company
    companyAddressService.getCompanyAddresses(companyId).stream()
        .filter(ca -> ca.getAddressId().equals(addressId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Address is not assigned to this company"));

    addressContactService.removeContact(addressId, contactId);

    return ResponseEntity.ok(
        ApiResponse.success(null, "Contact removed from address successfully"));
  }
}
