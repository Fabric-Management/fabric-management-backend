package com.fabricmanagement.common.platform.company.api.controller;

import com.fabricmanagement.common.infrastructure.web.ApiResponse;
import com.fabricmanagement.common.platform.company.app.CompanyService;
import com.fabricmanagement.common.platform.company.app.SubscriptionService;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import com.fabricmanagement.common.platform.company.dto.CompanyDto;
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
}

