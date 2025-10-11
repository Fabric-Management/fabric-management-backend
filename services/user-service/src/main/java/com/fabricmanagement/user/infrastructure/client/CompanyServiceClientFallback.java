package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.user.infrastructure.client.dto.CreateCompanyDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Slf4j
public class CompanyServiceClientFallback implements CompanyServiceClient {

    @Override
    public ApiResponse<UUID> createCompany(CreateCompanyDto request) {
        log.error("⚠️ Fallback: {} - cannot create company: {}", 
            ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE, request.getName());
        return ApiResponse.error(ServiceConstants.MSG_COMPANY_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
    }
}

