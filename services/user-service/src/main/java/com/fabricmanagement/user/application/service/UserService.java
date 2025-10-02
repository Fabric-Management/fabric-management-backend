package com.fabricmanagement.user.application.service;

import com.fabricmanagement.user.api.dto.CreateUserRequest;
import com.fabricmanagement.user.api.dto.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.messaging.UserEventPublisher;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Service
 * 
 * Application service for user management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final ContactServiceClient contactServiceClient;
    private final UserEventPublisher eventPublisher;
    
    /**
     * Gets a user by ID
     */
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID tenantId) {
        log.debug("Getting user: {} for tenant: {}", userId, tenantId);
        
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .filter(u -> u.getTenantId().equals(tenantId.toString()))
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        return mapToResponse(user);
    }
    
    /**
     * Checks if a user exists
     */
    @Transactional(readOnly = true)
    public boolean userExists(UUID userId, UUID tenantId) {
        log.debug("Checking if user exists: {} for tenant: {}", userId, tenantId);
        
        return userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .filter(u -> u.getTenantId().equals(tenantId.toString()))
                .isPresent();
    }
    
    /**
     * Gets users by company ID
     * TODO: Implement company-user relationship
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting users for company: {} and tenant: {}", companyId, tenantId);
        
        // For now, return all users for the tenant
        // TODO: Add company_users table relationship
        List<User> users = userRepository.findByTenantId(tenantId);
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets user count for a company
     * TODO: Implement company-user relationship
     */
    @Transactional(readOnly = true)
    public int getUserCountForCompany(UUID companyId, UUID tenantId) {
        log.debug("Getting user count for company: {} and tenant: {}", companyId, tenantId);
        
        // For now, return tenant user count
        // TODO: Add company_users table relationship
        return (int) userRepository.countActiveUsersByTenant(tenantId);
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
                .tenantId(tenantId.toString())
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
                .build();
        
        user = userRepository.save(user);
        
        log.info("User created successfully: {}", user.getId());
        
        // Publish UserCreatedEvent to Kafka
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
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
        
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .filter(u -> u.getTenantId().equals(tenantId.toString()))
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
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
                .tenantId(user.getTenantId())
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
        
        User user = userRepository.findById(userId)
                .filter(u -> !u.isDeleted())
                .filter(u -> u.getTenantId().equals(tenantId.toString()))
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
        
        user.setDeleted(true);
        user.setUpdatedBy(deletedBy);
        
        userRepository.save(user);
        
        log.info("User deleted successfully: {}", userId);
        
        // Publish UserDeletedEvent to Kafka
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(user.getId())
                .tenantId(user.getTenantId())
                .timestamp(LocalDateTime.now())
                .build();
        
        eventPublisher.publishUserDeleted(event);
    }
    
    /**
     * Lists all users for a tenant
     */
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID tenantId) {
        log.debug("Listing users for tenant: {}", tenantId);
        
        List<User> users = userRepository.findByTenantId(tenantId);
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Searches users by criteria
     */
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(UUID tenantId, String firstName, String lastName, 
                                         String email, String status) {
        log.debug("Searching users with criteria for tenant: {}", tenantId);
        
        List<User> users = userRepository.findByTenantId(tenantId);
        
        // Apply filters
        if (firstName != null && !firstName.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getFirstName().toLowerCase().contains(firstName.toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (lastName != null && !lastName.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getLastName().toLowerCase().contains(lastName.toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (email != null && !email.isEmpty()) {
            // Filter by checking contacts via Contact Service
            // Note: This is a simplified implementation
            // In production, consider using a database query for better performance
            final String emailLower = email.toLowerCase();
            users = users.stream()
                    .filter(u -> {
                        try {
                            List<ContactDto> contacts = contactServiceClient.getContactsByOwner(u.getId().toString());
                            return contacts.stream()
                                    .anyMatch(c -> c.getContactValue().toLowerCase().contains(emailLower));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getStatus().name().equals(status))
                    .collect(Collectors.toList());
        }
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Maps User entity to UserResponse DTO
     */
    private UserResponse mapToResponse(User user) {
        // Get primary contact from Contact Service
        String email = null;
        String phone = null;
        
        try {
            List<ContactDto> contacts = contactServiceClient.getContactsByOwner(user.getId().toString());
            
            // Find primary email and phone
            for (ContactDto contact : contacts) {
                if ("EMAIL".equals(contact.getContactType()) && contact.isPrimary()) {
                    email = contact.getContactValue();
                } else if ("PHONE".equals(contact.getContactType()) && contact.isPrimary()) {
                    phone = contact.getContactValue();
                }
            }
            
            // If no primary, get first of each type
            if (email == null) {
                email = contacts.stream()
                        .filter(c -> "EMAIL".equals(c.getContactType()))
                        .findFirst()
                        .map(ContactDto::getContactValue)
                        .orElse(null);
            }
            
            if (phone == null) {
                phone = contacts.stream()
                        .filter(c -> "PHONE".equals(c.getContactType()))
                        .findFirst()
                        .map(ContactDto::getContactValue)
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to fetch contacts for user {}: {}", user.getId(), e.getMessage());
        }
        
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(UUID.fromString(user.getTenantId()))
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
                .email(email)
                .phone(phone)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .registrationType(user.getRegistrationType() != null ? user.getRegistrationType().name() : null)
                .role(user.getRole())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .preferences(user.getPreferences())
                .settings(user.getSettings())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .version(user.getVersion())
                .build();
    }
}

