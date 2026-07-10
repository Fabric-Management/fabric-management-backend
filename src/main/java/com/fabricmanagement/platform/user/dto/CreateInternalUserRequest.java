package com.fabricmanagement.platform.user.dto;

import com.fabricmanagement.common.infrastructure.identity.Gender;
import com.fabricmanagement.common.infrastructure.identity.Title;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating internal employees (own staff).
 *
 * <p><b>Includes HR data:</b> Title, gender, birth date, nationality, emergency contact, employee
 * number, hire date.
 *
 * <p><b>Use Case:</b> Creating employees for your own company (tenant users with HR records).
 *
 * <p><b>Required fields:</b>
 *
 * <ul>
 *   <li>firstName, lastName, organizationId - Basic user info
 *   <li>contactValue, contactType - Primary contact for authentication
 * </ul>
 *
 * <p><b>Optional HR fields:</b>
 *
 * <ul>
 *   <li>title, gender, birthDate, nationality - Personal information
 *   <li>employeeNumber, hireDate - Employment details
 *   <li>emergencyContact - Emergency contact information
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInternalUserRequest {

  // ========== Basic User Fields (from CreateUserRequest) ==========

  @NotBlank(message = "First name is required")
  @Size(max = 100, message = "First name must be at most 100 characters")
  private String firstName;

  @NotBlank(message = "Last name is required")
  @Size(max = 100, message = "Last name must be at most 100 characters")
  private String lastName;

  @NotBlank(message = "Contact value is required")
  @Size(max = 255, message = "Contact value must be at most 255 characters")
  private String contactValue;

  @NotNull(message = "Contact type is required")
  private com.fabricmanagement.platform.user.domain.ContactType contactType;

  @NotNull(message = "Organization ID is required")
  private UUID organizationId;

  private String department;

  /** Department ID (if department is selected by ID instead of name). */
  private UUID departmentId;

  /** Role ID for user assignment. */
  private UUID roleId;

  /** Optional job title code to assign a JobTitlePreset. */
  private String jobTitleCode;

  /** Additional contacts (emails, phones) beyond the primary contact. */
  @Builder.Default @Valid private List<ContactData> additionalContacts = new ArrayList<>();

  /** Addresses for the user (work, home, etc.). */
  @Builder.Default @Valid private List<AddressData> addresses = new ArrayList<>();

  // ========== HR/Employee Fields ==========

  /** Personal title/salutation (Mr, Miss, Mrs, Ms, Dr, Prof, Eng, None). */
  private Title title;

  /** Gender identity (MALE, FEMALE, OTHER, PREFER_NOT_TO_SAY). */
  private Gender gender;

  /** Birth date for age calculations and HR requirements. */
  private LocalDate birthDate;

  /** Nationality (ISO 3166-1 alpha-2 country code: TR, US, GB, etc.). */
  @Size(max = 10, message = "Nationality code must be at most 10 characters")
  private String nationality;

  /** Employee number (company-specific unique identifier). Example: "EMP-001", "2024-001" */
  @Size(max = 50, message = "Employee number must be at most 50 characters")
  private String employeeNumber;

  /** Employment start date (hire date). */
  private LocalDate hireDate;

  /** Organization address ID for work location assignment (optional). */
  private UUID workLocationOrgAddressId;

  /** Emergency contact information. */
  @Valid private EmergencyContactData emergencyContact;

  /**
   * When {@code true}, user creation does not publish an invitation email event.
   *
   * <p>Demo seeders create fictional employees and must set this flag so setup data does not
   * generate real invitation mail.
   */
  @Builder.Default private boolean invitationEmailSuppressed = false;

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
