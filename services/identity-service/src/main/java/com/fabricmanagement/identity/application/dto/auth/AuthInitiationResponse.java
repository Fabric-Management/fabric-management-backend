package com.fabricmanagement.identity.application.dto.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Single Responsibility: Auth initiation response representation only
 * Open/Closed: Can be extended without modification
 */
@Getter
@Setter
@Builder
public class AuthInitiationResponse {
    
    private boolean success;
    private String message;
    private String sessionId;
    private boolean requiresTwoFactor;
    private String qrCode;
    private String secretKey;
}