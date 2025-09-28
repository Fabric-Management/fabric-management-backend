package com.fabricmanagement.company.infrastructure.integration.contact;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Feign client for integrating with contact-service.
 * Handles all communication with contact-service for company contact information.
 *
 * Circuit breaker and resilience patterns will be applied via configuration.
 */
@FeignClient(
    name = "contact-service",
    url = "${services.contact-service.url:http://localhost:8082}",
    configuration = ContactServiceClientConfiguration.class
)
public interface ContactServiceClient {

    /**
     * Get company contact information.
     */
    @GetMapping("/api/v1/contacts/company/{companyId}")
    ApiResponse<CompanyContactResponse> getCompanyContact(@PathVariable UUID companyId);

    /**
     * Create company contact information.
     */
    @PostMapping("/api/v1/contacts/company")
    ApiResponse<CompanyContactResponse> createCompanyContact(@RequestBody CreateCompanyContactRequest request);

    /**
     * Update company contact information.
     */
    @PutMapping("/api/v1/contacts/company/{companyId}")
    ApiResponse<CompanyContactResponse> updateCompanyContact(
        @PathVariable UUID companyId,
        @RequestBody UpdateCompanyContactRequest request
    );

    /**
     * Delete company contact information.
     */
    @DeleteMapping("/api/v1/contacts/company/{companyId}")
    ApiResponse<Void> deleteCompanyContact(@PathVariable UUID companyId);

    /**
     * Check if company contact exists.
     */
    @GetMapping("/api/v1/contacts/company/{companyId}/exists")
    ApiResponse<Boolean> hasCompanyContact(@PathVariable UUID companyId);
}