package com.fabricmanagement.contact.infrastructure.web.controller;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.contact.application.dto.common.PageRequestDto;
import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.contact.application.dto.contact.request.CreateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateCompanyContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.CompanyContactResponse;
import com.fabricmanagement.contact.application.dto.contact.response.ContactListResponse;
import com.fabricmanagement.contact.application.service.CompanyContactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
@Validated
public class CompanyContactController {

    private final CompanyContactService companyContactService;

    /**
     * Creates a new contact for a company.
     */
    @PostMapping("/{companyId}")
    @Operation(summary = "Create company contact", description = "Creates a new contact for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<CompanyContactResponse>> createCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId,
        @Valid @RequestBody CreateCompanyContactRequest request
    ) {
        log.info("Creating contact for company: {}", companyId);
        CompanyContactResponse response = companyContactService.createCompanyContact(companyId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response, "Company contact created successfully"));
    }

    /**
     * Gets a company's contact by company ID.
     */
    @GetMapping("/{companyId}")
    @Operation(summary = "Get company contact", description = "Gets the contact information for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN') or hasRole('USER')")
    public ResponseEntity<ApiResponse<CompanyContactResponse>> getCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId
    ) {
        log.info("Fetching contact for company: {}", companyId);
        CompanyContactResponse response = companyContactService.getCompanyContact(companyId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Updates a company's contact.
     */
    @PutMapping("/{companyId}")
    @Operation(summary = "Update company contact", description = "Updates the contact information for the specified company")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<CompanyContactResponse>> updateCompanyContact(
        @PathVariable @Parameter(description = "Company ID") UUID companyId,
        @Valid @RequestBody UpdateCompanyContactRequest request
    ) {
        log.info("Updating contact for company: {}", companyId);
        CompanyContactResponse response = companyContactService.updateCompanyContact(companyId, request);
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
    public ResponseEntity<ApiResponse<PageResponse<ContactListResponse>>> getCompanyContacts(
        @Parameter(description = "Tenant ID") @RequestParam(required = false) UUID tenantId,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @Parameter(description = "Sort by") @RequestParam(defaultValue = "companyName") String sortBy,
        @Parameter(description = "Sort direction") @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        log.info("Fetching company contacts - page: {}, size: {}", page, size);

        PageRequestDto pageRequest = PageRequestDto.builder()
            .page(page)
            .size(size)
            .sortBy(sortBy)
            .sortDirection(sortDirection)
            .build();

        PageResponse<ContactListResponse> response = companyContactService.getCompanyContactsByTenant(
            tenantId != null ? tenantId : getCurrentTenantId(),
            pageRequest
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Searches company contacts by name or contact person.
     */
    @GetMapping("/search")
    @Operation(summary = "Search company contacts", description = "Searches company contacts by company name or contact person")
    @PreAuthorize("hasRole('ADMIN') or hasRole('COMPANY_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ContactListResponse>>> searchCompanyContacts(
        @Parameter(description = "Search query") @RequestParam String query,
        @Parameter(description = "Page number") @RequestParam(defaultValue = "0") @Min(0) int page,
        @Parameter(description = "Page size") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        log.info("Searching company contacts with query: {}", query);

        PageRequestDto pageRequest = PageRequestDto.builder().page(page).size(size).build();
        PageResponse<ContactListResponse> response = companyContactService.searchCompanyContacts(query, pageRequest);

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * Gets the current tenant ID from security context.
     */
    private UUID getCurrentTenantId() {
        return SecurityContextUtil.getCurrentTenantId();
    }
}