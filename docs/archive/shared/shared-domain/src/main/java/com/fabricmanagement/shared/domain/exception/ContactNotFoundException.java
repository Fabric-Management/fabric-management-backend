package com.fabricmanagement.shared.domain.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * Contact Not Found Exception
 * 
 * Exception thrown when contact is not found
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ DOMAIN EXCEPTION
 * ✅ UUID TYPE SAFETY
 */
@Getter
public class ContactNotFoundException extends DomainException {
    
    private final UUID contactId;
    private final String contactValue;
    
    public ContactNotFoundException(UUID contactId) {
        super("Contact not found with ID: " + contactId);
        this.contactId = contactId;
        this.contactValue = null;
    }
    
    public ContactNotFoundException(String contactValue) {
        super("Contact not found with value: " + contactValue);
        this.contactId = null;
        this.contactValue = contactValue;
    }
    
    public ContactNotFoundException(UUID contactId, String contactValue) {
        super("Contact not found with ID: " + contactId + " and value: " + contactValue);
        this.contactId = contactId;
        this.contactValue = contactValue;
    }
    
    public ContactNotFoundException(String message, UUID contactId) {
        super(message);
        this.contactId = contactId;
        this.contactValue = null;
    }
    
    public ContactNotFoundException(String message, String contactValue) {
        super(message);
        this.contactId = null;
        this.contactValue = contactValue;
    }
}