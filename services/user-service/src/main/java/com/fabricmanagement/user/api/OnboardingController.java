package com.fabricmanagement.user.api;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.dto.request.TenantRegistrationRequest;
import com.fabricmanagement.user.api.dto.response.TenantOnboardingResponse;
import com.fabricmanagement.user.application.service.TenantOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/public/onboarding")
@RequiredArgsConstructor
@Slf4j
public class OnboardingController {
    
    private final TenantOnboardingService onboardingService;
    
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<TenantOnboardingResponse>> registerTenant(
            @Valid @RequestBody TenantRegistrationRequest request) {
        
        log.info("Tenant registration request received for company: {}", request.getCompanyName());
        
        TenantOnboardingResponse response = onboardingService.registerTenant(request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Tenant registered successfully"));
    }
}

