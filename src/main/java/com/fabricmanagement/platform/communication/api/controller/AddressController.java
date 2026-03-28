package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.platform.communication.api.facade.AddressFacade;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressDto;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.dto.UpdateAddressRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/common/addresses")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

  private final AddressFacade facade;

  @PostMapping
  public ResponseEntity<ApiResponse<AddressDto>> createAddress(
      @Valid @RequestBody CreateAddressRequest request) {
    log.info("Creating address: type={}, city={}", request.getAddressType(), request.getCity());
    return ResponseEntity.ok(
        ApiResponse.success(facade.createAddress(request), "Address created successfully"));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<AddressDto>> getAddress(@PathVariable UUID id) {
    log.debug("Getting address: id={}", id);
    return ResponseEntity.ok(ApiResponse.success(facade.getAddress(id)));
  }

  @GetMapping("/type/{type}")
  public ResponseEntity<ApiResponse<List<AddressDto>>> getAddressesByType(
      @PathVariable String type) {
    log.debug("Getting addresses by type: type={}", type);
    AddressType addressType = AddressType.valueOf(type.toUpperCase());
    return ResponseEntity.ok(ApiResponse.success(facade.getAddressesByType(addressType)));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<AddressDto>> updateAddress(
      @PathVariable UUID id, @RequestBody UpdateAddressRequest request) {
    log.info("Updating address: id={}", id);
    return ResponseEntity.ok(
        ApiResponse.success(facade.updateAddress(id, request), "Address updated successfully"));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id) {
    log.info("Deleting address: id={}", id);
    facade.deleteAddress(id);
    return ResponseEntity.ok(ApiResponse.success(null, "Address deleted successfully"));
  }
}
