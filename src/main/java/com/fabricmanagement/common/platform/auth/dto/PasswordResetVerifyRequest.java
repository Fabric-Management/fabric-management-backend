package com.fabricmanagement.common.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Password reset verification request.
 * 
 * <p>Used to complete password reset flow:</p>
 * <ol>
 *   <li>User selects a masked contact from list</li>
 *   <li>Verification code is sent to selected contact</li>
 *   <li>User enters verification code + new password</li>
 *   <li>System verifies code and updates password</li>
 * </ol>
 * 
 * <p><b>Security:</b></p>
 * <ul>
 *   <li>authUserId: Direct lookup (prevents masking attack)</li>
 *   <li>code: 6-digit verification code (expires in 10 minutes)</li>
 *   <li>newPassword: Minimum 8 characters, must meet password policy</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetVerifyRequest {

    /**
     * AuthUser ID from masked contact selection.
     * Used for direct lookup - more secure and performant than masked value matching.
     */
    @NotNull(message = "Auth user ID is required")
    private UUID authUserId;

    /**
     * Verification code received via email/phone/WhatsApp.
     * 6-digit code with 10-minute expiry.
     */
    @NotBlank(message = "Verification code is required")
    @Pattern(regexp = "^\\d{6}$", message = "Verification code must be 6 digits")
    private String code;

    /**
     * New password to set after verification.
     * Must meet password policy requirements (min 8 chars, complexity, etc.)
     */
    @NotBlank(message = "New password is required")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    private String newPassword;
}

