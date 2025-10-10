package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.domain.exception.UserNotFoundException;
import com.fabricmanagement.user.api.dto.CreateUserRequest;
import com.fabricmanagement.user.api.dto.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.messaging.UserEventPublisher;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User Service
 * 
 * Application service for user management operations.
 * Orchestrates business logic and delegates mapping to UserMapper.
 * 
 * Responsibilities:
 * - Transaction management
 * - Business logic orchestration
 * - Event publishing
 * - Validation coordination
 * 
 * Does NOT:
 * - Handle mapping (delegated to UserMapper)
 * - Handle HTTP concerns (delegated to Controllers)
 * - Contain domain logic (delegated to User aggregate)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserEventPublisher eventPublisher;
    private final UserSearchService userSearchService;
    
    /**
     * Gets a user by ID
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID tenantId) {
        log.debug("Getting user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        return userMapper.toResponse(user);
    }
    
    /**
     * Checks if a user exists
     */
    @Transactional(readOnly = true)
    public boolean userExists(UUID userId, UUID tenantId) {
        log.debug("Checking if user exists: {} for tenant: {}", userId, tenantId);
        
        return userRepository.findActiveByIdAndTenantId(userId, tenantId).isPresent();
    }
    
    /**
     * Gets users by company ID
     * 
     * Note: This currently returns all users for the tenant.
     * For accurate company-user relationships, use Company Service's API:
     * GET /api/v1/companies/{companyId}/users
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting users for company: {} and tenant: {}", companyId, tenantId);
        
        // Return all users for the tenant
        // Company-user relationship is managed by Company Service via company_users table
        List<User> users = userRepository.findByTenantId(tenantId);
        
        log.info("Found {} users for tenant {} (company filter not applied in User Service)", 
            users.size(), tenantId);
        
        return userMapper.toResponseList(users);
    }
    
    /**
     * Gets user count for a company
     * 
     * Note: This currently returns count for all tenant users.
     * For accurate company-user count, use Company Service's API:
     * GET /api/v1/companies/{companyId}/users/count
     */
    @Transactional(readOnly = true)
    public int getUserCountForCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting user count for company: {} and tenant: {}", companyId, tenantId);
        
        // Return tenant user count
        // Company-user relationship is managed by Company Service
        long count = userRepository.countActiveUsersByTenant(tenantId);
        
        log.info("Found {} active users for tenant {} (company filter not applied)", count, tenantId);
        
        return (int) count;
    }
    
    /**
     * Creates a new user
     */
    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        log.info("Creating user: {} for tenant: {}", request.getEmail(), tenantId);
        
        // Create user
        User user = User.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .displayName(request.getDisplayName())
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(request.getRole() != null ? request.getRole() : "USER")
                .preferences(request.getPreferences())
                .settings(request.getSettings())
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .deleted(false)
                .version(0L)
                // Policy-based authorization fields
                .companyId(request.getCompanyId() != null ? UUID.fromString(request.getCompanyId()) : null)
                .departmentId(request.getDepartmentId() != null ? UUID.fromString(request.getDepartmentId()) : null)
                .stationId(request.getStationId() != null ? UUID.fromString(request.getStationId()) : null)
                .jobTitle(request.getJobTitle())
                .userContext(request.getUserContext() != null ? 
                    com.fabricmanagement.shared.domain.policy.UserContext.valueOf(request.getUserContext()) : null)
                .build();
        
        user = userRepository.save(user);
        
        log.info("User created successfully: {}", user.getId());
        
        // Publish UserCreatedEvent to Kafka
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString()) // Kafka events use String
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(request.getEmail())
                .status(user.getStatus().name())
                .registrationType(user.getRegistrationType().name())
                .timestamp(LocalDateTime.now())
                .build();
        
        eventPublisher.publishUserCreated(event);
        
        return user.getId();
    }
    
    /**
     * Updates a user
     */
    @Transactional
    public void updateUser(UUID userId, UpdateUserRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        
        // Update fields
        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getDisplayName() != null) {
            user.setDisplayName(request.getDisplayName());
        }
        if (request.getRole() != null) {
            user.setRole(request.getRole());
        }
        if (request.getPreferences() != null) {
            user.setPreferences(request.getPreferences());
        }
        if (request.getSettings() != null) {
            user.setSettings(request.getSettings());
        }
        
        user.setUpdatedBy(updatedBy);
        user.setVersion(user.getVersion() + 1);
        
        userRepository.save(user);
        
        log.info("User updated successfully: {}", userId);
        
        // Publish UserUpdatedEvent to Kafka
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString()) // Kafka events use String
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .status(user.getStatus().name())
                .timestamp(LocalDateTime.now())
                .build();
        
        eventPublisher.publishUserUpdated(event);
    }
    
    /**
     * Deletes a user (soft delete)
     */
    @Transactional
    public void deleteUser(UUID userId, UUID tenantId, String deletedBy) {
        log.info("Deleting user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        
        user.setDeleted(true);
        user.setUpdatedBy(deletedBy);
        
        userRepository.save(user);
        
        log.info("User deleted successfully: {}", userId);
        
        // Publish UserDeletedEvent to Kafka
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId().toString()) // Kafka events use String
                .timestamp(LocalDateTime.now())
                .build();
        
        eventPublisher.publishUserDeleted(event);
    }
    
    /**
     * Lists all users for a tenant (non-paginated)
     * 
     * Note: For large datasets, use listUsersPaginated() instead
     * 
     * Performance: Uses optimized batch contact fetching (1 API call for all users)
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID tenantId) {
        log.debug("Listing users for tenant: {}", tenantId);
        
        List<User> users = userRepository.findByTenantId(tenantId);
        
        // Use optimized batch fetching (fixes N+1 query problem)
        return userMapper.toResponseListOptimized(users);
    }
    
    /**
     * Lists users for a tenant with pagination
     * 
     * Performance: Uses optimized batch contact fetching
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsersPaginated(UUID tenantId, Pageable pageable) {
        log.debug("Listing users for tenant: {} with pagination (page: {}, size: {})", 
                  tenantId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<User> userPage = userRepository.findByTenantIdPaginated(tenantId, pageable);
        
        // Use optimized batch contact fetching
        List<UserResponse> userResponses = userMapper.toResponseListOptimized(userPage.getContent());
        
        return PagedResponse.<UserResponse>builder()
                .content(userResponses)
                .page(userPage.getNumber())
                .size(userPage.getSize())
                .totalElements(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .first(userPage.isFirst())
                .last(userPage.isLast())
                .build();
    }
    
    /**
     * Searches users by criteria
     * Delegates to UserSearchService for better separation of concerns
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(UUID tenantId, String firstName, String lastName, 
                                         String email, String status) {
        return userSearchService.searchUsers(tenantId, firstName, lastName, email, status);
    }
    
    /**
     * Searches users by criteria with pagination
     * Delegates to UserSearchService for better separation of concerns
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsersPaginated(UUID tenantId, String firstName, 
                                                            String lastName, String status, 
                                                            Pageable pageable) {
        return userSearchService.searchUsersPaginated(tenantId, firstName, lastName, status, pageable);
    }
    
    /**
     * Helper method to find active user by ID and tenant ID
     * Throws UserNotFoundException if not found
     */
    private User findActiveUserOrThrow(UUID userId, UUID tenantId) {
        return userRepository.findActiveByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
    }
}

