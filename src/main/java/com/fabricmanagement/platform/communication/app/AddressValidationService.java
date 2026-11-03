package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.platform.communication.dto.ValidateAddressRequest;
import com.fabricmanagement.platform.communication.infra.client.GoogleMapsClient;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Address Validation Service - Business logic for address validation and normalization.
 *
 * <p>Orchestrates Google Maps Platform integration for address validation and standardization.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Validate addresses using Google Geocoding API
 *   <li>Normalize address data from Google response
 *   <li>Persist addresses through the common write policy
 *   <li>Handle validation errors gracefully
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressValidationService {

  private final GoogleMapsClient googleMapsClient;
  private final AddressService addressService;

  /** Validate address by placeId (recommended method). */
  @Transactional
  public AddressValidationResponse validateAddress(ValidateAddressRequest request) {
    log.info(
        "Validating address: placeId={}, addressType={}",
        request.getPlaceId(),
        request.getAddressType());

    AddressValidationResponse validationResponse;

    if (request.getPlaceId() != null && !request.getPlaceId().isBlank()) {
      // Recommended: validate by placeId
      validationResponse =
          request.getSessionToken() == null
              ? googleMapsClient.validateByPlaceId(request.getPlaceId(), request.getOriginalInput())
              : googleMapsClient.validateByPlaceId(
                  request.getPlaceId(), request.getOriginalInput(), request.getSessionToken());
    } else if (request.getAddress() != null && !request.getAddress().isBlank()) {
      // Fallback: validate by address string
      validationResponse = googleMapsClient.validateByAddress(request.getAddress());
    } else {
      throw new PlatformDomainException(
          "Either placeId or address must be provided", "ADDRESS_LOOKUP_INPUT_REQUIRED", 400);
    }

    if (validationResponse == null || validationResponse.getVerificationStatus() == null) {
      throw new PlatformDomainException(
          "Address lookup returned no result", "ADDRESS_LOOKUP_FAILED", 400);
    }
    // Lookup completeness is separate from delivery verification.
    if (validationResponse.getVerificationStatus()
        != AddressValidationResponse.VerificationStatus.FAILED) {
      log.debug(
          "Address validation successful, status={}", validationResponse.getVerificationStatus());
    } else {
      log.warn("Address validation failed: {}", validationResponse.getErrorMessage());
    }

    return validationResponse;
  }

  /**
   * Validate and create address entity.
   *
   * <p>Validates address first, then creates Address entity with normalized data.
   */
  @Transactional
  public Address validateAndCreateAddress(ValidateAddressRequest request) {
    log.info("Validating and creating address: placeId={}", request.getPlaceId());

    AddressValidationResponse validation = validateAddress(request);

    if (validation.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
      throw new PlatformDomainException("Address lookup failed", "ADDRESS_LOOKUP_FAILED", 400);
    }

    AddressType addressType;
    try {
      addressType =
          request.getAddressType() != null
              ? AddressType.valueOf(request.getAddressType().trim().toUpperCase(Locale.ROOT))
              : AddressType.HOME;
    } catch (IllegalArgumentException invalidType) {
      throw new PlatformDomainException("Unknown address type", "ADDRESS_TYPE_INVALID", 400);
    }
    return addressService.createFromLookup(validation, addressType, request.getLabel());
  }

  /** Validate existing address by placeId and update if needed. */
  @Transactional
  public Address revalidateAddress(UUID addressId) {
    log.info("Revalidating address: addressId={}", addressId);

    Address address = addressService.requireActiveAddress(addressId);
    if (address.getPlaceId() == null || address.getPlaceId().isBlank()) {
      throw new PlatformDomainException(
          "Address has no lookup reference", "ADDRESS_LOOKUP_REFERENCE_REQUIRED", 400);
    }
    AddressValidationResponse validation =
        googleMapsClient.validateByPlaceId(address.getPlaceId(), null);
    return addressService.replaceFromLookup(addressId, validation);
  }
}
