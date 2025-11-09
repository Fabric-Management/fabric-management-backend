package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.value.ProfileCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for comprehensive user profile updates.
 * 
 * <p>Separates fields into categories for permission checks:
 * <ul>
 *   <li>WORK_PROFILE: firstName, lastName, workEmail, workPhone, workAddress, departmentId</li>
 *   <li>PERSONAL_PROFILE: homeAddress, personalPhone, birthDate, emergencyContact</li>
 * </ul>
 * 
 * <p><b>Security:</b> Self-update is NOT allowed. Only Admin/HR/Dept Manager can update.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserProfileRequest {

    // ========== WORK_PROFILE Fields ==========
    
    /**
     * First name (WORK_PROFILE).
     */
    private String firstName;

    /**
     * Last name (WORK_PROFILE).
     */
    private String lastName;

    /**
     * Work email contact value (WORK_PROFILE).
     * Will be used to create/update work email contact.
     */
    @Deprecated
    private String workEmail;

    /**
     * Work phone contact value (WORK_PROFILE).
     * Will be used to create/update work phone contact.
     */
    @Deprecated
    private String workPhone;

    /**
     * Work address data (WORK_PROFILE).
     */
    private AddressData workAddress;

    /**
     * Department ID (WORK_PROFILE).
     */
    private UUID departmentId;

    // ========== PERSONAL_PROFILE Fields ==========

    /**
     * Home address data (PERSONAL_PROFILE).
     */
    private AddressData homeAddress;

    /**
     * Personal phone contact value (PERSONAL_PROFILE).
     */
    @Deprecated
    private String personalPhone;

    /**
     * Birth date (PERSONAL_PROFILE).
     */
    private LocalDate birthDate;

    /**
     * Emergency contact data (PERSONAL_PROFILE).
     */
    private EmergencyContactData emergencyContact;

    /**
     * Determine which categories are being updated.
     * Used for permission checks.
     */
    public Set<ProfileCategory> getUpdatedCategories() {
        Set<ProfileCategory> categories = new HashSet<>();
        
        // Check WORK_PROFILE fields
        if (firstName != null || lastName != null || workEmail != null || 
            workPhone != null || workAddress != null || departmentId != null) {
            categories.add(ProfileCategory.WORK_PROFILE);
        }
        
        // Check PERSONAL_PROFILE fields
        if (homeAddress != null || personalPhone != null || 
            birthDate != null || emergencyContact != null) {
            categories.add(ProfileCategory.PERSONAL_PROFILE);
        }
        
        return categories;
    }

    /**
     * Check if any fields are being updated.
     */
    public boolean hasUpdates() {
        return firstName != null || lastName != null || workEmail != null ||
               workPhone != null || workAddress != null || departmentId != null ||
               homeAddress != null || personalPhone != null || 
               birthDate != null || emergencyContact != null;
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

