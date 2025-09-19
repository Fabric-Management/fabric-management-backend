package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.valueobject.ContactEmail;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Service for managing contact emails.
 */
@Service
public class ContactEmailService {
    
    public List<ContactEmail> getContactEmails(UUID contactId) {
        // TODO: Implement email retrieval logic
        return List.of();
    }
    
    public void addEmail(UUID contactId, String email, boolean isPrimary) {
        // TODO: Implement email addition logic
    }
    
    public void updateEmail(UUID emailId, String email, boolean isPrimary) {
        // TODO: Implement email update logic
    }
    
    public void deleteEmail(UUID emailId) {
        // TODO: Implement email deletion logic
    }
    
    public void addEmailsToContact(UUID contactId, java.util.List<String> emails) {
        // TODO: Implement bulk email addition logic
    }
}
