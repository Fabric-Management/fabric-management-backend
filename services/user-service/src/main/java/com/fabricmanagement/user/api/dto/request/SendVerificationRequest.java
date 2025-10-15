package com.fabricmanagement.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Send Verification Request
 * 
 * Request to send verification code to unverified contact.
 * Used when user needs to verify before password setup/reset.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendVerificationRequest {

    @NotBlank(message = "Contact value is required")
    private String contactValue;  // email or phone
    
    /**
     * Preferred notification channel
     * Options: WHATSAPP (default), EMAIL, SMS
     */
    @Pattern(regexp = "^(WHATSAPP|EMAIL|SMS)?$", message = "Preferred channel must be WHATSAPP, EMAIL, or SMS")
    private String preferredChannel;
}

