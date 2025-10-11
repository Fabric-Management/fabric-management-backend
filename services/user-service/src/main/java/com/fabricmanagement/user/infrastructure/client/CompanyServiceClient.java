package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.infrastructure.client.dto.CreateCompanyDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.UUID;

@FeignClient(
    name = "company-service",
    url = "${company-service.url:http://localhost:8081}",
    path = "/api/v1/companies",
    configuration = com.fabricmanagement.user.infrastructure.config.FeignClientConfig.class,
    fallback = CompanyServiceClientFallback.class
)
public interface CompanyServiceClient {

    @PostMapping
    ApiResponse<UUID> createCompany(@RequestBody CreateCompanyDto request);
}

