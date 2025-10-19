package com.fabricmanagement.user.application.service;

import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.domain.exception.UserNotFoundException;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserResponse;
import com.fabricmanagement.user.application.mapper.UserEventMapper;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.messaging.UserEventPublisher;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class UserService {
    
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final UserEventMapper eventMapper;
    private final UserEventPublisher eventPublisher;
    private final com.fabricmanagement.user.infrastructure.client.ContactServiceClient contactServiceClient;
    
    public UserService(
            UserRepository userRepository,
            UserMapper userMapper,
            UserEventMapper eventMapper,
            UserEventPublisher eventPublisher,
            @org.springframework.context.annotation.Lazy com.fabricmanagement.user.infrastructure.client.ContactServiceClient contactServiceClient) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.eventMapper = eventMapper;
        this.eventPublisher = eventPublisher;
        this.contactServiceClient = contactServiceClient;
    }
    
    @Transactional(readOnly = true)
    public UserResponse getUser(UUID userId, UUID tenantId) {
        log.debug("Getting user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        return userMapper.toResponse(user);
    }
    
    @Transactional(readOnly = true)
    public boolean userExists(UUID userId, UUID tenantId) {
        log.debug("Checking if user exists: {} for tenant: {}", userId, tenantId);
        
        return userRepository.findActiveByIdAndTenantId(userId, tenantId).isPresent();
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> getUsersByTenant(UUID tenantId) {
        log.debug("Getting users for tenant: {}", tenantId);
        List<User> users = userRepository.findByTenantId(tenantId);
        return userMapper.toResponseList(users);
    }
    
    @Transactional(readOnly = true)
    public int getUserCountForTenant(UUID tenantId) {
        log.debug("Getting user count for tenant: {}", tenantId);
        return (int) userRepository.countActiveUsersByTenant(tenantId);
    }
    
    @Transactional
    public UUID createUser(CreateUserRequest request, UUID tenantId, String createdBy) {
        log.info("Creating user: {} for tenant: {}", request.getEmail(), tenantId);
        
        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        user = userRepository.save(user);
        
        log.info("User created successfully: {}", user.getId());
        
        eventPublisher.publishUserCreated(eventMapper.toCreatedEvent(user, request.getEmail()));
        
        return user.getId();
    }
    
    @Transactional
    public com.fabricmanagement.user.api.dto.response.UserInvitationResponse inviteUser(
            com.fabricmanagement.user.api.dto.request.InviteUserRequest request,
            UUID tenantId,
            String createdBy) {
        
        log.info("Inviting user: {} for tenant: {}", request.getEmail(), tenantId);
        
        // Step 1: Create user
        CreateUserRequest createRequest = CreateUserRequest.builder()
            .firstName(request.getFirstName())
            .lastName(request.getLastName())
            .email(request.getEmail())
            .phone(request.getPhone())
            .role(request.getRole())
            .build();
        
        UUID userId = createUser(createRequest, tenantId, createdBy);
        
        // Step 2: Create email contact (Feign call)
        com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto emailContactRequest = 
            com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto.builder()
                .ownerId(userId.toString())
                .ownerType("USER")
                .contactType("EMAIL")
                .contactValue(request.getEmail())
                .isPrimary(true)
                .build();
        
        com.fabricmanagement.user.infrastructure.client.dto.ContactDto emailContact = 
            contactServiceClient.createContact(emailContactRequest).getData();
        
        // Step 3: Create phone contact (if provided)
        UUID phoneContactId = null;
        if (request.getPhone() != null && !request.getPhone().isEmpty()) {
            com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto phoneContactRequest = 
                com.fabricmanagement.user.infrastructure.client.dto.CreateContactDto.builder()
                    .ownerId(userId.toString())
                    .ownerType("USER")
                    .contactType("PHONE")
                    .contactValue(request.getPhone())
                    .isPrimary(false)
                    .build();
            
            com.fabricmanagement.user.infrastructure.client.dto.ContactDto phoneContact = 
                contactServiceClient.createContact(phoneContactRequest).getData();
            phoneContactId = phoneContact.getId();
        }
        
        // Step 4: Send verification code (if requested)
        if (request.isSendVerification()) {
            contactServiceClient.sendVerificationCode(emailContact.getId());
        }
        
        log.info("User invited successfully: userId={}, emailContactId={}", userId, emailContact.getId());
        
        // Step 5: Build response
        return com.fabricmanagement.user.api.dto.response.UserInvitationResponse.builder()
            .userId(userId)
            .emailContactId(emailContact.getId())
            .phoneContactId(phoneContactId)
            .verificationSent(request.isSendVerification())
            .message("User invited successfully")
            .build();
    }
    
    @Transactional
    public void updateUser(UUID userId, UpdateUserRequest request, UUID tenantId, String updatedBy) {
        log.info("Updating user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        userMapper.updateFromRequest(user, request, updatedBy);
        userRepository.save(user);
        
        log.info("User updated successfully: {}", userId);
        
        eventPublisher.publishUserUpdated(eventMapper.toUpdatedEvent(user));
    }
    
    @Transactional
    public void deleteUser(UUID userId, UUID tenantId, String deletedBy) {
        log.info("Deleting user: {} for tenant: {}", userId, tenantId);
        
        User user = findActiveUserOrThrow(userId, tenantId);
        
        user.setDeleted(true);
        user.setStatus(UserStatus.DELETED);
        user.setUpdatedBy(deletedBy);
        
        userRepository.save(user);
        
        log.info("User deleted successfully: {}", userId);
        
        eventPublisher.publishUserDeleted(eventMapper.toDeletedEvent(user));
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> listUsers(UUID tenantId) {
        log.debug("Listing users for tenant: {}", tenantId);
        List<User> users = userRepository.findByTenantId(tenantId);
        return userMapper.toResponseListOptimized(users);
    }
    
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> listUsersPaginated(UUID tenantId, Pageable pageable) {
        log.debug("Listing users for tenant: {} with pagination", tenantId);
        
        Page<User> userPage = userRepository.findByTenantIdPaginated(tenantId, pageable);
        return userMapper.toPagedResponse(userPage);
    }
    
    @Transactional(readOnly = true)
    public List<UserResponse> searchUsers(UUID tenantId, String firstName, String lastName, 
                                         String email, String status) {
        log.debug("Searching users for tenant: {}", tenantId);
        
        List<User> users = userRepository.findByTenantId(tenantId);
        
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
        
        if (status != null && !status.isEmpty()) {
            users = users.stream()
                    .filter(u -> u.getStatus().name().equals(status))
                    .toList();
        }
        
        return userMapper.toResponseListOptimized(users);
    }
    
    @Transactional(readOnly = true)
    public PagedResponse<UserResponse> searchUsersPaginated(UUID tenantId, String firstName, 
                                                            String lastName, String status, 
                                                            Pageable pageable) {
        log.debug("Searching users for tenant: {} with pagination", tenantId);
        
        Page<User> userPage = userRepository.searchUsersPaginated(
            tenantId, firstName, lastName, status, pageable
        );
        
        return userMapper.toPagedResponse(userPage);
    }
    
    private User findActiveUserOrThrow(UUID userId, UUID tenantId) {
        return userRepository.findActiveByIdAndTenantId(userId, tenantId)
                .orElseThrow(() -> new UserNotFoundException(userId.toString()));
    }
}

