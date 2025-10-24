package com.fabricmanagement.user.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Setup Password With Verification Request
 * 
 * Atomic operation: Verify contact + Setup password + Login
 * 
 * Flow:
 * 1. User enters contact + password
 * 2. System sends verification code
 * 3. User enters code in pop-up
 * 4. This request: Verify + Create password + Auto-login
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetupPasswordWithVerificationRequest {

    @NotBlank(message = "Contact value is required")
    private String contactValue;  // email or phone

    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 6, message = "Verification code must be 6 digits")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String verificationCode;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$",
            message = "Password must contain uppercase, lowercase, number and special character")
    private String password;
    
    /**
     * Preferred channel for future notifications (optional)
     */
    @Pattern(regexp = "^(WHATSAPP|EMAIL|SMS)?$", message = "Preferred channel must be WHATSAPP, EMAIL, or SMS")
    private String preferredChannel;
}

