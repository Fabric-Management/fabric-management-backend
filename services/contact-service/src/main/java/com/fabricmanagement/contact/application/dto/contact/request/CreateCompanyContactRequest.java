package com.fabricmanagement.contact.application.dto.contact.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Builder;

import java.util.List;

/**
 * Request DTO for creating a company contact as a record.
 * Focused only on contact information.
 */
@Builder
public record CreateCompanyContactRequest(
    @NotBlank(message = "Company name is required")
    @Size(max = 200, message = "Company name cannot exceed 200 characters")
    String companyName,

    @Pattern(regexp = "^https?://.*", message = "Website must be a valid URL")
    @Size(max = 500, message = "Website URL cannot exceed 500 characters")
    String website,

    @Size(max = 200, message = "Main contact person name cannot exceed 200 characters")
    String mainContactPerson,

    @Email(message = "Main contact email must be valid")
    @Size(max = 100, message = "Main contact email cannot exceed 100 characters")
    String mainContactEmail,

    @Pattern(regexp = "^[+]?[0-9\\s\\-().]{3,50}$", message = "Main contact phone must be a valid phone number")
    @Size(max = 50, message = "Main contact phone cannot exceed 50 characters")
    String mainContactPhone,

    @Size(max = 200, message = "Business hours cannot exceed 200 characters")
    String businessHours,

    @Size(max = 5000, message = "Notes cannot exceed 5000 characters")
    String notes,

    @Valid
    List<CreateEmailRequest> emails,

    @Valid
    List<CreatePhoneRequest> phones,

    @Valid
    List<CreateAddressRequest> addresses
) {
    public boolean hasMainContact() {
        return mainContactPerson != null && !mainContactPerson.trim().isEmpty();
    }

    public boolean hasContactInfo() {
        return (mainContactEmail != null && !mainContactEmail.trim().isEmpty()) ||
               (mainContactPhone != null && !mainContactPhone.trim().isEmpty()) ||
               (emails != null && !emails.isEmpty()) ||
               (phones != null && !phones.isEmpty());
    }
}