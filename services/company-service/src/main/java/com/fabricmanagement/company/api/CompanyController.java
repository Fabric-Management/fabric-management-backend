package com.fabricmanagement.company.api;

import com.fabricmanagement.company.api.dto.request.*;
import com.fabricmanagement.company.api.dto.response.*;
import com.fabricmanagement.company.api.dto.response.CompanySimilarResponse;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.SecurityRoles;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.security.annotation.InternalEndpoint;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {
    
    private final CompanyService companyService;
    private final DuplicateDetectionConfig duplicateConfig;
    
    /**
     * Create new company
     * 
     * Dual mode endpoint:
     * 1. Internal call (onboarding flow) - No JWT, tenantId in request body
     * 2. Authenticated call (normal flow) - JWT required, tenantId from SecurityContext
     * 
     * Security: @InternalEndpoint allows internal calls with X-Internal-API-Key
     */
    @InternalEndpoint(
        description = "Create company during tenant onboarding or by authenticated admin",
        calledBy = {"user-service"},
        critical = true
    )
    @PostMapping
    public ResponseEntity<ApiResponse<UUID>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Creating company: {}", request.getName());
        
        // Support both authenticated calls (with JWT) and internal service calls (onboarding)
        UUID tenantId;
        String createdBy;
        
        if (request.getTenantId() != null) {
            // Internal service-to-service call (onboarding flow)
            tenantId = request.getTenantId();  // Already UUID type
            createdBy = request.getCreatedBy() != null 
                ? request.getCreatedBy() 
                : ServiceConstants.AUDIT_SYSTEM_USER;
            log.debug("Creating company via internal call. Tenant: {}, CreatedBy: {}", tenantId, createdBy);
        } else if (ctx != null) {
            // Authenticated call (with JWT) - require proper role
            if (!ctx.hasAnyRole(SecurityRoles.TENANT_ADMIN, SecurityRoles.COMPANY_MANAGER)) {
                log.warn("User {} attempted to create company without proper role", ctx.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ApiResponse.error(SecurityConstants.MSG_INSUFFICIENT_PERMISSIONS));
            }
            tenantId = ctx.getTenantId();
            createdBy = ctx.getUserId().toString();
            log.debug("Creating company via authenticated call. Tenant: {}, User: {}", tenantId, createdBy);
        } else {
            log.error("Cannot create company: No authentication context and no tenantId in request");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(SecurityConstants.MSG_UNAUTHORIZED_ACCESS));
        }
        
        UUID companyId = companyService.createCompany(request, tenantId, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(companyId, ServiceConstants.MSG_COMPANY_CREATED));
    }
    
    @GetMapping("/{companyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyResponse>> getCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        CompanyResponse company = companyService.getCompany(companyId, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(company));
    }
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> listCompanies(
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(required = false) Boolean paginated,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        // If pagination requested, use paginated endpoint
        if (Boolean.TRUE.equals(paginated)) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            PagedResponse<CompanyResponse> pagedResult = companyService.listCompaniesPaginated(ctx.getTenantId(), pageable);
            
            // Return paged response directly (it extends API response structure)
            return ResponseEntity.ok()
                    .body(ApiResponse.success(pagedResult.getContent()));
        }
        
        // Default: return all companies (backward compatible)
        List<CompanyResponse> companies = companyService.listCompanies(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @GetMapping("/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<CompanyResponse>> listCompaniesPaginated(
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        PagedResponse<CompanyResponse> result = companyService.listCompaniesPaginated(ctx.getTenantId(), pageable);
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company: {}", companyId);
        companyService.updateCompany(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_UPDATED));
    }
    
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Deleting company: {}", companyId);
        companyService.deleteCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_DELETED));
    }
    
    @PostMapping("/{companyId}/activate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Activating company: {}", companyId);
        companyService.activateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_ACTIVATED));
    }
    
    @PostMapping("/{companyId}/deactivate")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Deactivating company: {}", companyId);
        companyService.deactivateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_DEACTIVATED));
    }
    
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getCompaniesByStatus(
            @PathVariable CompanyStatus status,
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(required = false) Boolean paginated,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        // If pagination requested
        if (Boolean.TRUE.equals(paginated)) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            PagedResponse<CompanyResponse> pagedResult = companyService.getCompaniesByStatusPaginated(status, ctx.getTenantId(), pageable);
            return ResponseEntity.ok(ApiResponse.success(pagedResult.getContent()));
        }
        
        // Default: return all
        List<CompanyResponse> companies = companyService.getCompaniesByStatus(status, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @GetMapping("/status/{status}/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<CompanyResponse>> getCompaniesByStatusPaginated(
            @PathVariable CompanyStatus status,
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        PagedResponse<CompanyResponse> result = companyService.getCompaniesByStatusPaginated(status, ctx.getTenantId(), pageable);
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> searchCompanies(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String companyType,
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(required = false) Boolean paginated,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        // If pagination requested
        if (Boolean.TRUE.equals(paginated)) {
            Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
                ? Sort.Direction.DESC : Sort.Direction.ASC;
            Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
            
            PagedResponse<CompanyResponse> pagedResult = companyService.searchCompaniesPaginated(ctx.getTenantId(), name, pageable);
            return ResponseEntity.ok(ApiResponse.success(pagedResult.getContent()));
        }
        
        // Default: return all
        List<CompanyResponse> companies = companyService.searchCompanies(ctx.getTenantId(), name);
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @GetMapping("/search/paginated")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PagedResponse<CompanyResponse>> searchCompaniesPaginated(
            @RequestParam(required = false) String name,
            @AuthenticationPrincipal SecurityContext ctx,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection) {
        
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) 
            ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        PagedResponse<CompanyResponse> result = companyService.searchCompaniesPaginated(ctx.getTenantId(), name, pageable);
        return ResponseEntity.ok(result);
    }
    
    @PutMapping("/{companyId}/settings")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompanySettings(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanySettingsRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company settings: {}", companyId);
        companyService.updateSettings(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_SETTINGS_UPDATED));
    }
    
    @PutMapping("/{companyId}/subscription")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSubscription(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company subscription: {}", companyId);
        companyService.updateSubscription(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, ServiceConstants.MSG_COMPANY_SUBSCRIPTION_UPDATED));
    }
    
    @InternalEndpoint(
        description = "Check company duplicate during tenant onboarding validation",
        calledBy = {"user-service"},
        critical = true
    )
    @PostMapping("/check-duplicate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CheckDuplicateResponse>> checkDuplicate(
            @Valid @RequestBody CheckDuplicateRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        // If internal service call, check globally (for tenant onboarding)
        if (SecurityConstants.INTERNAL_SERVICE_PRINCIPAL.equals(ctx.getUserId())) {
            log.info("Internal call - checking duplicate globally");
            CheckDuplicateResponse result = companyService.checkDuplicateGlobal(request);
            
            if (result.isDuplicate()) {
                log.warn("GLOBAL duplicate found: {} (type: {}, confidence: {}%)", 
                    result.getMatchedCompanyName(), result.getMatchType(), (int)(result.getConfidence() * 100));
            }
            
            return ResponseEntity.ok(ApiResponse.success(result));
        }
        
        // Otherwise, tenant-scoped check
        CheckDuplicateResponse result = companyService.checkDuplicate(request, ctx.getTenantId());
        
        if (result.isDuplicate()) {
            log.warn("Potential duplicate found: {} (confidence: {}%)", 
                result.getMatchedCompanyName(), (int)(result.getConfidence() * 100));
        }
        
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/autocomplete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanyAutocompleteResponse>> autocomplete(
            @RequestParam("q") String query,
            @AuthenticationPrincipal SecurityContext ctx) {
        int minLength = duplicateConfig.getAutocompleteMinLength();
        if (query == null || query.trim().length() < minLength) {
            return ResponseEntity.ok(ApiResponse.success(
                CompanyAutocompleteResponse.builder()
                    .suggestions(List.of())
                    .totalCount(0)
                    .build(),
                String.format("Query too short (minimum %d characters)", minLength)
            ));
        }
        
        CompanyAutocompleteResponse result = companyService.autocomplete(query.trim(), ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
    
    @GetMapping("/similar")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CompanySimilarResponse>> findSimilar(
            @RequestParam("name") String name,
            @RequestParam(value = "threshold", required = false, defaultValue = "0.3") Double threshold,
            @AuthenticationPrincipal SecurityContext ctx) {
        
        log.debug("Finding similar companies for: {} with threshold: {}", name, threshold);
        
        int minLength = duplicateConfig.getFuzzySearchMinLength();
        if (name == null || name.trim().length() < minLength) {
            return ResponseEntity.ok(ApiResponse.success(
                CompanySimilarResponse.builder()
                    .matches(List.of())
                    .totalCount(0)
                    .threshold(threshold)
                    .build(),
                String.format("Query too short (minimum %d characters)", minLength)
            ));
        }
        
        CompanySimilarResponse result = companyService.findSimilar(name.trim(), threshold, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
