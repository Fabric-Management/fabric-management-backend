package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when a contact is not found
 */
public class ContactNotFoundException extends DomainException {
    
    private static final String ERROR_CODE = "CONTACT_NOT_FOUND";

    public ContactNotFoundException(String contactValue) {
        super(String.format("Contact not found: %s", contactValue), ERROR_CODE);
    }

    public ContactNotFoundException(String contactValue, Throwable cause) {
        super(String.format("Contact not found: %s", contactValue), ERROR_CODE, cause);
    }
}
