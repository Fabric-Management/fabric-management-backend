package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
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
            .isPrimary(false)
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
  public Address setAsPrimary(UUID addressId) {
    UUID tenantId = TenantContext.getCurrentTenantId();
    log.info("Setting address as primary: tenantId={}, addressId={}", tenantId, addressId);

    Address address =
        addressRepository
            .findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

    if (!address.getTenantId().equals(tenantId)) {
      throw new IllegalArgumentException("Address does not belong to current tenant");
    }

    // Remove primary flag from other addresses of same type
    List<Address> sameTypeAddresses =
        addressRepository.findByTenantIdAndAddressType(tenantId, address.getAddressType());

    sameTypeAddresses.forEach(
        a -> {
          if (!a.getId().equals(addressId) && a.getIsPrimary()) {
            a.removePrimary();
            addressRepository.save(a);
          }
        });

    address.setAsPrimary();
    return addressRepository.save(address);
  }

  @Transactional
  public Address updateAddress(
      UUID addressId,
      String streetAddress,
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
