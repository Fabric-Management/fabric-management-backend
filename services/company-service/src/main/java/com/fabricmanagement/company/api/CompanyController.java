package com.fabricmanagement.company.api;

import com.fabricmanagement.company.api.dto.request.*;
import com.fabricmanagement.company.api.dto.response.*;
import com.fabricmanagement.company.application.service.CompanyService;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.infrastructure.config.DuplicateDetectionConfig;
import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<UUID>> createCompany(
            @Valid @RequestBody CreateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Creating company: {}", request.getName());
        UUID companyId = companyService.createCompany(request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(companyId, "Company created successfully"));
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
            @AuthenticationPrincipal SecurityContext ctx) {
        List<CompanyResponse> companies = companyService.listCompanies(ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @PutMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompany(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanyRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company: {}", companyId);
        companyService.updateCompany(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company updated successfully"));
    }
    
    @DeleteMapping("/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Deleting company: {}", companyId);
        companyService.deleteCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company deleted successfully"));
    }
    
    @PostMapping("/{companyId}/activate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> activateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Activating company: {}", companyId);
        companyService.activateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company activated successfully"));
    }
    
    @PostMapping("/{companyId}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(
            @PathVariable UUID companyId,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Deactivating company: {}", companyId);
        companyService.deactivateCompany(companyId, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company deactivated successfully"));
    }
    
    @GetMapping("/status/{status}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> getCompaniesByStatus(
            @PathVariable CompanyStatus status,
            @AuthenticationPrincipal SecurityContext ctx) {
        List<CompanyResponse> companies = companyService.getCompaniesByStatus(status, ctx.getTenantId());
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<CompanyResponse>>> searchCompanies(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String industry,
            @RequestParam(required = false) String companyType,
            @AuthenticationPrincipal SecurityContext ctx) {
        List<CompanyResponse> companies = companyService.searchCompanies(ctx.getTenantId(), name);
        return ResponseEntity.ok(ApiResponse.success(companies));
    }
    
    @PutMapping("/{companyId}/settings")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY_MANAGER')")
    public ResponseEntity<ApiResponse<Void>> updateCompanySettings(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateCompanySettingsRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company settings: {}", companyId);
        companyService.updateSettings(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company settings updated successfully"));
    }
    
    @PutMapping("/{companyId}/subscription")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> updateSubscription(
            @PathVariable UUID companyId,
            @Valid @RequestBody UpdateSubscriptionRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
        log.info("Updating company subscription: {}", companyId);
        companyService.updateSubscription(companyId, request, ctx.getTenantId(), ctx.getUserId());
        return ResponseEntity.ok(ApiResponse.success(null, "Company subscription updated successfully"));
    }
    
    @PostMapping("/check-duplicate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<CheckDuplicateResponse>> checkDuplicate(
            @Valid @RequestBody CheckDuplicateRequest request,
            @AuthenticationPrincipal SecurityContext ctx) {
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
}
