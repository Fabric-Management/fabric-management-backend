package com.fabricmanagement.common.platform.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating external users (partner/supplier/customer users).
 * 
 * <p><b>No HR data:</b> Only basic user information for external company relationships.</p>
 * 
 * <p><b>Use Case:</b> Creating users for partner companies, suppliers, or customers (no HR records needed).</p>
 * 
 * <p><b>Required fields:</b></p>
 * <ul>
 *   <li>firstName, lastName, companyId - Basic user info</li>
 *   <li>contactValue, contactType - Primary contact for authentication</li>
 * </ul>
 * 
 * <p><b>Optional fields:</b></p>
 * <ul>
 *   <li>additionalContacts - Multiple emails/phones</li>
 *   <li>addresses - Work and home addresses</li>
 *   <li>department - Department assignment</li>
 * </ul>
 * 
 * <p><b>Note:</b> No HR data (title, gender, birth date, etc.) - these are for external business relationships only.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExternalUserRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Contact value is required")
    private String contactValue;

    @NotNull(message = "Contact type is required")
    private com.fabricmanagement.common.platform.user.domain.ContactType contactType;

    @NotNull(message = "Company ID is required")
    private UUID companyId;

    private String department;

    /**
     * Additional contacts (emails, phones) beyond the primary contact.
     */
    @Builder.Default
    @Valid
    private List<ContactData> additionalContacts = new ArrayList<>();

    /**
     * Addresses for the user (work, home, etc.).
     */
    @Builder.Default
    @Valid
    private List<AddressData> addresses = new ArrayList<>();
}

