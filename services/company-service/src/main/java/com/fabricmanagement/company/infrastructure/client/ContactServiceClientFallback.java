package com.fabricmanagement.company.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.company.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.company.infrastructure.client.dto.AddressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contact Service Client Fallback for Company Service
 * 
 * Provides graceful degradation when Contact Service is unavailable.
 * 
 * ✅ RESILIENCE PATTERN:
 * - Company operations continue even if Contact Service is down
 * - List companies still works (without contact enrichment)
 * - Create company still works
 * 
 * ⚠️ MONITORING:
 * - All fallback activations are logged
 * - Alerts should be configured for repeated fallbacks
 * 
 * Pattern: @Lazy to prevent eager initialization during FeignClient setup
 */
@Component
@Lazy  // ✅ Lazy initialization to prevent circular dependency
@Slf4j
public class ContactServiceClientFallback implements ContactServiceClient {

    @Override
    public ApiResponse<List<ContactDto>> getContactsByOwner(UUID ownerId, String ownerType) {
        log.warn("⚠️ Fallback: {} - returning empty contacts for {} owner: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerType, ownerId);
        return ApiResponse.success(Collections.emptyList(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<ContactDto> getPrimaryContact(UUID ownerId, String ownerType, String contactMedium) {
        log.warn("⚠️ Fallback: {} - returning null primary {} for {} owner: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactMedium, ownerType, ownerId);
        return ApiResponse.success(null, ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Map<String, List<ContactDto>>> getContactsByOwnersBatch(List<UUID> ownerIds, String ownerType) {
        log.warn("⚠️ Fallback: {} - returning empty batch for {} {} owners", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerIds.size(), ownerType);
        return ApiResponse.success(Collections.emptyMap(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }
    
    @Override
    public ApiResponse<List<AddressDto>> getAddressesByOwner(UUID ownerId, String ownerType) {
        log.warn("⚠️ Fallback: {} - returning empty addresses for company: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerId);
        return ApiResponse.success(Collections.emptyList(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }
}

