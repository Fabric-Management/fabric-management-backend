package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.common.platform.organization.domain.OrganizationAddress;
import com.fabricmanagement.common.platform.organization.infra.repository.OrganizationAddressRepository;
import com.fabricmanagement.common.platform.user.infra.repository.UserWorkLocationRepository;
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.debug("Creating address: tenantId={}, type={}, city={}", tenantId, addressType, city);

    Address address =
        Address.builder()
            .streetAddress(streetAddress)
            .city(city)
            .state(state)
            .district(district)
            .postalCode(postalCode)
            .country(country)
            .countryCode(countryCode)
            .addressType(addressType)
            .label(label)
            .addressLine2(addressLine2)
            .build();

    return addressRepository.save(address);
  }

  @Transactional(readOnly = true)
  public Optional<Address> findById(UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Finding address: tenantId={}, addressId={}", tenantId, addressId);

    return addressRepository.findById(addressId).filter(a -> a.getTenantId().equals(tenantId));
  }

  @Transactional(readOnly = true)
  public List<Address> findByType(AddressType addressType) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Finding addresses by type: tenantId={}, type={}", tenantId, addressType);

    return addressRepository.findByTenantIdAndAddressType(tenantId, addressType);
  }

  @Transactional(readOnly = true)
  public List<Address> findByCity(String city) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.trace("Finding addresses by city: tenantId={}, city={}", tenantId, city);

    return addressRepository.findByTenantIdAndCity(tenantId, city);
  }

  @Transactional(readOnly = true)
  public List<Address> findByCountry(String country) {
    UUID tenantId = TenantContext.getCurrentTenantId();
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
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Updating address: tenantId={}, addressId={}", tenantId, addressId);

    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }

    if (streetAddress != null) address.setStreetAddress(streetAddress);
    if (addressLine2 != null) address.setAddressLine2(addressLine2);
    if (city != null) address.setCity(city);
    if (state != null) address.setState(state);
    if (district != null) address.setDistrict(district);
    if (postalCode != null) address.setPostalCode(postalCode);
    if (country != null) address.setCountry(country);
    if (countryCode != null) address.setCountryCode(countryCode);
    if (addressType != null) address.setAddressType(addressType);
    if (label != null) address.setLabel(label);
    if (contactPerson != null) address.setContactPerson(contactPerson);
    if (contactPhone != null) address.setContactPhone(contactPhone);
    if (contactEmail != null) address.setContactEmail(contactEmail);

    return addressRepository.save(address);
  }

  /**
   * Soft-deletes an address with cascade to related junctions. If this address is linked to an
   * organization, the OrganizationAddress junction is soft-deleted and all UserWorkLocation
   * assignments pointing to it are hard-deleted (users become locationless).
   */
  @Transactional
  public void deleteAddress(UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
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
