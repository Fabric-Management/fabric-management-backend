package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.human.core.employee.domain.Gender;
import com.fabricmanagement.human.core.employee.domain.Title;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating internal employees (own staff).
 * 
 * <p><b>Includes HR data:</b> Title, gender, birth date, nationality, emergency contact, employee number, hire date.</p>
 * 
 * <p><b>Use Case:</b> Creating employees for your own company (tenant users with HR records).</p>
 * 
 * <p><b>Required fields:</b></p>
 * <ul>
 *   <li>firstName, lastName, companyId - Basic user info</li>
 *   <li>contactValue, contactType - Primary contact for authentication</li>
 * </ul>
 * 
 * <p><b>Optional HR fields:</b></p>
 * <ul>
 *   <li>title, gender, birthDate, nationality - Personal information</li>
 *   <li>employeeNumber, hireDate - Employment details</li>
 *   <li>emergencyContact - Emergency contact information</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInternalUserRequest {

    // ========== Basic User Fields (from CreateUserRequest) ==========
    
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
     * Department ID (if department is selected by ID instead of name).
     */
    private UUID departmentId;

    /**
     * Department Category ID (for organizational structure).
     */
    private UUID departmentCategoryId;

    /**
     * Role ID for user assignment.
     */
    private UUID roleId;

    /**
     * Position ID for user assignment.
     */
    private UUID positionId;

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

    // ========== HR/Employee Fields ==========

    /**
     * Personal title/salutation (Mr, Miss, Mrs, Ms, Dr, Prof, Eng, None).
     */
    private Title title;

    /**
     * Gender identity (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY).
     */
    private Gender gender;

    /**
     * Birth date for age calculations and HR requirements.
     */
    private LocalDate birthDate;

    /**
     * Nationality (ISO 3166-1 alpha-2 country code: TR, US, GB, etc.).
     */
    private String nationality;

    /**
     * Employee number (company-specific unique identifier).
     * Example: "EMP-001", "2024-001"
     */
    private String employeeNumber;

    /**
     * Employment start date (hire date).
     */
    private LocalDate hireDate;

    /**
     * Emergency contact information.
     */
    @Valid
    private EmergencyContactData emergencyContact;

    // ========== Nested DTOs ==========

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EmergencyContactData {
        private String name;
        private String phone;
        private String relationship; // e.g., "Spouse", "Parent", "Sibling", "Friend"
    }
}

