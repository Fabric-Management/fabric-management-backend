package com.fabricmanagement.common.platform.communication.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.communication.app.AddressValidationService;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.dto.AddressDto;
import com.fabricmanagement.common.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.common.platform.communication.dto.AutocompleteRequest;
import com.fabricmanagement.common.platform.communication.dto.AutocompleteResponse;
import com.fabricmanagement.common.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.common.platform.communication.infra.client.GoogleMapsClient;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Address Validation Controller - REST endpoints for address autocomplete and validation.
 *
 * <p>Provides endpoints for:
 * <ul>
 *   <li>Address autocomplete (Google Places API)</li>
 *   <li>Address validation (Google Geocoding API)</li>
 *   <li>Validate and create address</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/common/addresses/validation")
@RequiredArgsConstructor
@Slf4j
public class AddressValidationController {

    private final GoogleMapsClient googleMapsClient;
    private final AddressValidationService addressValidationService;

    /**
     * Autocomplete endpoint - Get address suggestions as user types.
     *
     * <p>Uses Google Places Autocomplete API (New) to provide real-time suggestions.</p>
     */
    @PostMapping("/autocomplete")
    public ResponseEntity<ApiResponse<AutocompleteResponse>> autocomplete(
            @Valid @RequestBody AutocompleteRequest request) {
        log.debug("Autocomplete request: input={}, country={}", request.getInput(), request.getCountry());

        AutocompleteResponse response = googleMapsClient.autocomplete(
            request.getInput(),
            request.getCountry()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Validate address endpoint - Validate address without persisting.
     *
     * <p>Returns normalized address data without saving to database.</p>
     */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<AddressValidationResponse>> validateAddress(
            @Valid @RequestBody ValidateAddressRequest request) {
        log.info("Validation request: placeId={}", request.getPlaceId());

        AddressValidationResponse response = addressValidationService.validateAddress(request);

        if (response.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
            return ResponseEntity.badRequest()
                .body(ApiResponse.error("VALIDATION_FAILED", response.getErrorMessage()));
        }

        return ResponseEntity.ok(ApiResponse.success(response, "Address validated successfully"));
    }

    /**
     * Validate and create endpoint - Validate address and persist to database.
     *
     * <p>Validates address first, then creates Address entity with normalized data.</p>
     * <p>Only VERIFIED or PARTIAL addresses are persisted.</p>
     */
    @PostMapping("/validate-and-create")
    public ResponseEntity<ApiResponse<AddressDto>> validateAndCreate(
            @Valid @RequestBody ValidateAddressRequest request) {
        log.info("Validate and create request: placeId={}, addressType={}", 
            request.getPlaceId(), request.getAddressType());

        Address address = addressValidationService.validateAndCreateAddress(request);

        return ResponseEntity.ok(ApiResponse.success(
            AddressDto.from(address),
            "Address validated and created successfully"
        ));
    }

    /**
     * Revalidate existing address endpoint.
     *
     * <p>Revalidates an existing address by its placeId and updates with latest data.</p>
     */
    @PostMapping("/{addressId}/revalidate")
    public ResponseEntity<ApiResponse<AddressDto>> revalidateAddress(@PathVariable UUID addressId) {
        log.info("Revalidate request: addressId={}", addressId);

        Address address = addressValidationService.revalidateAddress(addressId);

        return ResponseEntity.ok(ApiResponse.success(
            AddressDto.from(address),
            "Address revalidated successfully"
        ));
    }
}

