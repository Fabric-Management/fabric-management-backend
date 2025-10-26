package com.fabricmanagement.common.platform.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Password setup request - Complete registration with secure token.
 *
 * <p><b>Simple flow:</b> Token + password only</p>
 * <p>Email link click proves email ownership - no code needed.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordSetupRequest {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    private String password;
}

