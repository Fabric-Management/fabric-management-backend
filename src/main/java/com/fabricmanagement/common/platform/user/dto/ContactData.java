package com.fabricmanagement.common.platform.user.dto;

import com.fabricmanagement.common.platform.user.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contact data for user creation (email or phone).
 * 
 * <p>Used in CreateInternalUserRequest and CreateExternalUserRequest.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactData {
    @NotBlank(message = "Contact value is required")
    private String contactValue;

    @NotNull(message = "Contact type is required")
    private ContactType contactType;

    /**
     * Label for this contact (e.g., "Work Email", "Personal Phone", "Mobile")
     */
    private String label;

    /**
     * Whether this is a personal contact (true) or work contact (false).
     * Default: true (personal)
     */
    @Builder.Default
    private Boolean isPersonal = true;

    /**
     * Whether this contact has WhatsApp capability (for PHONE contacts only).
     * If null, system will check automatically via WhatsApp API.
     * If true, verification codes and notifications will prioritize WhatsApp.
     * Note: Only applicable for MOBILE phones, not LANDLINE.
     */
    private Boolean isWhatsApp;

    /**
     * Phone type (for PHONE contacts only).
     * If null, defaults to MOBILE for backward compatibility.
     * Allowed values: "MOBILE", "LANDLINE"
     * 
     * <p>WhatsApp capability is only checked for MOBILE phones.
     * LANDLINE phones cannot have WhatsApp.</p>
     */
    private String phoneType; // "MOBILE" or "LANDLINE"
}

