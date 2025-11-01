package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.platform.communication.domain.Address;
import com.fabricmanagement.common.platform.communication.domain.AddressType;
import com.fabricmanagement.common.platform.communication.infra.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Address Service - Business logic for address management.
 *
 * <p>Handles CRUD operations for Address entities.</p>
 *
 * <p>Key responsibilities:
 * <ul>
 *   <li>Address creation and validation</li>
 *   <li>Primary address designation</li>
 *   <li>Address search by location</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional
    public Address createAddress(String streetAddress, String city, String state,
                                 String postalCode, String country, AddressType addressType,
                                 String label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.debug("Creating address: tenantId={}, type={}, city={}", tenantId, addressType, city);

        Address address = Address.builder()
            .streetAddress(streetAddress)
            .city(city)
            .state(state)
            .postalCode(postalCode)
            .country(country)
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

        return addressRepository.findById(addressId)
            .filter(a -> a.getTenantId().equals(tenantId));
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

        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (!address.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Address does not belong to current tenant");
        }

        // Remove primary flag from other addresses of same type
        List<Address> sameTypeAddresses = addressRepository.findByTenantIdAndAddressType(
            tenantId, address.getAddressType());
        
        sameTypeAddresses.forEach(a -> {
            if (!a.getId().equals(addressId) && a.getIsPrimary()) {
                a.removePrimary();
                addressRepository.save(a);
            }
        });

        address.setAsPrimary();
        return addressRepository.save(address);
    }

    @Transactional
    public Address updateAddress(UUID addressId, String streetAddress, String city,
                                 String state, String postalCode, String country, String label) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Updating address: tenantId={}, addressId={}", tenantId, addressId);

        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (!address.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Address does not belong to current tenant");
        }

        address.setStreetAddress(streetAddress);
        address.setCity(city);
        address.setState(state);
        address.setPostalCode(postalCode);
        address.setCountry(country);
        if (label != null) {
            address.setLabel(label);
        }

        return addressRepository.save(address);
    }

    @Transactional
    public void deleteAddress(UUID addressId) {
        UUID tenantId = TenantContext.getCurrentTenantId();
        log.info("Deleting address: tenantId={}, addressId={}", tenantId, addressId);

        Address address = addressRepository.findById(addressId)
            .orElseThrow(() -> new IllegalArgumentException("Address not found"));

        if (!address.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Address does not belong to current tenant");
        }

        address.delete();
        addressRepository.save(address);
    }
}

