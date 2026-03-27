package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.app.AddressService;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.dto.UpdateAddressRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/addresses")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

  private final AddressService addressService;

  @PostMapping
  public ResponseEntity<ApiResponse<AddressDto>> createAddress(
      @Valid @RequestBody CreateAddressRequest request) {
    log.info("Creating address: type={}, city={}", request.getAddressType(), request.getCity());

    Address address = addressService.createAddress(request);

    return ResponseEntity.ok(
        ApiResponse.success(AddressDto.from(address), "Address created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AddressDto>> getAddress(@PathVariable UUID id) {
    log.debug("Getting address: id={}", id);

    Address address =
        addressService
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

    return ResponseEntity.ok(ApiResponse.success(AddressDto.from(address)));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<AddressDto>>> getAddressesByType(
      @PathVariable String type) {
    log.debug("Getting addresses by type: type={}", type);

    AddressType addressType = AddressType.valueOf(type.toUpperCase());
    List<AddressDto> addresses =
        addressService.findByType(addressType).stream()
            .map(AddressDto::from)
            .collect(Collectors.toList());

    return ResponseEntity.ok(ApiResponse.success(addresses));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AddressDto>> updateAddress(
      @PathVariable UUID id, @RequestBody UpdateAddressRequest request) {
    log.info("Updating address: id={}", id);

    Address address =
        addressService.updateAddress(
            id,
            request.getStreetAddress(),
            request.getAddressLine2(),
            request.getCity(),
            request.getState(),
            request.getDistrict(),
            request.getPostalCode(),
            request.getCountry(),
            request.getCountryCode(),
            request.getAddressType(),
            request.getLabel(),
            request.getContactPerson(),
            request.getContactPhone(),
            request.getContactEmail());

    return ResponseEntity.ok(
        ApiResponse.success(AddressDto.from(address), "Address updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id) {
    log.info("Deleting address: id={}", id);

    addressService.deleteAddress(id);

    return ResponseEntity.ok(ApiResponse.success(null, "Address deleted successfully"));
  }
}
