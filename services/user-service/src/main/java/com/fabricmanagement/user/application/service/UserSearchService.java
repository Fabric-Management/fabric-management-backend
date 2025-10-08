package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * User Search Service
 * 
 * Dedicated service for user search and filtering operations.
 * Separated from UserService to follow Single Responsibility Principle.
 * 
 * Responsibilities:
 * - User search by criteria
 * - Advanced filtering
 * - Pagination support
 * 
 * Note: Basic CRUD operations remain in UserService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserSearchService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    
    /**
     * Searches users by criteria (non-paginated)
     * 
     * Note: For large datasets, use searchUsersPaginated() instead
     * 
     * Performance: Uses optimized batch contact fetching (1 API call for all users)
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(UUID tenantId, String firstName, String lastName, 
                                         String email, String status) {
        log.debug("Searching users with criteria for tenant: {}", tenantId);
        
        List<User> users = userRepository.findByTenantId(tenantId);
        
        // Apply in-memory filters
        if (firstName != null && !firstName.isEmpty()) {
            final String firstNameLower = firstName.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getFirstName().toLowerCase().contains(firstNameLower))
                    .toList();
        }
        
        if (lastName != null && !lastName.isEmpty()) {
            final String lastNameLower = lastName.toLowerCase();
            users = users.stream()
                    .filter(u -> u.getLastName().toLowerCase().contains(lastNameLower))
                    .toList();
        }
        
        // Note: Email search not implemented (see IMPROVEMENTS.md #1 and #2)
        
        if (status != null && !status.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getStatus().name().equals(status))
                    .toList();
        }
        
        // Use optimized batch fetching (fixes N+1 query problem)
        return userMapper.toResponseListOptimized(users);
    }
    
    /**
     * Searches users by criteria with pagination and database-level filtering
     * 
     * Uses database queries for better performance (no in-memory filtering)
     * Performance: Uses optimized batch contact fetching
     */
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsersPaginated(UUID tenantId, String firstName, 
                                                            String lastName, String status, 
                                                            Pageable pageable) {
        log.debug("Searching users for tenant {} with pagination (page: {}, size: {})", 
                  tenantId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<User> userPage = userRepository.searchUsersPaginated(
            tenantId, firstName, lastName, status, pageable
        );
        
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
     * Searches users by name (first or last name)
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchByName(UUID tenantId, String searchTerm) {
        log.debug("Searching users by name: {} for tenant: {}", searchTerm, tenantId);
        
        List<User> users = userRepository.searchByName(searchTerm);
        
        // Filter by tenant (repository returns all tenants)
        users = users.stream()
                .filter(u -> u.getTenantId().equals(tenantId))
                .toList();
        
        return userMapper.toResponseList(users);
    }
}

