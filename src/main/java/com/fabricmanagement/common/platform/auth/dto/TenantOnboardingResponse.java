package com.fabricmanagement.common.platform.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Tenant onboarding response.
 *
 * <p>Contains all information about newly created tenant.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingResponse {

    private UUID companyId;
    
    private UUID tenantId;
    
    private String companyUid;
    
    private String companyName;
    
    private UUID adminUserId;
    
    private String adminContactValue;
    
    private String registrationToken;
    
    private List<String> subscriptions;
    
    private Instant trialEndsAt;
    
    private String setupUrl;
}

