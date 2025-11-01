package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.app.CompanyService;
import com.fabricmanagement.common.platform.company.app.SubscriptionService;
import com.fabricmanagement.common.platform.company.domain.CompanyCategory;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CompanyTypeDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.SubscriptionDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/common/companies")
@RequiredArgsConstructor
@Slf4j
public class CompanyController {

    private final CompanyService companyService;
    private final SubscriptionService subscriptionService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyDto>> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getCompanyName());

        CompanyDto created = companyService.createCompany(request);

        return ResponseEntity.ok(ApiResponse.success(created, "Company created successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyDto>> getCompany(@PathVariable UUID id) {
        log.debug("Getting company: id={}", id);

        CompanyDto company = companyService.getCompany(id);

        return ResponseEntity.ok(ApiResponse.success(company));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CompanyDto>>> getAllCompanies() {
        log.debug("Getting all companies");

        List<CompanyDto> companies = companyService.getAllCompanies();

        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @GetMapping("/tenants")
    public ResponseEntity<ApiResponse<List<CompanyDto>>> getTenantCompanies() {
        log.debug("Getting tenant companies only");

        List<CompanyDto> tenants = companyService.getTenantCompanies();

        return ResponseEntity.ok(ApiResponse.success(tenants, 
            "Found " + tenants.size() + " tenant companies"));
    }

    @GetMapping("/type/{type}")
    public ResponseEntity<ApiResponse<List<CompanyDto>>> getCompaniesByType(@PathVariable String type) {
        log.debug("Getting companies by type: {}", type);

        CompanyType companyType = CompanyType.valueOf(type.toUpperCase());
        List<CompanyDto> companies = companyService.getCompaniesByType(companyType);

        return ResponseEntity.ok(ApiResponse.success(companies));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deactivateCompany(@PathVariable UUID id) {
        log.info("Deactivating company: id={}", id);

        companyService.deactivateCompany(id);

        return ResponseEntity.ok(ApiResponse.success(null, "Company deactivated successfully"));
    }

    @GetMapping("/{id}/subscriptions")
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getCompanySubscriptions(@PathVariable UUID id) {
        log.debug("Getting subscriptions for company: id={}", id);

        List<SubscriptionDto> subscriptions = subscriptionService.getCompanySubscriptions(id);

        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    @PostMapping("/{id}/subscriptions/{subscriptionId}/activate")
    public ResponseEntity<ApiResponse<SubscriptionDto>> activateSubscription(
            @PathVariable UUID id,
            @PathVariable UUID subscriptionId) {
        log.info("Activating subscription: companyId={}, subscriptionId={}", id, subscriptionId);

        SubscriptionDto activated = subscriptionService.activateSubscription(subscriptionId);

        return ResponseEntity.ok(ApiResponse.success(activated, "Subscription activated successfully"));
    }

    /**
     * Get all company types (for dropdowns, forms, etc.).
     * 
     * <p><b>Public endpoint</b> - No authentication required (useful for signup forms)</p>
     * 
     * <p>Returns all company types with metadata (category, tenant status, suggested OS).</p>
     */
    @GetMapping("/types")
    public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getCompanyTypes() {
        log.debug("Getting all company types");

        List<CompanyTypeDto> types = List.of(CompanyType.values())
            .stream()
            .map(CompanyTypeDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(types));
    }

    /**
     * Get tenant company types only (for self-service signup).
     * 
     * <p><b>Public endpoint</b> - No authentication required</p>
     * 
     * <p>Returns only company types that can be platform tenants.
     * Perfect for signup forms where users select their company type.</p>
     * 
     * @return List of tenant company types (6 types)
     */
    @GetMapping("/types/tenant")
    public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getTenantCompanyTypes() {
        log.debug("Getting tenant company types only");

        List<CompanyTypeDto> tenantTypes = List.of(CompanyType.values())
            .stream()
            .filter(CompanyType::isTenant)
            .map(CompanyTypeDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(tenantTypes, 
            "Found " + tenantTypes.size() + " tenant company types"));
    }

    /**
     * Get company types by category.
     * 
     * <p><b>Public endpoint</b> - No authentication required</p>
     * 
     * <p>Filter company types by category (TENANT, SUPPLIER, SERVICE_PROVIDER, PARTNER, CUSTOMER).</p>
     * 
     * @param category Company category
     * @return List of company types in the specified category
     */
    @GetMapping("/types/category/{category}")
    public ResponseEntity<ApiResponse<List<CompanyTypeDto>>> getCompanyTypesByCategory(
            @PathVariable String category) {
        log.debug("Getting company types by category: {}", category);

        CompanyCategory companyCategory = CompanyCategory.valueOf(category.toUpperCase());
        
        List<CompanyTypeDto> types = List.of(CompanyType.values())
            .stream()
            .filter(type -> type.getCategory() == companyCategory)
            .map(CompanyTypeDto::from)
            .toList();

        return ResponseEntity.ok(ApiResponse.success(types, 
            "Found " + types.size() + " company types in category " + category));
    }
}

