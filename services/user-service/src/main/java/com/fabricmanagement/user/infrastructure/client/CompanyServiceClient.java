package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.infrastructure.client.dto.CheckCompanyDuplicateDto;
import com.fabricmanagement.user.infrastructure.client.dto.CompanyDuplicateCheckResult;
import com.fabricmanagement.user.infrastructure.client.dto.CreateCompanyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
    name = "company-service",
    url = "${COMPANY_SERVICE_URL:http://localhost:8083}",
    path = "/api/v1/companies",
    configuration = com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig.class,
    fallback = CompanyServiceClientFallback.class
)
public interface CompanyServiceClient {

    @PostMapping
    ApiResponse<UUID> createCompany(@RequestBody CreateCompanyDto request);
    
    @PostMapping("/check-duplicate")
    ApiResponse<CompanyDuplicateCheckResult> checkDuplicate(@RequestBody CheckCompanyDuplicateDto request);
}

