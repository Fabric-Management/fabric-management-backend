package com.fabricmanagement.contact.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.core.application.dto.PageRequest;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactDetailResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.application.service.CompanyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for company contact operations.
 */
@RestController
@RequestMapping("/api/v1/contacts/companies")
@RequiredArgsConstructor
@Tag(name = "Company Contacts", description = "Company contact management endpoints")
@Slf4j
public class CompanyContactController {

    private final CompanyContactService companyContactService;

    /**
     * Creates a new contact for a company.
     */
    @PostMapping("/{companyId}")
    @Operation(summary = "Create company contact", description = "Creates a new contact for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> createCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId,
        @Valid @RequestBody CreateContactRequest request
    ) {
        log.info("Creating contact for company: {}", companyId);
        ContactDetailResponse response = companyContactService.createCompanyContact(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Company contact created successfully"));
    }

    /**
     * Gets a company's contact by company ID.
     */
    @GetMapping("/{companyId}")
    @Operation(summary = "Get company contact", description = "Gets the contact information for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> getCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId
    ) {
        log.info("Fetching contact for company: {}", companyId);
        ContactDetailResponse response = companyContactService.getCompanyContact(companyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a company's contact.
     */
    @PutMapping("/{companyId}")
    @Operation(summary = "Update company contact", description = "Updates the contact information for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> updateCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId,
        @Valid @RequestBody UpdateContactRequest request
    ) {
        log.info("Updating contact for company: {}", companyId);
        ContactDetailResponse response = companyContactService.updateCompanyContact(companyId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Company contact updated successfully"));
    }

    /**
     * Deletes a company's contact.
     */
    @DeleteMapping("/{companyId}")
    @Operation(summary = "Delete company contact", description = "Deletes the contact information for the specified company")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId
    ) {
        log.info("Deleting contact for company: {}", companyId);
        companyContactService.deleteCompanyContact(companyId);
        return ResponseEntity.ok(ApiResponse.success(null, "Company contact deleted successfully"));
    }

    /**
     * Gets all company contacts for the current tenant.
     */
    @GetMapping
    @Operation(summary = "List company contacts", description = "Gets all company contacts with pagination")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getCompanyContacts(
        @Parameter(description = "Tenant ID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
        @Parameter(description = "Sort by") @RequestParam(defaultValue = "companyName") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        log.info("Fetching company contacts - page: {}, size: {}", page, size);

        PageRequest pageRequest = PageRequest.of(page, size)
            .withSort(sortBy, sortDirection);

        PageResponse<ContactResponse> response = companyContactService.getCompanyContactsByTenant(
            tenantId != null ? tenantId : getCurrentTenantId(),
            pageRequest
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets company contacts by industry.
     */
    @GetMapping("/by-industry")
    @Operation(summary = "Get contacts by industry", description = "Gets company contacts filtered by industry")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> getCompanyContactsByIndustry(
        @Parameter(description = "Industry") @RequestParam String industry,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Fetching company contacts for industry: {}", industry);

        PageRequest pageRequest = PageRequest.of(page, size);
        PageResponse<ContactResponse> response = companyContactService.getCompanyContactsByIndustry(industry, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Searches company contacts.
     */
    @GetMapping("/search")
    @Operation(summary = "Search company contacts", description = "Searches company contacts by query")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactResponse>>> searchCompanyContacts(
        @Parameter(description = "Search query") @RequestParam String query,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    ) {
        log.info("Searching company contacts with query: {}", query);

        PageRequest pageRequest = PageRequest.of(page, size);
        PageResponse<ContactResponse> response = companyContactService.searchCompanyContacts(query, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates credit information for a company.
     */
    @PutMapping("/{companyId}/credit")
    @Operation(summary = "Update credit info", description = "Updates the credit information for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('FINANCE_ADMIN')")
    public ResponseEntity<ApiResponse<ContactDetailResponse>> updateCreditInfo(
        @PathVariable @Parameter(description = "Company ID") UUID companyId,
        @Parameter(description = "Credit limit") @RequestParam(required = false) Long creditLimit,
        @Parameter(description = "Payment terms") @RequestParam(required = false) String paymentTerms
    ) {
        log.info("Updating credit info for company: {}", companyId);
        ContactDetailResponse response = companyContactService.updateCreditInfo(companyId, creditLimit, paymentTerms);
        return ResponseEntity.ok(ApiResponse.success(response, "Credit information updated successfully"));
    }

    /**
     * Gets the current tenant ID from security context.
     * This is a placeholder - implement based on your security setup.
     */
    private UUID getCurrentTenantId() {
        // TODO: Get from security context
        return UUID.randomUUID();
    }
}