package com.fabricmanagement.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for contact check
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckContactResponse {

    private boolean exists;           // Contact exists in system
    private boolean hasPassword;      // User has created password
    private String userId;            // User ID (if exists)
    private String message;           // User-friendly message
}
