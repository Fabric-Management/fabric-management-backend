package com.fabricmanagement.user.application.mapper;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.domain.policy.UserContext;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserResponse;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User Mapper
 * 
 * Maps User entities to DTOs with contact enrichment.
 * 
 * Pattern: @Lazy injection for ContactServiceClient to prevent circular dependency
 */
@Component
@Slf4j
public class UserMapper {
    
    private final ContactServiceClient contactServiceClient;
    
    // ✅ Manual constructor with @Lazy for ContactServiceClient
    public UserMapper(@Lazy ContactServiceClient contactServiceClient) {
        this.contactServiceClient = contactServiceClient;
    }
    
    public UserResponse toResponse(User user) {
        String email = null;
        String phone = null;
        
        try {
            ApiResponse<List<ContactDto>> response = contactServiceClient.getContactsByOwner(user.getId());
            List<ContactDto> contacts = response != null && response.getData() != null ? response.getData() : null;

            if (contacts != null) {
                for (ContactDto contact : contacts) {
                    if ("EMAIL".equals(contact.getContactType()) && contact.isPrimary()) {
                        email = contact.getContactValue();
                    } else if ("PHONE".equals(contact.getContactType()) && contact.isPrimary()) {
                        phone = contact.getContactValue();
                    }
                }
                
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
        
        return buildUserResponse(user, email, phone);
    }
    
    private UserResponse buildUserResponse(User user, String email, String phone) {
        String displayName = user.getDisplayName() != null 
            ? user.getDisplayName() 
            : user.getFirstName() + " " + user.getLastName();
        
        return UserResponse.builder()
                .id(user.getId())
                .tenantId(user.getTenantId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .displayName(displayName)
                .email(email)
                .phone(phone)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .registrationType(user.getRegistrationType() != null ? user.getRegistrationType().name() : null)
                .role(user.getRole() != null ? user.getRole().name() : null)
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
    
    public List<UserResponse> toResponseList(List<User> users) {
        return users.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }
    
    public List<UserResponse> toResponseListOptimized(List<User> users) {
        if (users == null || users.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        
        log.debug("Mapping {} users with batch contact fetching", users.size());
        
        List<UUID> userIds = users.stream().map(User::getId).toList();
        Map<String, List<ContactDto>> contactsMap = fetchContactsBatch(userIds);
        
        return users.stream()
                .map(user -> toResponseWithContacts(user, contactsMap.get(user.getId().toString())))
                .toList();
    }
    
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
    
    private UserResponse toResponseWithContacts(User user, List<ContactDto> contacts) {
        String email = null;
        String phone = null;
        
        if (contacts != null && !contacts.isEmpty()) {
            for (ContactDto contact : contacts) {
                if ("EMAIL".equals(contact.getContactType()) && contact.isPrimary()) {
                    email = contact.getContactValue();
                } else if ("PHONE".equals(contact.getContactType()) && contact.isPrimary()) {
                    phone = contact.getContactValue();
                }
            }
            
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
        
        return buildUserResponse(user, email, phone);
    }
    
    public UserResponse toResponseWithoutContacts(User user) {
        return buildUserResponse(user, null, null);
    }
    
    public PagedResponse<UserResponse> toPagedResponse(Page<User> userPage) {
        List<UserResponse> userResponses = toResponseListOptimized(userPage.getContent());
        
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
    
    public User fromCreateRequest(CreateUserRequest request, UUID tenantId, String createdBy) {
        // ✅ Parse role with fallback
        SystemRole role = SystemRole.USER; // Default role
        if (request.getRole() != null && !request.getRole().isEmpty()) {
            try {
                role = SystemRole.valueOf(request.getRole());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role '{}' provided, using default USER role", request.getRole());
                role = SystemRole.USER;
            }
        }
        
        // ✅ Parse UserContext with fallback (NOT NULL in DB!)
        UserContext userContext = UserContext.INTERNAL; // Default: INTERNAL
        if (request.getUserContext() != null && !request.getUserContext().isEmpty()) {
            try {
                userContext = UserContext.valueOf(request.getUserContext());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid userContext '{}' provided, using default INTERNAL", request.getUserContext());
                userContext = UserContext.INTERNAL;
            }
        }
        
        // ✅ Safe UUID parsing with validation
        UUID companyId = null;
        if (request.getCompanyId() != null && !request.getCompanyId().trim().isEmpty()) {
            try {
                companyId = UUID.fromString(request.getCompanyId().trim());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID for companyId: '{}', setting to null", request.getCompanyId());
            }
        }
        
        UUID departmentId = null;
        if (request.getDepartmentId() != null && !request.getDepartmentId().trim().isEmpty()) {
            try {
                departmentId = UUID.fromString(request.getDepartmentId().trim());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID for departmentId: '{}', setting to null", request.getDepartmentId());
            }
        }
        
        UUID stationId = null;
        if (request.getStationId() != null && !request.getStationId().trim().isEmpty()) {
            try {
                stationId = UUID.fromString(request.getStationId().trim());
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID for stationId: '{}', setting to null", request.getStationId());
            }
        }
        
        return User.builder()
                // ✅ DON'T set ID manually - BaseEntity has @GeneratedValue(UUID)!
                // ✅ DON'T set version manually - BaseEntity has @Version!
                // Hibernate will:
                //   1. Generate UUID on persist
                //   2. Set version=0 on first persist
                //   3. Auto-increment version on each update
                .tenantId(tenantId)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .displayName(request.getDisplayName())
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .role(role)
                .preferences(request.getPreferences())
                .settings(request.getSettings())
                .createdBy(createdBy)
                .updatedBy(createdBy)
                .deleted(false)
                .companyId(companyId)
                .departmentId(departmentId)
                .stationId(stationId)
                .jobTitle(request.getJobTitle())
                .userContext(userContext)  // ✅ NEVER null - database constraint!
                .build();
    }
    
    public void updateFromRequest(User user, UpdateUserRequest request, String updatedBy) {
        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) user.setLastName(request.getLastName());
        if (request.getDisplayName() != null) user.setDisplayName(request.getDisplayName());
        
        if (request.getRole() != null && !request.getRole().isEmpty()) {
            try {
                SystemRole role = SystemRole.valueOf(request.getRole());
                user.setRole(role);
            } catch (IllegalArgumentException e) {
                log.warn("Invalid role '{}' provided for update, ignoring", request.getRole());
            }
        }
        
        if (request.getPreferences() != null) user.setPreferences(request.getPreferences());
        if (request.getSettings() != null) user.setSettings(request.getSettings());
        
        user.setUpdatedBy(updatedBy);
        // ✅ DON'T increment version manually - Hibernate @Version auto-increments on save!
    }
}

