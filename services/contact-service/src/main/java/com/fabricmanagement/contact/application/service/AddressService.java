package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.api.dto.request.CreateAddressRequest;
import com.fabricmanagement.contact.api.dto.response.AddressResponse;
import com.fabricmanagement.contact.application.mapper.AddressMapper;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.entity.Address;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.contact.infrastructure.repository.AddressRepository;
import com.fabricmanagement.contact.infrastructure.repository.ContactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Address Service
 * 
 * Business logic for Address management
 * Anemic Domain Model → Logic in Service layer
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AddressService {

    private final AddressRepository addressRepository;
    private final ContactRepository contactRepository;
    private final AddressMapper addressMapper;

    /**
     * Creates address contact (Contact + Address)
     * 
     * Creates:
     * 1. Contact entry (type=ADDRESS, contactValue=null)
     * 2. Address entry (linked to contact)
     */
    @Transactional
    public AddressResponse createAddress(CreateAddressRequest request) {
        UUID ownerId = UUID.fromString(request.getOwnerId());
        Contact.OwnerType ownerType = Contact.OwnerType.valueOf(request.getOwnerType());
        
        log.info("Creating address for owner: {} ({})", ownerId, ownerType);

        // 1. Create Contact entry (marker for ADDRESS type)
        Contact contact = Contact.builder()
                .ownerId(ownerId)
                .ownerType(ownerType)
                .contactType(ContactType.ADDRESS)
                .contactValue(null)  // No value for ADDRESS
                .isVerified(false)
                .isPrimary(request.getIsPrimary())
                .build();
        
        contact = contactRepository.save(contact);
        log.debug("Created contact marker: {}", contact.getId());

        // 2. Remove primary from others if this is primary
        if (request.getIsPrimary()) {
            addressRepository.removePrimaryStatusForOwner(ownerId, ownerType);
        }

        // 3. Create Address entry
        Address address = addressMapper.fromCreateRequest(request, contact.getId());
        address = addressRepository.save(address);
        
        log.info("Address created successfully: {}", address.getId());
        return addressMapper.toResponse(address);
    }

    /**
     * Gets all addresses for an owner
     */
    @Transactional(readOnly = true)
    public List<AddressResponse> getAddressesByOwner(UUID ownerId, String ownerType) {
        log.debug("Fetching addresses for owner: {} ({})", ownerId, ownerType);
        
        List<Address> addresses = addressRepository.findByOwner(
            ownerId, 
            Contact.OwnerType.valueOf(ownerType)
        );
        
        return addresses.stream()
                .map(addressMapper::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Gets address by ID
     */
    @Transactional(readOnly = true)
    public AddressResponse getAddress(UUID addressId) {
        log.debug("Fetching address: {}", addressId);
        
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        
        return addressMapper.toResponse(address);
    }

    /**
     * Gets address by contact ID
     */
    @Transactional(readOnly = true)
    public AddressResponse getAddressByContactId(UUID contactId) {
        log.debug("Fetching address by contact: {}", contactId);
        
        Address address = addressRepository.findByContactId(contactId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found for contact: " + contactId));
        
        return addressMapper.toResponse(address);
    }

    /**
     * Updates address
     */
    @Transactional
    public AddressResponse updateAddress(UUID addressId, CreateAddressRequest request) {
        log.info("Updating address: {}", addressId);
        
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        
        // Update fields
        addressMapper.updateFromRequest(address, request);
        
        // Handle primary flag
        if (request.getIsPrimary() && !address.isPrimary()) {
            addressRepository.removePrimaryStatusForOwner(address.getOwnerId(), address.getOwnerType());
            address.setPrimary(true);
        }
        
        address = addressRepository.save(address);
        log.info("Address updated successfully: {}", addressId);
        
        return addressMapper.toResponse(address);
    }

    /**
     * Deletes address (soft delete)
     */
    @Transactional
    public void deleteAddress(UUID addressId) {
        log.info("Deleting address: {}", addressId);
        
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        
        // Soft delete address
        address.markAsDeleted();
        addressRepository.save(address);
        
        // Soft delete contact marker
        Contact contact = contactRepository.findById(address.getContactId())
                .orElseThrow(() -> new IllegalArgumentException("Contact not found: " + address.getContactId()));
        contact.markAsDeleted();
        contactRepository.save(contact);
        
        log.info("Address deleted successfully: {}", addressId);
    }

    /**
     * Sets address as primary
     */
    @Transactional
    public void setAsPrimary(UUID addressId) {
        log.info("Setting address as primary: {}", addressId);
        
        Address address = addressRepository.findById(addressId)
                .orElseThrow(() -> new IllegalArgumentException("Address not found: " + addressId));
        
        // Remove primary from others
        addressRepository.removePrimaryStatusForOwner(address.getOwnerId(), address.getOwnerType());
        
        // Set this as primary
        address.setPrimary(true);
        addressRepository.save(address);
        
        log.info("Address set as primary: {}", addressId);
    }
}

