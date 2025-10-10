package com.fabricmanagement.user.infrastructure.client;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.infrastructure.constants.ServiceConstants;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contact Service Client Fallback
 * 
 * Provides graceful degradation when Contact Service is unavailable.
 * 
 * ✅ RESILIENCE PATTERN:
 * - User operations continue even if Contact Service is down
 * - Login still works (returns user without contact enrichment)
 * - List users still works (without contact info)
 * 
 * ⚠️ IMPORTANT:
 * - Does NOT hide errors permanently
 * - Logs all fallback activations for monitoring
 * - Returns empty/null data instead of throwing exceptions
 */
@Component
@Slf4j
public class ContactServiceClientFallback implements ContactServiceClient {

    @Override
    public ApiResponse<List<ContactDto>> getContactsByOwner(UUID ownerId) {
        log.warn("⚠️ Fallback: {} - returning empty contacts for owner: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerId);
        return ApiResponse.success(Collections.emptyList(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<List<ContactDto>> getVerifiedContacts(UUID ownerId) {
        log.warn("⚠️ Fallback: {} - returning empty verified contacts for owner: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerId);
        return ApiResponse.success(Collections.emptyList(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<ContactDto> getPrimaryContact(UUID ownerId) {
        log.warn("⚠️ Fallback: {} - returning null primary contact for owner: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerId);
        return ApiResponse.success(null, ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<ContactDto> verifyContact(UUID contactId, String code) {
        log.error("⚠️ Fallback: {} - cannot verify contact: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactId);
        return ApiResponse.error(ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<ContactDto> makePrimary(UUID contactId) {
        log.warn("⚠️ Fallback: {} - cannot make contact primary: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactId);
        return ApiResponse.error(ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Void> deleteContact(UUID contactId) {
        log.warn("⚠️ Fallback: {} - cannot delete contact: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactId);
        return ApiResponse.error(ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Boolean> checkAvailability(String contactValue) {
        log.warn("⚠️ Fallback: {} - assuming contact value available: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactValue);
        // Optimistic: assume available (user can proceed with registration)
        return ApiResponse.success(true, ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Void> sendVerificationCode(UUID contactId) {
        log.error("⚠️ Fallback: {} - cannot send verification code for: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactId);
        return ApiResponse.error(ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, 
            SecurityConstants.ERROR_CODE_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<ContactDto> findByContactValue(String contactValue) {
        log.warn("⚠️ Fallback: {} - cannot find contact: {}", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, contactValue);
        // Return null - login will fail gracefully
        return ApiResponse.success(null, ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }

    @Override
    public ApiResponse<Map<String, List<ContactDto>>> getContactsByOwnersBatch(List<UUID> ownerIds) {
        log.warn("⚠️ Fallback: {} - returning empty batch for {} owners", 
            ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE, ownerIds.size());
        return ApiResponse.success(Collections.emptyMap(), ServiceConstants.MSG_CONTACT_SERVICE_UNAVAILABLE);
    }
}

