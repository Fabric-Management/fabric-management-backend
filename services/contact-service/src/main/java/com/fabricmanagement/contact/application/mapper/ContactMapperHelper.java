package com.fabricmanagement.contact.application.mapper;

import org.springframework.stereotype.Component;

/**
 * Helper class for ContactMapper to provide additional mapping utilities.
 * This class can be used by MapStruct for complex mapping operations
 * that require custom logic or external dependencies.
 */
@Component
public class ContactMapperHelper {

    /**
     * Validates and normalizes phone numbers.
     * Can be extended to include country code validation, formatting, etc.
     */
    public String normalizePhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }
        // Remove all non-numeric characters except + for international prefix
        return phoneNumber.replaceAll("[^+0-9]", "");
    }

    /**
     * Validates email addresses.
     */
    public boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        // Basic email validation pattern
        String emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailPattern);
    }

    /**
     * Formats display name from first and last name.
     */
    public String formatDisplayName(String firstName, String lastName) {
        if (firstName == null && lastName == null) {
            return null;
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    /**
     * Generates a default display name if none provided.
     */
    public String generateDisplayName(String firstName, String lastName, String companyName) {
        String fromNames = formatDisplayName(firstName, lastName);
        if (fromNames != null && !fromNames.isEmpty()) {
            return fromNames;
        }
        if (companyName != null && !companyName.isEmpty()) {
            return companyName;
        }
        return "Contact";
    }
}