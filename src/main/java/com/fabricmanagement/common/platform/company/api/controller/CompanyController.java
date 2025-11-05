package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.app.CompanyService;
import com.fabricmanagement.common.platform.company.app.SubscriptionQuotaService;
import com.fabricmanagement.common.platform.company.app.SubscriptionService;
import com.fabricmanagement.common.platform.company.domain.CompanyCategory;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
import com.fabricmanagement.common.platform.company.dto.CompanyTypeDto;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.CreateCompanyWithContactRequest;
import com.fabricmanagement.common.platform.company.dto.SubscriptionDto;
import com.fabricmanagement.common.platform.company.dto.SubscriptionQuotaDto;
import com.fabricmanagement.common.platform.company.dto.UpdateCompanyRequest;
import com.fabricmanagement.common.platform.company.dto.UpdateSubscriptionRequest;
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
    private final SubscriptionQuotaService quotaService;

    @PostMapping
    public ResponseEntity<ApiResponse<CompanyDto>> createCompany(@Valid @RequestBody CreateCompanyRequest request) {
        log.info("Creating company: {}", request.getCompanyName());

        CompanyDto created = companyService.createCompany(request);

        return ResponseEntity.ok(ApiResponse.success(created, "Company created successfully"));
    }

    /**
     * Orchestration endpoint: Create company with contact and address in single transaction.
     * 
     * <p>This endpoint creates company, contact (email/phone), and address in one atomic operation.
     * If any step fails, entire transaction rolls back.</p>
     * 
     * <p>Use this endpoint when you want to create company with communication information in one request.</p>
     * 
     * <p>Example request:
     * <pre>
     * {
     *   "companyName": "ACME Corp",
     *   "taxId": "1234567890",
     *   "companyType": "MANUFACTURER",
     *   "email": "info@acme.com",
     *   "phoneNumber": "+905551234567",
     *   "address": "Ataturk Cad. No:1",
     *   "city": "Istanbul",
     *   "country": "Turkey"
     * }
     * </pre>
     */
    @PostMapping("/with-contact")
    public ResponseEntity<ApiResponse<CompanyDto>> createCompanyWithContact(
            @Valid @RequestBody CreateCompanyWithContactRequest request) {
        log.info("Creating company with contact/address: {}", request.getCompanyName());

        CompanyDto created = companyService.createCompanyWithContact(request);

        return ResponseEntity.ok(ApiResponse.success(created, 
            "Company with contact and address created successfully"));
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

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CompanyDto>> updateCompany(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCompanyRequest request) {
        log.info("Updating company: id={}", id);

        CompanyDto updated = companyService.updateCompany(id, request);

        return ResponseEntity.ok(ApiResponse.success(updated, "Company updated successfully"));
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

    // =========================================================================
    // SUBSCRIPTION MANAGEMENT ENDPOINTS
    // =========================================================================

    /**
     * Get active subscriptions for current tenant.
     */
    @GetMapping("/subscriptions/active")
    public ResponseEntity<ApiResponse<List<SubscriptionDto>>> getActiveSubscriptions() {
        log.debug("Getting active subscriptions");

        List<SubscriptionDto> subscriptions = subscriptionService.getActiveSubscriptions();

        return ResponseEntity.ok(ApiResponse.success(subscriptions));
    }

    /**
     * Get subscription by ID.
     */
    @GetMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getSubscription(@PathVariable UUID subscriptionId) {
        log.debug("Getting subscription: subscriptionId={}", subscriptionId);

        SubscriptionDto subscription = subscriptionService.getSubscription(subscriptionId);

        return ResponseEntity.ok(ApiResponse.success(subscription));
    }

    /**
     * Update subscription (expiry date, features, pricing tier).
     */
    @PutMapping("/subscriptions/{subscriptionId}")
    public ResponseEntity<ApiResponse<SubscriptionDto>> updateSubscription(
            @PathVariable UUID subscriptionId,
            @Valid @RequestBody UpdateSubscriptionRequest request) {
        log.info("Updating subscription: subscriptionId={}", subscriptionId);

        SubscriptionDto updated = subscriptionService.updateSubscription(subscriptionId, request);

        return ResponseEntity.ok(ApiResponse.success(updated, "Subscription updated successfully"));
    }

    /**
     * Cancel subscription.
     */
    @PostMapping("/subscriptions/{subscriptionId}/cancel")
    public ResponseEntity<ApiResponse<SubscriptionDto>> cancelSubscription(@PathVariable UUID subscriptionId) {
        log.info("Cancelling subscription: subscriptionId={}", subscriptionId);

        SubscriptionDto cancelled = subscriptionService.cancelSubscription(subscriptionId);

        return ResponseEntity.ok(ApiResponse.success(cancelled, "Subscription cancelled successfully"));
    }

    /**
     * Get quotas for a subscription.
     */
    @GetMapping("/subscriptions/{subscriptionId}/quotas")
    public ResponseEntity<ApiResponse<List<SubscriptionQuotaDto>>> getSubscriptionQuotas(
            @PathVariable UUID subscriptionId) {
        log.debug("Getting quotas for subscription: subscriptionId={}", subscriptionId);

        List<SubscriptionQuotaDto> quotas = quotaService.getSubscriptionQuotas(subscriptionId);

        return ResponseEntity.ok(ApiResponse.success(quotas));
    }

    /**
     * Get all quotas for current tenant.
     */
    @GetMapping("/subscriptions/quotas")
    public ResponseEntity<ApiResponse<List<SubscriptionQuotaDto>>> getTenantQuotas() {
        log.debug("Getting quotas for current tenant");

        List<SubscriptionQuotaDto> quotas = quotaService.getTenantQuotas();

        return ResponseEntity.ok(ApiResponse.success(quotas));
    }

    /**
     * Reset quota for a specific subscription and quota type.
     */
    @PutMapping("/subscriptions/{subscriptionId}/quotas/{quotaType}/reset")
    public ResponseEntity<ApiResponse<SubscriptionQuotaDto>> resetQuota(
            @PathVariable UUID subscriptionId,
            @PathVariable String quotaType) {
        log.info("Resetting quota: subscriptionId={}, quotaType={}", subscriptionId, quotaType);

        SubscriptionQuotaDto reset = quotaService.resetQuota(subscriptionId, quotaType);

        return ResponseEntity.ok(ApiResponse.success(reset, "Quota reset successfully"));
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

