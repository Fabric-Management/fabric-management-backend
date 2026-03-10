package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import com.fabricmanagement.human.core.employee.domain.Gender;
import com.fabricmanagement.human.core.employee.domain.Title;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for comprehensive user profile updates.
 *
 * <p>Separates fields into categories for permission checks:
 *
 * <ul>
 *   <li>WORK_PROFILE: firstName, lastName, workEmail, workPhone, workAddress, departmentId
 *   <li>PERSONAL_PROFILE: homeAddress, personalPhone, birthDate, emergencyContact, title, gender,
 *       nationality, employeeNumber, hireDate
 * </ul>
 *
 * <p><b>Security:</b> Self-update is NOT allowed. Only Admin/HR/Dept Manager can update.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

  // ========== WORK_PROFILE Fields ==========

  /** First name (WORK_PROFILE). */
  private String firstName;

  /** Last name (WORK_PROFILE). */
  private String lastName;

  /** Role ID (WORK_PROFILE). Updates the user's role assignment. */
  private UUID roleId;

  /** Work email contact value (WORK_PROFILE). Creates/updates work email contact. */
  private String workEmail;

  /** Work phone contact value (WORK_PROFILE). Creates/updates work phone contact. */
  private String workPhone;

  /** Work address data (WORK_PROFILE). */
  private AddressData workAddress;

  /** Department ID (WORK_PROFILE). */
  private UUID departmentId;

  // ========== PERSONAL_PROFILE Fields ==========

  /** Home address data (PERSONAL_PROFILE). */
  private AddressData homeAddress;

  /** Personal phone contact value (PERSONAL_PROFILE). */
  private String personalPhone;

  /** Birth date (PERSONAL_PROFILE). */
  private LocalDate birthDate;

  /** Emergency contact data (PERSONAL_PROFILE). */
  private EmergencyContactData emergencyContact;

  /** Personal title/salutation (PERSONAL_PROFILE). */
  private Title title;

  /** Gender identity (PERSONAL_PROFILE). */
  private Gender gender;

  /** Nationality ISO code (PERSONAL_PROFILE). */
  @Size(max = 10, message = "Nationality code must be at most 10 characters")
  private String nationality;

  /** Employee number (PERSONAL_PROFILE). */
  @Size(max = 50, message = "Employee number must be at most 50 characters")
  private String employeeNumber;

  /** Employment start / hire date (PERSONAL_PROFILE). */
  private LocalDate hireDate;

  /** Determine which categories are being updated. Used for permission checks. */
  public Set<ProfileCategory> getUpdatedCategories() {
    Set<ProfileCategory> categories = new HashSet<>();

    // Check WORK_PROFILE fields
    if (firstName != null
        || lastName != null
        || roleId != null
        || workEmail != null
        || workPhone != null
        || workAddress != null
        || departmentId != null) {
      categories.add(ProfileCategory.WORK_PROFILE);
    }

    // Check PERSONAL_PROFILE fields
    if (homeAddress != null
        || personalPhone != null
        || birthDate != null
        || emergencyContact != null
        || title != null
        || gender != null
        || nationality != null
        || employeeNumber != null
        || hireDate != null) {
      categories.add(ProfileCategory.PERSONAL_PROFILE);
    }

    return categories;
  }

  /** Check if any fields are being updated. */
  public boolean hasUpdates() {
    return firstName != null
        || lastName != null
        || roleId != null
        || workEmail != null
        || workPhone != null
        || workAddress != null
        || departmentId != null
        || homeAddress != null
        || personalPhone != null
        || birthDate != null
        || emergencyContact != null
        || title != null
        || gender != null
        || nationality != null
        || employeeNumber != null
        || hireDate != null;
  }

  // ========== Nested DTOs ==========

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AddressData {
    private String streetAddress;
    private String city;
    private String state;
    private String postalCode;
    private String country;
    private String placeId; // Google Maps Place ID for validation
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class EmergencyContactData {
    private String name;
    private String phone;
    private String relationship;
  }
}
