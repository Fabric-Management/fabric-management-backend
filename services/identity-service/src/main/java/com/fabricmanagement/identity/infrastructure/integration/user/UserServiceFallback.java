package com.fabricmanagement.identity.infrastructure.integration.user;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single Responsibility: Fallback handling only
 * Open/Closed: Can be extended without modification
 * Liskov Substitution: Can substitute UserServiceClient
 */
@Component
@Slf4j
public class UserServiceFallback implements UserServiceClient {

    @Override
    public UserProfileResponse createUserProfile(CreateUserProfileRequest request) {
        log.error("Fallback for createUserProfile: User Service is unavailable or returned an error for user {}", request.id());
        // Return a minimal response indicating failure
        return new UserProfileResponse(request.id(), request.tenantId(), request.username(), request.email(), null, null, "ERROR");
    }

    @Override
    public UserProfileResponse updateUserProfileIdentity(String userId, UpdateUserProfileIdentityRequest request) {
        log.error("Fallback for updateUserProfileIdentity: User Service is unavailable or returned an error for user {}", userId);
        return new UserProfileResponse(userId, null, request.username(), request.email(), null, null, "ERROR");
    }

    @Override
    public UserProfileResponse updateUserProfileStatus(String userId, UpdateUserProfileStatusRequest request) {
        log.error("Fallback for updateUserProfileStatus: User Service is unavailable or returned an error for user {}", userId);
        return new UserProfileResponse(userId, null, null, null, null, null, "ERROR");
    }

    @Override
    public UserProfileResponse getUserProfile(String userId) {
        log.error("Fallback for getUserProfile: User Service is unavailable or returned an error for user {}", userId);
        return new UserProfileResponse(userId, null, null, null, null, null, "ERROR");
    }
}