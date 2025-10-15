package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.user.infrastructure.client.dto.CheckCompanyDuplicateDto;
import com.fabricmanagement.user.infrastructure.client.dto.CompanyDuplicateCheckResult;
import com.fabricmanagement.user.infrastructure.client.dto.CreateCompanyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Company Service Client Fallback
 * 
 * Provides graceful degradation when Company Service is unavailable.
 * 
 * ✅ RESILIENCE STRATEGY for Tenant Onboarding:
 * - createCompany: CRITICAL - Fail gracefully with clear error
 * - checkDuplicate: Optimistic - Allow registration (assume no duplicate)
 * 
 * ⚠️ MONITORING:
 * - All fallback activations are logged at ERROR level
 * - Alerts should be configured for Company Service downtime
 * 
 * 📊 METRICS:
 * - Track fallback activation rate
 * - Alert if > 5% of requests use fallback
 * 
 * Pattern: @Lazy to prevent eager initialization during FeignClient setup
 * 
 * @since 3.1.0 - Resilience Enhancement (Oct 14, 2025)
 */
@Component
@Lazy  // ✅ Lazy initialization to prevent circular dependency
@Slf4j
public class CompanyServiceClientFallback implements CompanyServiceClient {

    /**
     * Fallback for createCompany
     * 
     * CRITICAL OPERATION - Tenant onboarding cannot proceed without company creation.
     * 
     * Strategy: FAIL FAST with clear error message
     * - Return error response with SERVICE_UNAVAILABLE code
     * - Log at ERROR level for immediate alerting
     * - User sees clear message: "Company Service temporarily unavailable"
     * 
     * Alternative Strategy (NOT USED):
     * - Queue for later processing (adds complexity)
     * - Partial registration (creates inconsistency)
     * 
     * Decision: Fail fast is best for data consistency
     */
    @Override
    public ApiResponse<UUID> createCompany(CreateCompanyDto request) {
        log.error("🚨 CRITICAL FALLBACK: {} - Cannot create company: {} (Tax ID: {})", 
            ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE,
            request.getName(),
            request.getTaxId());
        
        return ApiResponse.error(
            ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE
        );
    }

    /**
     * Fallback for checkDuplicate
     * 
     * VALIDATION OPERATION - Used during tenant onboarding.
     * 
     * Strategy: OPTIMISTIC - Allow registration to proceed
     * - Assume no duplicate exists
     * - Registration proceeds normally
     * - Duplicate detection can happen later via background job
     * 
     * Rationale:
     * - False negative (missing duplicate) < False positive (blocking valid registration)
     * - Company Service will enforce uniqueness on actual creation
     * - Better UX: Don't block users when validation service is down
     * 
     * Risk: Minimal - uniqueness constraints exist at DB level
     */
    @Override
    public ApiResponse<CompanyDuplicateCheckResult> checkDuplicate(CheckCompanyDuplicateDto request) {
        log.warn("⚠️ Fallback: {} - Assuming no duplicate for company: {} (Tax ID: {})", 
            ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE,
            request.getName(),
            request.getTaxId());
        
        // Optimistic result: no duplicate found
        CompanyDuplicateCheckResult result = CompanyDuplicateCheckResult.builder()
                .isDuplicate(false)
                .message("Duplicate check bypassed - Company Service unavailable")
                .confidence(0.0)
                .recommendation("PROCEED_WITH_CAUTION")
                .build();
        
        return ApiResponse.success(result, ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE);
    }
}
