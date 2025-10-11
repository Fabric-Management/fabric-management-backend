package com.fabricmanagement.user.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantOnboardingResponse {
    
    private UUID companyId;
    private UUID userId;
    private String email;
    private String message;
    private String nextStep;
}

