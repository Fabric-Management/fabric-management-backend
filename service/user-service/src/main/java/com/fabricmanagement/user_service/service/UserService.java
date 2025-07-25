package com.fabricmanagement.user_service.service;

import com.fabricmanagement.user_service.dto.request.*;
import com.fabricmanagement.user_service.dto.response.*;
import com.fabricmanagement.user_service.entity.User;
import com.fabricmanagement.user_service.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {

    // User CRUD operations
    UserResponse createUser(CreateUserRequest request, UUID createdBy);

    UserResponse getUserById(UUID id);

    User getUserEntityById(UUID id);

    UserResponse getUserByUsername(String username);

    UserResponse getUserByEmail(String email);

    UserResponse updateUser(UUID id, UpdateUserRequest request);

    void deleteUser(UUID id);

    void deleteUsers(List<UUID> ids);

    // User search and listing
    Page<UserSummaryResponse> searchUsers(UserSearchRequest request);

    Page<UserSummaryResponse> getUsersByCompany(UUID companyId, Pageable pageable);

    List<UserMinimalResponse> getActiveUsersByCompany(UUID companyId);

    // Password operations
    PasswordCreatedResponse createPassword(UUID userId, CreatePasswordRequest request);

    PasswordChangedResponse changePassword(UUID userId, ChangePasswordRequest request);

    void initiatePasswordReset(ResetPasswordRequest request);

    PasswordChangedResponse confirmPasswordReset(ConfirmResetPasswordRequest request);

    // Email verification
    void sendEmailVerification(UUID userId);

    EmailVerificationResponse verifyEmail(String token);

    // User status operations
    void activateUser(UUID id);

    void deactivateUser(UUID id);

    void suspendUser(UUID id);

    void unlockUser(UUID id);

    // Bulk operations
    BulkOperationResponse bulkOperation(BulkUserOperationRequest request);

    // Role operations
    void addRoles(UUID userId, List<Role> roles);

    void removeRoles(UUID userId, List<Role> roles);

    void setRoles(UUID userId, List<Role> roles);

    // Validation
    UserValidationResponse validateUser(String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    // Availability checks (new methods for thin controller)
    boolean isUsernameAvailable(String username);

    boolean isEmailAvailable(String email);

    // Statistics
    UserDashboardStats getDashboardStats(UUID companyId);

    CompanyUserStats getCompanyStats(UUID companyId);

    LoginActivityStats getLoginStats(UUID companyId);

    // Login tracking
    void recordSuccessfulLogin(UUID userId);

    void recordFailedLogin(String identifier);

    // Profile operations
    UserProfileResponse getCurrentUserProfile(UUID userId);

    UserResponse updateCurrentUserProfile(UUID userId, UpdateUserRequest request);
}