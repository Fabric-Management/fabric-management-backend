package com.fabricmanagement.common.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Password setup request - Complete registration with secure token.
 *
 * <p><b>Flow:</b> Token + password only (email verified by link click)</p>
 *
 * <p><b>Note:</b> Both SALES_LED and SELF_SERVICE tokens work the same way.
 * No verification code needed - email link click verifies ownership.
 * Verification codes are only used for unverified contacts during login flows.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetupRequest {

    @NotBlank(message = "Token is required")
    private String token;

    /**
     * Verification code - NOT used in password setup flow.
     * 
     * <p>This field is deprecated for password setup. Verification codes
     * are only used for unverified contacts during login flows.</p>
     * 
     * @deprecated Verification code not needed - email link click verifies ownership
     */
    @Deprecated
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String verificationCode;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}

