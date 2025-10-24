package com.fabricmanagement.auth.infrastructure.client;

import com.fabricmanagement.shared.infrastructure.config.BaseFeignClientConfig;
import com.fabricmanagement.shared.application.response.ApiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Contact Service Client
 * 
 * Feign client for communication with Contact-Service
 * 
 * ✅ ZERO HARDCODED VALUES - BaseFeignClientConfig kullanıyor
 * ✅ PRODUCTION-READY - Circuit breaker, retry, timeout
 * ✅ INTERNAL ENDPOINT - Internal API Key authentication
 * ✅ JWT PROPAGATION - User context maintained
 */
@FeignClient(
    name = "contact-service",
    url = "${services.contact-service.url:http://localhost:8083}",
    configuration = BaseFeignClientConfig.class
)
public interface ContactServiceClient {
    
    /**
     * Get contact by ID
     */
    @GetMapping("/api/v1/contacts/{contactId}")
    ApiResponse<ContactResponse> getContact(@PathVariable UUID contactId);
    
    /**
     * Verify contact (email/phone)
     */
    @PostMapping("/api/v1/contacts/verify")
    ApiResponse<ContactVerificationResponse> verifyContact(
        @RequestBody VerifyContactRequest request
    );
    
    /**
     * Get contact by value (email/phone)
     */
    @GetMapping("/api/v1/contacts/by-value")
    ApiResponse<ContactResponse> getContactByValue(
        @RequestParam String contactValue,
        @RequestParam String contactType
    );
    
    /**
     * Update contact verification status
     */
    @PutMapping("/api/v1/contacts/{contactId}/verification")
    ApiResponse<Void> updateVerificationStatus(
        @PathVariable UUID contactId,
        @RequestBody UpdateVerificationRequest request
    );
    
    // =========================================================================
    // REQUEST/RESPONSE DTOs
    // =========================================================================
    
    class ContactResponse {
        public UUID id;
        public String contactValue;
        public String contactType;
        public Boolean isVerified;
        public UUID tenantId;
    }
    
    class ContactVerificationResponse {
        public UUID contactId;
        public Boolean isVerified;
        public String verificationCode;
        public String message;
    }
    
    class VerifyContactRequest {
        public String contactValue;
        public String contactType;
        public UUID tenantId;
    }
    
    class UpdateVerificationRequest {
        public Boolean isVerified;
        public String verificationCode;
        public String reason;
    }
}
