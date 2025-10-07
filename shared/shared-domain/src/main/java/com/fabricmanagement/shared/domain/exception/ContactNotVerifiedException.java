package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when trying to perform operations on unverified contact
 */
public class ContactNotVerifiedException extends DomainException {
    
    private static final String ERROR_CODE = "CONTACT_NOT_VERIFIED";

    public ContactNotVerifiedException(String contactValue) {
        super(String.format("Contact not verified: %s. Please verify your contact before setting up password.", contactValue), 
              ERROR_CODE);
    }

    public ContactNotVerifiedException(String contactValue, Throwable cause) {
        super(String.format("Contact not verified: %s", contactValue), ERROR_CODE, cause);
    }
}
