package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.dto.AddressDto;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/common/addresses")
@RequiredArgsConstructor
@Slf4j
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddressDto>> createAddress(@Valid @RequestBody CreateAddressRequest request) {
        log.info("Creating address: type={}, city={}", request.getAddressType(), request.getCity());

        Address address = addressService.createAddress(
            request.getStreetAddress(),
            request.getCity(),
            request.getState(),
            request.getPostalCode(),
            request.getCountry(),
            request.getAddressType(),
            request.getLabel()
        );

        return ResponseEntity.ok(ApiResponse.success(AddressDto.from(address), "Address created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AddressDto>> getAddress(@PathVariable UUID id) {
        log.debug("Getting address: id={}", id);

        Address address = addressService.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        return ResponseEntity.ok(ApiResponse.success(AddressDto.from(address)));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<AddressDto>>> getAddressesByType(@PathVariable String type) {
        log.debug("Getting addresses by type: type={}", type);

        AddressType addressType = AddressType.valueOf(type.toUpperCase());
        List<AddressDto> addresses = addressService.findByType(addressType)
            .stream()
            .map(AddressDto::from)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(addresses));
    }

    @PutMapping("/{id}/primary")
    public ResponseEntity<ApiResponse<AddressDto>> setAsPrimary(@PathVariable UUID id) {
        log.info("Setting address as primary: id={}", id);

        Address address = addressService.setAsPrimary(id);

        return ResponseEntity.ok(ApiResponse.success(AddressDto.from(address), "Address set as primary"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id) {
        log.info("Deleting address: id={}", id);

        addressService.deleteAddress(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Address deleted successfully"));
    }
}

