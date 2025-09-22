package com.fabricmanagement.contact.application.dto.usercontact.request;

import jakarta.validation.constraints.*;
import lombok.Builder;

/**
 * Request DTO for updating user contact information.
 * Focuses ONLY on user-specific contact information.
 */
@Builder
public record UpdateUserContactRequest(

    @Size(max = 100, message = "User display name cannot exceed 100 characters")
    String userDisplayName,

    @Email(message = "Personal email must be valid")
    @Size(max = 100, message = "Personal email cannot exceed 100 characters")
    String personalEmail,

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Personal phone must be a valid phone number")
    @Size(max = 50, message = "Personal phone cannot exceed 50 characters")
    String personalPhone,

    @Email(message = "Alternate email must be valid")
    @Size(max = 100, message = "Alternate email cannot exceed 100 characters")
    String alternateEmail,

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Alternate phone must be a valid phone number")
    @Size(max = 50, message = "Alternate phone cannot exceed 50 characters")
    String alternatePhone,

    @Size(max = 500, message = "Home address cannot exceed 500 characters")
    String homeAddress,

    @Size(max = 100, message = "City cannot exceed 100 characters")
    String city,

    @Size(max = 100, message = "State cannot exceed 100 characters")
    String state,

    @Size(max = 20, message = "Postal code cannot exceed 20 characters")
    String postalCode,

    @Size(max = 100, message = "Country cannot exceed 100 characters")
    String country,

    @Size(max = 100, message = "Emergency contact name cannot exceed 100 characters")
    String emergencyContactName,

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Emergency contact phone must be a valid phone number")
    @Size(max = 50, message = "Emergency contact phone cannot exceed 50 characters")
    String emergencyContactPhone,

    @Size(max = 50, message = "Emergency contact relation cannot exceed 50 characters")
    String emergencyContactRelation,

    @Pattern(regexp = "^(EMAIL|PHONE|SMS)$", message = "Preferred contact method must be EMAIL, PHONE, or SMS")
    String preferredContactMethod,

    @Size(max = 50, message = "Time zone cannot exceed 50 characters")
    String timeZone,

    @Size(max = 10, message = "Language preference cannot exceed 10 characters")
    String languagePreference,

    Boolean publicProfile,

    Boolean allowDirectMessages,

    Boolean allowNotifications,

    @Size(max = 1000, message = "Notes cannot exceed 1000 characters")
    String notes

) {}