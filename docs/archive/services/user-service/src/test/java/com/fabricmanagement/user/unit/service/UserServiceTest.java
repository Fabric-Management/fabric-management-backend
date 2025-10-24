package com.fabricmanagement.user.unit.service;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.shared.domain.exception.UserNotFoundException;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.request.InviteUserRequest;
import com.fabricmanagement.user.api.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserInvitationResponse;
import com.fabricmanagement.user.api.dto.response.UserResponse;
import com.fabricmanagement.user.application.mapper.UserEventMapper;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.application.service.UserService;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserDeletedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.user.infrastructure.messaging.UserEventPublisher;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static com.fabricmanagement.user.fixtures.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit Tests for UserService
 * 
 * Testing Strategy:
 * - Fast (< 100ms per test)
 * - Isolated (mocked dependencies)
 * - Focused (single behavior per test)
 * - Readable (Given-When-Then pattern)
 * 
 * Coverage Goal: 95%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests")
class UserServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private UserMapper userMapper;
    
    @Mock
    private UserEventMapper eventMapper;
    
    @Mock
    private UserEventPublisher eventPublisher;
    
    @Mock
    private ContactServiceClient contactServiceClient;
    
    @InjectMocks
    private UserService userService;
    
    // ═════════════════════════════════════════════════════
    // CREATE USER TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should create user when valid request provided")
    void shouldCreateUser_whenValidRequest() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@test.com")
                .build();
        
        User user = createActiveUser("John", "Doe", "john.doe@test.com");
        UUID expectedUserId = UUID.randomUUID();
        
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(expectedUserId)
                .tenantId(TEST_TENANT_ID.toString())
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@test.com")
                .build();
        
        when(userMapper.fromCreateRequest(any(), any(), any())).thenReturn(user);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            // Simulate Hibernate setting the ID after save
            savedUser.setId(expectedUserId);
            return savedUser;
        });
        when(eventMapper.toCreatedEvent(any(User.class), anyString())).thenReturn(event);
        
        // When
        UUID userId = userService.createUser(request, TEST_TENANT_ID, "TEST_ADMIN");
        
        // Then
        assertThat(userId).isNotNull();
        assertThat(userId).isEqualTo(expectedUserId);
        verify(userRepository).save(any(User.class));
        verify(eventPublisher).publishUserCreated(event);
    }
    
    @Test
    @DisplayName("Should NOT publish event when user creation fails")
    void shouldNotPublishEvent_whenCreationFails() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@test.com")
                .build();
        
        User user = createActiveUser("John", "Doe", "john.doe@test.com");
        
        when(userMapper.fromCreateRequest(any(), any(), any())).thenReturn(user);
        when(userRepository.save(any())).thenThrow(new RuntimeException("Database error"));
        
        // When & Then
        assertThatThrownBy(() -> 
                userService.createUser(request, TEST_TENANT_ID, "TEST_ADMIN"))
                .isInstanceOf(RuntimeException.class);
        
        verify(eventPublisher, never()).publishUserCreated(any());
    }
    
    // ═════════════════════════════════════════════════════
    // GET USER TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should return user when user exists")
    void shouldReturnUser_whenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createActiveUser("John", "Doe", "john@test.com");
        UserResponse response = new UserResponse();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.of(user));
        when(userMapper.toResponse(user)).thenReturn(response);
        
        // When
        UserResponse result = userService.getUser(userId, TEST_TENANT_ID);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(response);
        verify(userRepository).findActiveByIdAndTenantId(userId, TEST_TENANT_ID);
    }
    
    @Test
    @DisplayName("Should throw exception when user not found")
    void shouldThrowException_whenUserNotFound() {
        // Given
        UUID userId = UUID.randomUUID();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> userService.getUser(userId, TEST_TENANT_ID))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessageContaining(userId.toString());
    }
    
    @Test
    @DisplayName("Should check if user exists")
    void shouldCheckUserExists_whenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createActiveUser("John", "Doe", "john@test.com");
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.of(user));
        
        // When
        boolean exists = userService.userExists(userId, TEST_TENANT_ID);
        
        // Then
        assertThat(exists).isTrue();
    }
    
    @Test
    @DisplayName("Should return false when user does not exist")
    void shouldReturnFalse_whenUserDoesNotExist() {
        // Given
        UUID userId = UUID.randomUUID();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.empty());
        
        // When
        boolean exists = userService.userExists(userId, TEST_TENANT_ID);
        
        // Then
        assertThat(exists).isFalse();
    }
    
    // ═════════════════════════════════════════════════════
    // UPDATE USER TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should update user when valid request provided")
    void shouldUpdateUser_whenValidRequest() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createActiveUser("John", "Doe", "john@test.com");
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .build();
        UserUpdatedEvent event = UserUpdatedEvent.builder()
                .userId(userId)
                .tenantId(TEST_TENANT_ID.toString())
                .build();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(eventMapper.toUpdatedEvent(user)).thenReturn(event);
        
        // When
        userService.updateUser(userId, request, TEST_TENANT_ID, "TEST_ADMIN");
        
        // Then
        verify(userMapper).updateFromRequest(user, request, "TEST_ADMIN");
        verify(userRepository).save(user);
        verify(eventPublisher).publishUserUpdated(event);
    }
    
    @Test
    @DisplayName("Should throw exception when updating non-existent user")
    void shouldThrowException_whenUpdatingNonExistentUser() {
        // Given
        UUID userId = UUID.randomUUID();
        UpdateUserRequest request = UpdateUserRequest.builder()
                .firstName("Jane")
                .build();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> 
                userService.updateUser(userId, request, TEST_TENANT_ID, "TEST_ADMIN"))
                .isInstanceOf(UserNotFoundException.class);
        
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserUpdated(any());
    }
    
    // ═════════════════════════════════════════════════════
    // DELETE USER TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should delete user when user exists")
    void shouldDeleteUser_whenUserExists() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = createActiveUser("John", "Doe", "john@test.com");
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(userId)
                .tenantId(TEST_TENANT_ID.toString())
                .build();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(eventMapper.toDeletedEvent(user)).thenReturn(event);
        
        // When
        userService.deleteUser(userId, TEST_TENANT_ID, "TEST_ADMIN");
        
        // Then
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.getStatus()).isEqualTo(UserStatus.DELETED);
        assertThat(user.getUpdatedBy()).isEqualTo("TEST_ADMIN");
        verify(userRepository).save(user);
        verify(eventPublisher).publishUserDeleted(event);
    }
    
    @Test
    @DisplayName("Should throw exception when deleting non-existent user")
    void shouldThrowException_whenDeletingNonExistentUser() {
        // Given
        UUID userId = UUID.randomUUID();
        
        when(userRepository.findActiveByIdAndTenantId(userId, TEST_TENANT_ID))
                .thenReturn(Optional.empty());
        
        // When & Then
        assertThatThrownBy(() -> 
                userService.deleteUser(userId, TEST_TENANT_ID, "TEST_ADMIN"))
                .isInstanceOf(UserNotFoundException.class);
        
        verify(userRepository, never()).save(any());
        verify(eventPublisher, never()).publishUserDeleted(any());
    }
    
    // ═════════════════════════════════════════════════════
    // LIST USERS TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should list users by tenant")
    void shouldListUsers_byTenant() {
        // Given
        List<User> users = createMultipleUsers(5);
        List<UserResponse> responses = new ArrayList<>();
        
        when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(users);
        when(userMapper.toResponseListOptimized(users)).thenReturn(responses);
        
        // When
        List<UserResponse> result = userService.listUsers(TEST_TENANT_ID);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(responses);
        verify(userRepository).findByTenantId(TEST_TENANT_ID);
    }
    
    @Test
    @DisplayName("Should return empty list when no users found")
    void shouldReturnEmptyList_whenNoUsersFound() {
        // Given
        when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(Collections.emptyList());
        when(userMapper.toResponseListOptimized(anyList())).thenReturn(Collections.emptyList());
        
        // When
        List<UserResponse> result = userService.listUsers(TEST_TENANT_ID);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    // ═════════════════════════════════════════════════════
    // BATCH GET USERS TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should get users batch when valid IDs provided")
    void shouldGetUsersBatch_whenValidIdsProvided() {
        // Given
        List<UUID> userIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<User> users = createMultipleUsers(3);
        List<UserResponse> responses = new ArrayList<>();
        
        when(userRepository.findAllByIdInAndTenantIdAndStatus(userIds, TEST_TENANT_ID, UserStatus.ACTIVE))
                .thenReturn(users);
        when(userMapper.toResponseList(users)).thenReturn(responses);
        
        // When
        List<UserResponse> result = userService.getUsersBatch(userIds, TEST_TENANT_ID);
        
        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findAllByIdInAndTenantIdAndStatus(userIds, TEST_TENANT_ID, UserStatus.ACTIVE);
    }
    
    @Test
    @DisplayName("Should return empty list when no users match batch IDs")
    void shouldReturnEmptyList_whenNoUsersMatchBatchIds() {
        // Given
        List<UUID> userIds = Arrays.asList(UUID.randomUUID(), UUID.randomUUID());
        
        when(userRepository.findAllByIdInAndTenantIdAndStatus(userIds, TEST_TENANT_ID, UserStatus.ACTIVE))
                .thenReturn(Collections.emptyList());
        when(userMapper.toResponseList(anyList())).thenReturn(Collections.emptyList());
        
        // When
        List<UserResponse> result = userService.getUsersBatch(userIds, TEST_TENANT_ID);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    // ═════════════════════════════════════════════════════
    // COUNT USERS TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should count active users for tenant")
    void shouldCountActiveUsers_forTenant() {
        // Given
        when(userRepository.countActiveUsersByTenant(TEST_TENANT_ID)).thenReturn(10L);
        
        // When
        int count = userService.getUserCountForTenant(TEST_TENANT_ID);
        
        // Then
        assertThat(count).isEqualTo(10);
        verify(userRepository).countActiveUsersByTenant(TEST_TENANT_ID);
    }
    
    @Test
    @DisplayName("Should return zero when no active users found")
    void shouldReturnZero_whenNoActiveUsersFound() {
        // Given
        when(userRepository.countActiveUsersByTenant(TEST_TENANT_ID)).thenReturn(0L);
        
        // When
        int count = userService.getUserCountForTenant(TEST_TENANT_ID);
        
        // Then
        assertThat(count).isZero();
    }
    
    // ═════════════════════════════════════════════════════
    // SEARCH USERS TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should search users by first name")
    void shouldSearchUsers_byFirstName() {
        // Given
        List<User> allUsers = Arrays.asList(
                createActiveUser("John", "Doe", "john@test.com"),
                createActiveUser("Jane", "Smith", "jane@test.com"),
                createActiveUser("Johnny", "Walker", "johnny@test.com")
        );
        
        when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(allUsers);
        when(userMapper.toResponseListOptimized(anyList())).thenReturn(new ArrayList<>());
        
        // When
        List<UserResponse> result = userService.searchUsers(TEST_TENANT_ID, "John", null, null, null);
        
        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findByTenantId(TEST_TENANT_ID);
    }
    
    @Test
    @DisplayName("Should search users by status")
    void shouldSearchUsers_byStatus() {
        // Given
        List<User> allUsers = createUsersWithDifferentStatuses();
        
        when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(allUsers);
        when(userMapper.toResponseListOptimized(anyList())).thenReturn(new ArrayList<>());
        
        // When
        List<UserResponse> result = userService.searchUsers(TEST_TENANT_ID, null, null, null, "ACTIVE");
        
        // Then
        assertThat(result).isNotNull();
        verify(userRepository).findByTenantId(TEST_TENANT_ID);
    }
    
    // ═════════════════════════════════════════════════════
    // GET USERS BY TENANT TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should get all users by tenant")
    void shouldGetUsersByTenant() {
        // Given
        List<User> users = Arrays.asList(
                createActiveUser("John", "Doe", "john@test.com"),
                createActiveUser("Jane", "Smith", "jane@test.com")
        );
        List<UserResponse> expectedResponses = Arrays.asList(
                UserResponse.builder().id(UUID.randomUUID()).build(),
                UserResponse.builder().id(UUID.randomUUID()).build()
        );
        
        when(userRepository.findByTenantId(TEST_TENANT_ID)).thenReturn(users);
        when(userMapper.toResponseList(users)).thenReturn(expectedResponses);
        
        // When
        List<UserResponse> result = userService.getUsersByTenant(TEST_TENANT_ID);
        
        // Then
        assertThat(result).hasSize(2);
        verify(userRepository).findByTenantId(TEST_TENANT_ID);
    }
    
    // ═════════════════════════════════════════════════════
    // PAGINATED LIST TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should list users with pagination")
    void shouldListUsersPaginated() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<User> users = Arrays.asList(
                createActiveUser("John", "Doe", "john@test.com"),
                createActiveUser("Jane", "Smith", "jane@test.com")
        );
        Page<User> userPage = new PageImpl<>(users, pageable, 2);
        PagedResponse<UserResponse> expectedResponse = PagedResponse.of(
                Arrays.asList(
                        UserResponse.builder().id(UUID.randomUUID()).build(),
                        UserResponse.builder().id(UUID.randomUUID()).build()
                ),
                0, 20, 2, 1
        );
        
        when(userRepository.findByTenantIdPaginated(TEST_TENANT_ID, pageable)).thenReturn(userPage);
        when(userMapper.toPagedResponse(userPage)).thenReturn(expectedResponse);
        
        // When
        PagedResponse<UserResponse> result = userService.listUsersPaginated(TEST_TENANT_ID, pageable);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(2);
        verify(userRepository).findByTenantIdPaginated(TEST_TENANT_ID, pageable);
    }
    
    // ═════════════════════════════════════════════════════
    // PAGINATED SEARCH TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should search users with pagination")
    void shouldSearchUsersPaginated() {
        // Given
        Pageable pageable = PageRequest.of(0, 20);
        List<User> users = Arrays.asList(createActiveUser("John", "Doe", "john@test.com"));
        Page<User> userPage = new PageImpl<>(users, pageable, 1);
        PagedResponse<UserResponse> expectedResponse = PagedResponse.of(
                Arrays.asList(UserResponse.builder().id(UUID.randomUUID()).build()),
                0, 20, 1, 1
        );
        
        when(userRepository.searchUsersPaginated(TEST_TENANT_ID, "John", null, null, pageable))
                .thenReturn(userPage);
        when(userMapper.toPagedResponse(userPage)).thenReturn(expectedResponse);
        
        // When
        PagedResponse<UserResponse> result = userService.searchUsersPaginated(
                TEST_TENANT_ID, "John", null, null, pageable
        );
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        verify(userRepository).searchUsersPaginated(TEST_TENANT_ID, "John", null, null, pageable);
    }
    
    // ═════════════════════════════════════════════════════
    // INVITE USER TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should invite user with email only")
    void shouldInviteUser_withEmailOnly() {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .sendVerification(true)
                .build();
        
        User user = createActiveUser("John", "Doe", "john@test.com");
        UUID expectedUserId = UUID.randomUUID();
        UUID emailContactId = UUID.randomUUID();
        
        ContactDto emailContact = ContactDto.builder()
                .id(emailContactId)
                .ownerId(expectedUserId)
                .contactValue("john@test.com")
                .build();
        
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(expectedUserId)
                .tenantId(TEST_TENANT_ID.toString())
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .status(UserStatus.ACTIVE.name())
                .registrationType(user.getRegistrationType().name())
                .timestamp(java.time.LocalDateTime.now())
                .build();
        
        when(userMapper.fromCreateRequest(any(CreateUserRequest.class), any(UUID.class), anyString()))
                .thenReturn(user);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(expectedUserId);
            return savedUser;
        });
        when(eventMapper.toCreatedEvent(any(User.class), anyString())).thenReturn(event);
        when(contactServiceClient.createContact(any())).thenReturn(ApiResponse.success(emailContact));
        
        // When
        UserInvitationResponse result = userService.inviteUser(request, TEST_TENANT_ID, "ADMIN");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(expectedUserId);
        assertThat(result.getEmailContactId()).isEqualTo(emailContactId);
        verify(contactServiceClient).createContact(any());
        verify(contactServiceClient).sendVerificationCode(emailContactId);
    }
    
    @Test
    @DisplayName("Should invite user with email and phone")
    void shouldInviteUser_withEmailAndPhone() {
        // Given
        InviteUserRequest request = InviteUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phone("+905551234567")
                .sendVerification(false)
                .build();
        
        User user = createActiveUser("John", "Doe", "john@test.com");
        UUID expectedUserId = UUID.randomUUID();
        UUID emailContactId = UUID.randomUUID();
        UUID phoneContactId = UUID.randomUUID();
        
        ContactDto emailContact = ContactDto.builder()
                .id(emailContactId)
                .ownerId(expectedUserId)
                .contactValue("john@test.com")
                .build();
        
        ContactDto phoneContact = ContactDto.builder()
                .id(phoneContactId)
                .ownerId(expectedUserId)
                .contactValue("+905551234567")
                .build();
        
        UserCreatedEvent event = UserCreatedEvent.builder()
                .userId(expectedUserId)
                .tenantId(TEST_TENANT_ID.toString())
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .status(UserStatus.ACTIVE.name())
                .registrationType(user.getRegistrationType().name())
                .timestamp(java.time.LocalDateTime.now())
                .build();
        
        when(userMapper.fromCreateRequest(any(CreateUserRequest.class), any(UUID.class), anyString()))
                .thenReturn(user);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User savedUser = invocation.getArgument(0);
            savedUser.setId(expectedUserId);
            return savedUser;
        });
        when(eventMapper.toCreatedEvent(any(User.class), anyString())).thenReturn(event);
        when(contactServiceClient.createContact(any()))
                .thenReturn(ApiResponse.success(emailContact))
                .thenReturn(ApiResponse.success(phoneContact));
        
        // When
        UserInvitationResponse result = userService.inviteUser(request, TEST_TENANT_ID, "ADMIN");
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(expectedUserId);
        assertThat(result.getEmailContactId()).isEqualTo(emailContactId);
        assertThat(result.getPhoneContactId()).isEqualTo(phoneContactId);
        verify(contactServiceClient, times(2)).createContact(any());
        verify(contactServiceClient, never()).sendVerificationCode(any());
    }
}

