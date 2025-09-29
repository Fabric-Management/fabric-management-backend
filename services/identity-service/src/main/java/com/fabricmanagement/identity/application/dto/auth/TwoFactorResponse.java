package com.fabricmanagement.identity.application.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Two-factor response representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class TwoFactorResponse {
    
    private boolean success;
    private String message;
    private String qrCode;
    private String secretKey;
    private String backupCodes;
}