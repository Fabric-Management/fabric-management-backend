package com.fabricmanagement.user.application.dto.contact.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for adding a user contact.
 */
public record AddContactRequest(
    @NotBlank(message = "Contact type is required")
    String contactType,
    
    @NotBlank(message = "Contact value is required")
    String contactValue
) {
    
    /**
     * Validates email format if contact type is EMAIL.
     */
    public boolean isValidEmail() {
        if ("EMAIL".equals(contactType)) {
            return contactValue != null && contactValue.contains("@");
        }
        return true;
    }
    
    /**
     * Validates phone format if contact type is PHONE.
     */
    public boolean isValidPhone() {
        if ("PHONE".equals(contactType)) {
            return contactValue != null && contactValue.matches("^\\+?[1-9]\\d{1,14}$");
        }
        return true;
    }
}
