package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.common.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.common.platform.communication.infra.client.GoogleMapsClient;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Address Validation Service - Business logic for address validation and normalization.
 *
 * <p>Orchestrates Google Maps Platform integration for address validation and standardization.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Validate addresses using Google Geocoding API</li>
 *   <li>Normalize address data from Google response</li>
 *   <li>Persist verified addresses</li>
 *   <li>Handle validation errors gracefully</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressValidationService {

    private final GoogleMapsClient googleMapsClient;
    private final AddressRepository addressRepository;

    /**
     * Validate address by placeId (recommended method).
     */
    @Transactional
    public AddressValidationResponse validateAddress(ValidateAddressRequest request) {
        log.info("Validating address: placeId={}, addressType={}", request.getPlaceId(), request.getAddressType());

        AddressValidationResponse validationResponse;

        if (request.getPlaceId() != null && !request.getPlaceId().isBlank()) {
            // Recommended: validate by placeId
            validationResponse = googleMapsClient.validateByPlaceId(request.getPlaceId());
        } else if (request.getAddress() != null && !request.getAddress().isBlank()) {
            // Fallback: validate by address string
            validationResponse = googleMapsClient.validateByAddress(request.getAddress());
        } else {
            throw new IllegalArgumentException("Either placeId or address must be provided");
        }

        // Only persist if validation was successful (VERIFIED or PARTIAL)
        if (validationResponse.getVerificationStatus() != AddressValidationResponse.VerificationStatus.FAILED) {
            log.debug("Address validation successful, status={}", validationResponse.getVerificationStatus());
        } else {
            log.warn("Address validation failed: {}", validationResponse.getErrorMessage());
        }

        return validationResponse;
    }

    /**
     * Validate and create address entity.
     *
     * <p>Validates address first, then creates Address entity with normalized data.</p>
     */
    @Transactional
    public Address validateAndCreateAddress(ValidateAddressRequest request) {
        log.info("Validating and creating address: placeId={}", request.getPlaceId());

        AddressValidationResponse validation = validateAddress(request);

        if (validation.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
            throw new IllegalArgumentException("Address validation failed: " + validation.getErrorMessage());
        }

        // Parse address type
        AddressType addressType = request.getAddressType() != null
            ? AddressType.valueOf(request.getAddressType().toUpperCase())
            : AddressType.HOME;

        // Create normalized address entity
        Address address = Address.builder()
            .streetAddress(validation.getStreetAddress())
            .city(validation.getCity())
            .state(validation.getState())
            .district(validation.getDistrict())
            .postalCode(validation.getPostalCode())
            .country(validation.getCountry())
            .countryCode(validation.getCountryCode())
            .addressType(addressType)
            .label(request.getLabel())
            .formattedAddress(validation.getFormattedAddress())
            .placeId(validation.getPlaceId())
            .latitude(validation.getLatitude())
            .longitude(validation.getLongitude())
            .isPrimary(false)
            .build();

        Address saved = addressRepository.save(address);
        log.info("Address created and saved: addressId={}, placeId={}", saved.getId(), saved.getPlaceId());

        return saved;
    }

    /**
     * Validate existing address by placeId and update if needed.
     */
    @Transactional
    public Address revalidateAddress(UUID addressId) {
        log.info("Revalidating address: addressId={}", addressId);

        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (address.getPlaceId() == null || address.getPlaceId().isBlank()) {
            throw new IllegalArgumentException("Address does not have placeId for revalidation");
        }

        AddressValidationResponse validation = googleMapsClient.validateByPlaceId(address.getPlaceId());

        if (validation.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
            throw new IllegalArgumentException("Address revalidation failed: " + validation.getErrorMessage());
        }

        // Update address with latest normalized data
        address.setStreetAddress(validation.getStreetAddress());
        address.setCity(validation.getCity());
        address.setState(validation.getState());
        address.setDistrict(validation.getDistrict());
        address.setPostalCode(validation.getPostalCode());
        address.setCountry(validation.getCountry());
        address.setCountryCode(validation.getCountryCode());
        address.setFormattedAddress(validation.getFormattedAddress());
        address.setLatitude(validation.getLatitude());
        address.setLongitude(validation.getLongitude());

        Address saved = addressRepository.save(address);
        log.info("Address revalidated and updated: addressId={}", saved.getId());

        return saved;
    }
}

