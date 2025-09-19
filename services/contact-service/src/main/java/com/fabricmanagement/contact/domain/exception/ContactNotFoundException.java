package com.fabricmanagement.contact.domain.exception;

/**
 * Exception thrown when a contact is not found.
 */
public class ContactNotFoundException extends RuntimeException {
    
    public ContactNotFoundException(String message) {
        super(message);
    }
    
    public ContactNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public static ContactNotFoundException forId(String id) {
        return new ContactNotFoundException("Contact not found with id: " + id);
    }
    
    public static ContactNotFoundException forUserId(String userId) {
        return new ContactNotFoundException("Contact not found for user id: " + userId);
    }
    
    public static ContactNotFoundException forCompanyId(String companyId) {
        return new ContactNotFoundException("Contact not found for company id: " + companyId);
    }
}
