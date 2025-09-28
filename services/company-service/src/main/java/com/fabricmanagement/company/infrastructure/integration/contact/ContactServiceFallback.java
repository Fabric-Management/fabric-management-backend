package com.fabricmanagement.company.infrastructure.integration.contact;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Fallback implementation for ContactServiceClient.
 * Provides graceful degradation when contact-service is unavailable.
 */
@Component
@Slf4j
public class ContactServiceFallback implements ContactServiceClient {

    @Override
    public ApiResponse<CompanyContactResponse> getCompanyContact(UUID companyId) {
        log.warn("Contact service unavailable - using fallback for getCompanyContact: {}", companyId);
        return ApiResponse.<CompanyContactResponse>builder()
            .success(false)
            .message("Contact service temporarily unavailable")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<CompanyContactResponse> createCompanyContact(CreateCompanyContactRequest request) {
        log.warn("Contact service unavailable - using fallback for createCompanyContact");
        return ApiResponse.<CompanyContactResponse>builder()
            .success(false)
            .message("Contact service temporarily unavailable - contact creation failed")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<CompanyContactResponse> updateCompanyContact(UUID companyId, UpdateCompanyContactRequest request) {
        log.warn("Contact service unavailable - using fallback for updateCompanyContact: {}", companyId);
        return ApiResponse.<CompanyContactResponse>builder()
            .success(false)
            .message("Contact service temporarily unavailable - contact update failed")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<Void> deleteCompanyContact(UUID companyId) {
        log.warn("Contact service unavailable - using fallback for deleteCompanyContact: {}", companyId);
        return ApiResponse.<Void>builder()
            .success(false)
            .message("Contact service temporarily unavailable - contact deletion failed")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<Boolean> hasCompanyContact(UUID companyId) {
        log.warn("Contact service unavailable - using fallback for hasCompanyContact: {}", companyId);
        return ApiResponse.<Boolean>builder()
            .success(false)
            .message("Contact service temporarily unavailable")
            .data(false)
            .build();
    }
}