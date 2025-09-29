package com.fabricmanagement.user.application.service;

import com.fabricmanagement.common.core.application.dto.PageResponse;
import com.fabricmanagement.common.core.domain.exception.EntityNotFoundException;
import com.fabricmanagement.common.security.context.SecurityContextUtil;
import com.fabricmanagement.user.application.dto.user.request.CreateUserRequest;
import com.fabricmanagement.user.application.dto.user.request.UpdateUserRequest;
import com.fabricmanagement.user.application.dto.user.response.UserDetailResponse;
import com.fabricmanagement.user.application.dto.user.response.UserResponse;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import com.fabricmanagement.user.domain.repository.UserRepository;
import com.fabricmanagement.user.infrastructure.messaging.publisher.UserEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Application service for user profile management operations.
 * Orchestrates business logic and coordinates between domain and infrastructure layers.
 * 
 * Responsibilities:
 * - User profile CRUD operations
 * - User status management
 * - User search and filtering
 * 
 * NOT responsible for:
 * - Authentication (handled by Identity Service)
 * - Contact information (handled by Contact Service)
 * - Username/email validation (handled by Identity Service)
 */
@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class UserApplicationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserEventPublisher eventPublisher;

    /**
     * Creates a new user profile.
     * Identity validation is handled by Identity Service.
     */
    public UserDetailResponse createUser(CreateUserRequest request) {
        log.info("Creating new user profile for identity: {} with name: {} {}", 
                request.identityId(), request.firstName(), request.lastName());

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();

        // Check if user profile already exists for this identity
        if (userRepository.existsByIdentityIdAndTenantId(request.identityId(), tenantId)) {
            throw new IllegalArgumentException("User profile already exists for identity: " + request.identityId());
        }

        User user = userMapper.toDomain(request);
        user.setTenantId(tenantId);
        user.setIdentityId(request.identityId());
        user.setStatus(UserStatus.ACTIVE);

        User savedUser = userRepository.save(user);
        eventPublisher.publishUserCreatedEvent(savedUser);

        log.info("User profile created successfully with ID: {}", savedUser.getId());
        return userMapper.toDetailResponse(savedUser);
    }

    /**
     * Gets a user by ID.
     */
    @Transactional(readOnly = true)
    public UserDetailResponse getUserById(UUID userId) {
        log.debug("Fetching user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        return userMapper.toDetailResponse(user);
    }

    /**
     * Updates an existing user.
     */
    public UserDetailResponse updateUser(UUID userId, UpdateUserRequest request) {
        log.info("Updating user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        User existingUser = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        userMapper.updateDomainFromRequest(request, existingUser);
        User updatedUser = userRepository.save(existingUser);
        eventPublisher.publishUserUpdatedEvent(updatedUser);

        log.info("User updated successfully with ID: {}", userId);
        return userMapper.toDetailResponse(updatedUser);
    }

    /**
     * Deactivates a user (soft delete).
     */
    public void deactivateUser(UUID userId) {
        log.info("Deactivating user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        user.deactivate();
        userRepository.save(user);
        eventPublisher.publishUserDeletedEvent(user);

        log.info("User deactivated successfully with ID: {}", userId);
    }

    /**
     * Permanently deletes a user (sets deleted flag).
     */
    public void deleteUser(UUID userId) {
        log.info("Deleting user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        userRepository.deleteByIdAndTenantId(userId, tenantId);

        log.info("User deleted successfully with ID: {}", userId);
    }

    /**
     * Gets all users for the current tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(int page, int size, String sortBy, String sortDirection) {
        log.debug("Fetching users - page: {}, size: {}, sort: {} {}", page, size, sortBy, sortDirection);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> userPage = userRepository.findByTenantId(tenantId, pageable);
        List<UserResponse> userResponses = userPage.getContent().stream()
            .map(userMapper::toResponse)
            .toList();

        return PageResponse.<UserResponse>builder()
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
     * Gets active users for the current tenant with pagination.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getActiveUsers(int page, int size, String sortBy, String sortDirection) {
        log.debug("Fetching active users - page: {}, size: {}, sort: {} {}", page, size, sortBy, sortDirection);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Sort sort = Sort.by(Sort.Direction.fromString(sortDirection), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<User> userPage = userRepository.findActiveUsersByTenantId(tenantId, pageable);
        List<UserResponse> userResponses = userPage.getContent().stream()
            .map(userMapper::toResponse)
            .toList();

        return PageResponse.<UserResponse>builder()
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
     * Searches users by name or job title.
     */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> searchUsers(String searchQuery, int page, int size) {
        log.debug("Searching users with query: {}", searchQuery);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("firstName", "lastName"));

        Page<User> userPage = userRepository.searchUsers(tenantId, searchQuery, pageable);
        List<UserResponse> userResponses = userPage.getContent().stream()
            .map(userMapper::toResponse)
            .toList();

        return PageResponse.<UserResponse>builder()
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
     * Gets users by department.
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByDepartment(String department) {
        log.debug("Fetching users in department: {}", department);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        return userRepository.findByTenantIdAndDepartment(tenantId, department)
            .stream()
            .map(userMapper::toResponse)
            .toList();
    }

    /**
     * Activates a user.
     */
    public UserDetailResponse activateUser(UUID userId) {
        log.info("Activating user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        user.activate();
        User activatedUser = userRepository.save(user);

        log.info("User activated successfully with ID: {}", userId);
        return userMapper.toDetailResponse(activatedUser);
    }

    /**
     * Suspends a user.
     */
    public UserDetailResponse suspendUser(UUID userId) {
        log.info("Suspending user with ID: {}", userId);

        UUID tenantId = SecurityContextUtil.getCurrentTenantId();
        User user = userRepository.findByIdAndTenantId(userId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));

        user.suspend();
        User suspendedUser = userRepository.save(user);

        log.info("User suspended successfully with ID: {}", userId);
        return userMapper.toDetailResponse(suspendedUser);
    }
}

