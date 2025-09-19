package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.valueobject.ContactPhone;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing contact phones.
 */
@Service
public class ContactPhoneService {
    
    public List<ContactPhone> getContactPhones(UUID contactId) {
        // TODO: Implement phone retrieval logic
        return List.of();
    }
    
    public void addPhone(UUID contactId, String phoneNumber, boolean isPrimary) {
        // TODO: Implement phone addition logic
    }
    
    public void updatePhone(UUID phoneId, String phoneNumber, boolean isPrimary) {
        // TODO: Implement phone update logic
    }
    
    public void deletePhone(UUID phoneId) {
        // TODO: Implement phone deletion logic
    }
    
    public void addPhonesToContact(UUID contactId, java.util.List<String> phones) {
        // TODO: Implement bulk phone addition logic
    }
}
