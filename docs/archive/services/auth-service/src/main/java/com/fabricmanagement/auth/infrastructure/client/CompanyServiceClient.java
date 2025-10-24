package com.fabricmanagement.auth.infrastructure.client;

import com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig;
import com.fabricmanagement.shared.application.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Company Service Client
 * 
 * Feign client for communication with Company-Service
 * 
 * ✅ ZERO HARDCODED VALUES - BaseFeignClientConfig kullanıyor
 * ✅ PRODUCTION-READY - Circuit breaker, retry, timeout
 * ✅ INTERNAL ENDPOINT - Internal API Key authentication
 * ✅ JWT PROPAGATION - User context maintained
 */
@FeignClient(
    name = "company-service",
    url = "${services.company-service.url:http://localhost:8084}",
    configuration = BaseFeignClientConfig.class
)
public interface CompanyServiceClient {
    
    /**
     * Get company by ID
     */
    @GetMapping("/api/v1/companies/{companyId}")
    ApiResponse<CompanyResponse> getCompany(@PathVariable UUID companyId);
    
    /**
     * Get company settings
     */
    @GetMapping("/api/v1/companies/{companyId}/settings")
    ApiResponse<CompanySettingsResponse> getCompanySettings(@PathVariable UUID companyId);
    
    /**
     * Validate tenant access
     */
    @PostMapping("/api/v1/companies/{companyId}/validate-tenant")
    ApiResponse<Boolean> validateTenantAccess(
        @PathVariable UUID companyId,
        @RequestBody ValidateTenantRequest request
    );
    
    /**
     * Get company security settings
     */
    @GetMapping("/api/v1/companies/{companyId}/security")
    ApiResponse<CompanySecuritySettings> getSecuritySettings(@PathVariable UUID companyId);
    
    // =========================================================================
    // REQUEST/RESPONSE DTOs
    // =========================================================================
    
    class CompanyResponse {
        public UUID id;
        public String name;
        public String domain;
        public Boolean isActive;
        public UUID tenantId;
    }
    
    class CompanySettingsResponse {
        public UUID companyId;
        public String timezone;
        public String language;
        public String currency;
    }
    
    class CompanySecuritySettings {
        public Boolean twoFactorRequired;
        public Integer passwordMinLength;
        public Integer maxFailedAttempts;
        public Integer sessionTimeoutMinutes;
        public Boolean accountLockoutEnabled;
    }
    
    class ValidateTenantRequest {
        public UUID userId;
        public String requiredRole;
    }
}
