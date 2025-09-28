package com.fabricmanagement.company.infrastructure.integration.user;

import com.fabricmanagement.common.core.application.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Fallback implementation for UserServiceClient.
 * Provides graceful degradation when user-service is unavailable.
 */
@Component
@Slf4j
public class UserServiceFallback implements UserServiceClient {

    @Override
    public ApiResponse<UserResponse> getUserById(UUID userId) {
        log.warn("User service unavailable - using fallback for getUserById: {}", userId);
        return ApiResponse.<UserResponse>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<List<UserResponse>> getUsersByIds(List<UUID> userIds) {
        log.warn("User service unavailable - using fallback for getUsersByIds, count: {}",
            userIds != null ? userIds.size() : 0);
        return ApiResponse.<List<UserResponse>>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(Collections.emptyList())
            .build();
    }

    @Override
    public ApiResponse<List<UserResponse>> getUsersByCompanyId(UUID companyId) {
        log.warn("User service unavailable - using fallback for getUsersByCompanyId: {}", companyId);
        return ApiResponse.<List<UserResponse>>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(Collections.emptyList())
            .build();
    }

    @Override
    public ApiResponse<Boolean> userExists(UUID userId) {
        log.warn("User service unavailable - using fallback for userExists: {}", userId);
        return ApiResponse.<Boolean>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(false)
            .build();
    }

    @Override
    public ApiResponse<UserBasicResponse> getUserBasicInfo(UUID userId) {
        log.warn("User service unavailable - using fallback for getUserBasicInfo: {}", userId);
        return ApiResponse.<UserBasicResponse>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(null)
            .build();
    }

    @Override
    public ApiResponse<List<UserBasicResponse>> getUsersBasicInfo(List<UUID> userIds) {
        log.warn("User service unavailable - using fallback for getUsersBasicInfo, count: {}",
            userIds != null ? userIds.size() : 0);
        return ApiResponse.<List<UserBasicResponse>>builder()
            .success(false)
            .message("User service temporarily unavailable")
            .data(Collections.emptyList())
            .build();
    }
}