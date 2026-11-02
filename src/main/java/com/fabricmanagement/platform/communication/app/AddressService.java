package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Address;
import com.fabricmanagement.platform.communication.domain.AddressContent;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.communication.dto.AddressValidationResponse;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.platform.user.infra.repository.UserWorkLocationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Address Service - Business logic for address management.
 *
 * <p>Handles CRUD operations for Address entities.
 *
 * <p>Key responsibilities:
 *
 * <ul>
 *   <li>Address creation and validation
 *   <li>Primary address designation
 *   <li>Address search by location
 *   <li>Safe deletion with cascade to organization and user work-location junctions
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {
  private final AddressRepository addressRepository;
  private final OrganizationAddressRepository organizationAddressRepository;
  private final UserWorkLocationRepository userWorkLocationRepository;
  private final DeliveryVerificationPolicy verificationPolicy;

  @Transactional(readOnly = true)
  public void requireExclusiveOnboardingAddress(UUID addressId, UUID organizationId) {
    if (addressRepository.countSharedOnboardingAddress(
            TenantContext.requireTenantId(), addressId, organizationId)
        > 0) {
      throw new PlatformDomainException(
          "Headquarters address is shared; edit its ownership before changing it",
          "ONBOARDING_HQ_ADDRESS_SHARED",
          409);
    }
  }

  /** Replace only the onboarding-owned address fields after patch values have been merged. */
  @Transactional
  public Address replaceOnboardingHeadquarters(
      UUID addressId, UUID organizationId, CreateAddressRequest request) {
    Address address =
        findById(addressId)
            .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Headquarters address not found", "ONBOARDING_HQ_ADDRESS_NOT_FOUND", 404));
    requireExclusiveOnboardingAddress(addressId, organizationId);
    address.replaceContent(
        new AddressContent(
            request.getStreetAddress(),
            request.getAddressLine2(),
            request.getCity(),
            request.getState(),
            request.getDistrict(),
            request.getPostalCode(),
            request.getCountry(),
            request.getCountryCode() != null
                ? request.getCountryCode()
                : java.util.Objects.equals(address.getCountry(), request.getCountry())
                    ? address.getCountryCode()
                    : null,
            address.getAddressType(),
            request.getLabel()));
    address.setVerification(verificationPolicy.forCurrentActor());
    return addressRepository.save(address);
  }

  /**
   * Create an address from the shared DTO. Single entry point for all address creation (REST and
   * internal callers such as onboarding). Ensures DRY and consistent handling of label,
   * addressLine2, and countryCode.
   *
   * @param request CreateAddressRequest (from API or built from CompleteOnboardingRequest etc.)
   * @return Persisted Address
   */
  @Transactional
  public Address createAddress(CreateAddressRequest request) {
    String label =
        (request.getLabel() != null && !request.getLabel().isBlank()) ? request.getLabel() : "";
    return createAddress(
        request.getStreetAddress(),
        request.getCity(),
        request.getState(),
        request.getDistrict(),
        request.getPostalCode(),
        request.getCountry(),
        request.getCountryCode(),
        request.getAddressType(),
        label,
        request.getAddressLine2());
  }

  @Transactional
  public Address createAddress(
      String streetAddress,
      String city,
      String state,
      String postalCode,
      String country,
      AddressType addressType,
      String label) {
    return createAddress(
        streetAddress, city, state, null, postalCode, country, null, addressType, label);
  }

  @Transactional
  public Address createAddress(
      String streetAddress,
      String city,
      String state,
      String district,
      String postalCode,
      String country,
      String countryCode,
      AddressType addressType,
      String label) {
    return createAddress(
        streetAddress,
        city,
        state,
        district,
        postalCode,
        country,
        countryCode,
        addressType,
        label,
        null);
  }

  /**
   * Create an address with full field set including {@code addressLine2}.
   *
   * @param streetAddress Primary street address (line 1)
   * @param city City
   * @param state State / province
   * @param district District / sub-administrative area
   * @param postalCode Postal code
   * @param country Country
   * @param countryCode ISO 3166-1 alpha-2 country code
   * @param addressType Address type
   * @param label Human-readable label
   * @param addressLine2 Suite, floor, building name etc. (may be null)
   * @return Persisted Address
   */
  @Transactional
  public Address createAddress(
      String streetAddress,
      String city,
      String state,
      String district,
      String postalCode,
      String country,
      String countryCode,
      AddressType addressType,
      String label,
      String addressLine2) {
    UUID tenantId = TenantContext.requireTenantId();
    log.debug("Creating address: tenantId={}, type={}, city={}", tenantId, addressType, city);

    return saveNew(
        new AddressContent(
            streetAddress,
            addressLine2,
            city,
            state,
            district,
            postalCode,
            country,
            countryCode,
            addressType,
            label),
        null);
  }

  @Transactional(readOnly = true)
  public Optional<Address> findById(UUID addressId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding address: tenantId={}, addressId={}", tenantId, addressId);

    return addressRepository.findById(addressId).filter(a -> a.getTenantId().equals(tenantId));
  }

  @Transactional(readOnly = true)
  public List<Address> findByType(AddressType addressType) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding addresses by type: tenantId={}, type={}", tenantId, addressType);

    return addressRepository.findByTenantIdAndAddressType(tenantId, addressType);
  }

  @Transactional(readOnly = true)
  public List<Address> findByCity(String city) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding addresses by city: tenantId={}, city={}", tenantId, city);

    return addressRepository.findByTenantIdAndCity(tenantId, city);
  }

  @Transactional(readOnly = true)
  public List<Address> findByCountry(String country) {
    UUID tenantId = TenantContext.requireTenantId();
    log.trace("Finding addresses by country: tenantId={}, country={}", tenantId, country);

    return addressRepository.findByTenantIdAndCountry(tenantId, country);
  }

  @Transactional
  public Address updateAddress(
      UUID addressId,
      String streetAddress,
      String addressLine2,
      String city,
      String state,
      String district,
      String postalCode,
      String country,
      String countryCode,
      AddressType addressType,
      String label,
      String contactPerson,
      String contactPhone,
      String contactEmail) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Updating address: tenantId={}, addressId={}", tenantId, addressId);

    Address address = requireActiveAddress(addressId);

    // Historical cards can lack postal fields now required for new cards. A contact-only
    // edit must not silently turn into a postal replacement or require invented missing data.
    boolean postalUpdate =
        streetAddress != null
            || addressLine2 != null
            || city != null
            || state != null
            || district != null
            || postalCode != null
            || country != null
            || countryCode != null
            || addressType != null
            || label != null;
    if (postalUpdate) {
      String nextCountryCode = countryCode != null ? countryCode : address.getCountryCode();
      if (country != null && !country.trim().equals(address.getCountry()) && countryCode == null) {
        nextCountryCode = null;
      }
      address.replaceContent(
          new AddressContent(
              streetAddress != null ? streetAddress : address.getStreetAddress(),
              addressLine2 != null ? addressLine2 : address.getAddressLine2(),
              city != null ? city : address.getCity(),
              state != null ? state : address.getState(),
              district != null ? district : address.getDistrict(),
              postalCode != null ? postalCode : address.getPostalCode(),
              country != null ? country : address.getCountry(),
              nextCountryCode,
              addressType != null ? addressType : address.getAddressType(),
              label != null ? label : address.getLabel()));
    }
    if (contactPerson != null) address.setContactPerson(contactPerson);
    if (contactPhone != null) address.setContactPhone(contactPhone);
    if (contactEmail != null) address.setContactEmail(contactEmail);
    // An edit is the editor's word: a verifier's stands verified, anyone else's waits (C5).
    address.setVerification(verificationPolicy.forCurrentActor());

    return addressRepository.save(address);
  }

  /** Google supplies postal content; it never supplies delivery approval. */
  @Transactional
  public Address createFromLookup(
      AddressValidationResponse result, AddressType type, String label) {
    return saveNew(
        lookupContent(result, type, label, result == null ? null : result.getFlatNumber()), result);
  }

  private Address saveNew(AddressContent content, AddressValidationResponse lookup) {
    UUID tenantId = TenantContext.requireTenantId();
    Address address = Address.builder().verification(verificationPolicy.forCurrentActor()).build();
    address.setTenantId(tenantId);
    address.replaceContent(content);
    if (lookup != null) applyLookup(address, lookup);
    return addressRepository.save(address);
  }

  @Transactional
  public Address replaceFromLookup(UUID id, AddressValidationResponse result) {
    Address address = requireActiveAddress(id);
    if (result == null) {
      throw new PlatformDomainException("Address lookup failed", "ADDRESS_LOOKUP_FAILED", 400);
    }
    // A provider refresh must not erase a user's flat, floor or entrance instructions.
    String secondLine =
        address.getAddressLine2() != null ? address.getAddressLine2() : result.getFlatNumber();
    address.replaceContent(
        lookupContent(result, address.getAddressType(), address.getLabel(), secondLine));
    applyLookup(address, result);
    address.setVerification(verificationPolicy.forCurrentActor());
    return addressRepository.save(address);
  }

  @Transactional(readOnly = true)
  public Address requireActiveAddress(UUID id) {
    return findById(id)
        .filter(a -> Boolean.TRUE.equals(a.getIsActive()))
        .orElseThrow(
            () -> new PlatformDomainException("Address not found", "ADDRESS_NOT_FOUND", 404));
  }

  private AddressContent lookupContent(
      AddressValidationResponse result, AddressType type, String label, String secondLine) {
    if (result == null
        || result.getVerificationStatus() == null
        || result.getVerificationStatus() == AddressValidationResponse.VerificationStatus.FAILED) {
      throw new PlatformDomainException("Address lookup failed", "ADDRESS_LOOKUP_FAILED", 400);
    }
    return new AddressContent(
        result.getStreetAddress(),
        secondLine,
        result.getCity(),
        result.getState(),
        result.getDistrict(),
        result.getPostalCode(),
        result.getCountry(),
        result.getCountryCode(),
        type,
        label);
  }

  private void applyLookup(Address address, AddressValidationResponse result) {
    Double latitude = result.getLatitude();
    Double longitude = result.getLongitude();
    if ((latitude == null) != (longitude == null)
        || (latitude != null
            && (!Double.isFinite(latitude)
                || latitude < -90
                || latitude > 90
                || !Double.isFinite(longitude)
                || longitude < -180
                || longitude > 180))) {
      throw new PlatformDomainException(
          "Invalid address coordinates", "ADDRESS_COORDINATES_INVALID", 400);
    }
    address.setPlaceId(result.getPlaceId());
    address.setLatitude(latitude);
    address.setLongitude(longitude);
    address.setFormattedAddress(result.getFormattedAddress());
  }

  /**
   * Confirm an address card for delivery (CONSIGNEE-1 C8). Does nothing when it already is.
   *
   * @return true when the card changed from unverified to verified
   */
  @Transactional
  public boolean verify(UUID addressId, UUID verifier) {
    UUID tenantId = TenantContext.requireTenantId();
    Address address =
        addressRepository
            .findById(addressId)
            .filter(found -> found.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));
    if (address.getVerification().isVerified()) {
      return false;
    }
    address.setVerification(Verification.verifiedBy(verifier, Instant.now()));
    addressRepository.save(address);
    log.info("Address verified for delivery: addressId={}, by={}", addressId, verifier);
    return true;
  }

  /** The verification state of an address card of this tenant; empty when there is no such card. */
  @Transactional(readOnly = true)
  public Optional<DeliveryVerificationStatus> verificationOf(UUID addressId) {
    return findById(addressId).map(Address::getVerificationStatus);
  }

  /**
   * Soft-deletes an address with cascade to related junctions. If this address is linked to an
   * organization, the OrganizationAddress junction is soft-deleted and all UserWorkLocation
   * assignments pointing to it are hard-deleted (users become locationless).
   */
  @Transactional
  public void deleteAddress(UUID addressId) {
    UUID tenantId = TenantContext.requireTenantId();
    log.info("Deleting address: tenantId={}, addressId={}", tenantId, addressId);

    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }

    Optional<OrganizationAddress> orgAddress =
        organizationAddressRepository.findByAddressIdIncludingDeleted(addressId);

    if (orgAddress.isPresent()) {
      OrganizationAddress oa = orgAddress.get();

      long cascadedUsers = userWorkLocationRepository.countByOrgAddressId(oa.getAddressId());
      if (cascadedUsers > 0) {
        log.info(
            "Cascading address deletion: removing {} user work-location assignments",
            cascadedUsers);
        userWorkLocationRepository.deleteAllByOrgAddressId(oa.getAddressId());
      }

      if (Boolean.TRUE.equals(oa.getIsActive())) {
        oa.delete();
        organizationAddressRepository.save(oa);
      }
    }

    address.delete();
    addressRepository.save(address);
  }
}
