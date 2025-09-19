package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.valueobject.ContactAddress;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing contact addresses.
 */
@Service
public class ContactAddressService {
    
    public List<ContactAddress> getContactAddresses(UUID contactId) {
        // TODO: Implement address retrieval logic
        return List.of();
    }
    
    public void addAddress(UUID contactId, String address, boolean isPrimary) {
        // TODO: Implement address addition logic
    }
    
    public void updateAddress(UUID addressId, String address, boolean isPrimary) {
        // TODO: Implement address update logic
    }
    
    public void deleteAddress(UUID addressId) {
        // TODO: Implement address deletion logic
    }
    
    public void addAddressesToContact(UUID contactId, java.util.List<String> addresses) {
        // TODO: Implement bulk address addition logic
    }
}
