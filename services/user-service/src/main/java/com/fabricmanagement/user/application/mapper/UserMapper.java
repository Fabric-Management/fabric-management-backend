package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.user.api.dto.UserResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Mapper
 * 
 * Responsible for mapping between User domain entities and DTOs.
 * Also handles enrichment with external data (contacts from Contact Service).
 * 
 * Benefits:
 * - Separates mapping concern from business logic
 * - Single Responsibility Principle
 * - Reusable across different services
 * - Easier to test
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserMapper {
    
    private final ContactServiceClient contactServiceClient;
    
    /**
     * Maps User entity to UserResponse DTO
     * Enriches with contact information from Contact Service
     */
    public UserResponse toResponse(User user) {
        // Get primary contact from Contact Service
        String email = null;
        String phone = null;
        
        try {
            ApiResponse<List<ContactDto>> response = contactServiceClient.getContactsByOwner(user.getId());
            List<ContactDto> contacts = response != null && response.getData() != null ? response.getData() : null;

            // Find primary email and phone
            if (contacts != null) {
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
            }
        } catch (Exception e) {
            log.warn("Failed to fetch contacts for user {}: {}", user.getId(), e.getMessage());
        }
        
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
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
    
    /**
     * Maps list of User entities to list of UserResponse DTOs
     * 
     * Note: See IMPROVEMENTS.md for N+1 query optimization plan
     * For better performance with large datasets, use toResponseListOptimized()
     */
    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Maps list of User entities to list of UserResponse DTOs with batch contact fetching
     * 
     * NEW: Optimized version that fixes N+1 query problem
     * Performance: 100 users = 1 Contact Service call instead of 100 calls
     * 
     * Use this method for:
     * - Listing users (GET /users)
     * - Searching users (GET /users/search)
     * - Any operation returning multiple users
     */
    public List<UserResponse> toResponseListOptimized(List<User> users) {
        if (users == null || users.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        log.debug("Mapping {} users to response with batch contact fetching", users.size());
        
        // Collect all user IDs (UUID - no conversion needed!)
        List<UUID> userIds = users.stream()
                .map(User::getId)
                .toList();
        
        // Single batch call to Contact Service!
        final Map<String, List<ContactDto>> contactsMap = fetchContactsBatch(userIds);
        
        // Map users with pre-fetched contacts
        return users.stream()
                .map(user -> toResponseWithContacts(user, contactsMap.get(user.getId().toString())))
                .toList();
    }
    
    /**
     * Fetches contacts in batch for multiple users
     * 
     * Helper method to keep toResponseListOptimized clean.
     * Returns Map<String, List<ContactDto>> where String is UUID.toString() for JSON compatibility
     */
    private Map<String, List<ContactDto>> fetchContactsBatch(List<UUID> userIds) {
        try {
            ApiResponse<Map<String, List<ContactDto>>> response = 
                contactServiceClient.getContactsByOwnersBatch(userIds);
            return response != null && response.getData() != null 
                ? response.getData() 
                : java.util.Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to batch fetch contacts, falling back to empty contacts: {}", e.getMessage());
            return java.util.Collections.emptyMap();
        }
    }
    
    /**
     * Maps User entity to UserResponse DTO with provided contacts
     * 
     * Helper method for batch mapping
     */
    private UserResponse toResponseWithContacts(User user, List<ContactDto> contacts) {
        String email = null;
        String phone = null;
        
        // Extract primary email and phone from contacts
        if (contacts != null && !contacts.isEmpty()) {
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
        }
        
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
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
    
    /**
     * Maps User entity to UserResponse DTO without contact enrichment
     * Use this for better performance when contact info is not needed
     */
    public UserResponse toResponseWithoutContacts(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(user.getDisplayName())
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

