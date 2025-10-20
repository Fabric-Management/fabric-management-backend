package com.fabricmanagement.user.unit.mapper;

import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.domain.role.SystemRole;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserResponse;
import com.fabricmanagement.user.application.mapper.UserMapper;
import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.user.infrastructure.client.dto.ContactDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.fabricmanagement.user.fixtures.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for UserMapper
 * 
 * Testing Strategy:
 * - Verify correct mapping between entities and DTOs
 * - Test Hibernate-managed fields are NOT set manually
 * - Validate contact enrichment logic
 * - Test edge cases (null values, missing contacts)
 * 
 * Coverage Goal: 90%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserMapper Unit Tests")
class UserMapperTest {
    
    @Mock
    private ContactServiceClient contactServiceClient;
    
    private UserMapper userMapper;
    
    @BeforeEach
    void setUp() {
        userMapper = new UserMapper(contactServiceClient);
    }
    
    // ═════════════════════════════════════════════════════
    // ENTITY → DTO MAPPING TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should map user entity to response DTO")
    void shouldMapUserEntityToResponse() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        
        ContactDto emailContact = ContactDto.builder()
                .id(UUID.randomUUID())
                .contactType("EMAIL")
                .contactValue("john@test.com")
                .isPrimary(true)
                .build();
        
        ContactDto phoneContact = ContactDto.builder()
                .id(UUID.randomUUID())
                .contactType("PHONE")
                .contactValue("+1234567890")
                .isPrimary(true)
                .build();
        
        ApiResponse<List<ContactDto>> contactResponse = ApiResponse.success(
                Arrays.asList(emailContact, phoneContact)
        );
        
        when(contactServiceClient.getContactsByOwner(any())).thenReturn(contactResponse);
        
        // When
        UserResponse response = userMapper.toResponse(user);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getLastName()).isEqualTo("Doe");
        assertThat(response.getDisplayName()).isEqualTo("John Doe");
        assertThat(response.getEmail()).isEqualTo("john@test.com");
        assertThat(response.getPhone()).isEqualTo("+1234567890");
        assertThat(response.getStatus()).isEqualTo(UserStatus.ACTIVE.name());
    }
    
    @Test
    @DisplayName("Should handle missing contacts gracefully")
    void shouldHandleMissingContacts_gracefully() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        UserResponse response = userMapper.toResponse(user);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getEmail()).isNull();
        assertThat(response.getPhone()).isNull();
    }
    
    @Test
    @DisplayName("Should handle contact service error gracefully")
    void shouldHandleContactServiceError_gracefully() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenThrow(new RuntimeException("Service unavailable"));
        
        // When
        UserResponse response = userMapper.toResponse(user);
        
        // Then
        assertThat(response).isNotNull();
        assertThat(response.getFirstName()).isEqualTo("John");
        assertThat(response.getEmail()).isNull(); // Fallback to null
    }
    
    // ═════════════════════════════════════════════════════
    // DTO → ENTITY MAPPING TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should map create request to user entity")
    void shouldMapCreateRequestToEntity() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .phone("+1234567890")
                .role("USER") // String representation
                .build();
        
        UUID tenantId = UUID.randomUUID();
        String createdBy = "ADMIN";
        
        // When
        User user = userMapper.fromCreateRequest(request, tenantId, createdBy);
        
        // Then
        assertThat(user).isNotNull();
        assertThat(user.getFirstName()).isEqualTo("John");
        assertThat(user.getLastName()).isEqualTo("Doe");
        assertThat(user.getTenantId()).isEqualTo(tenantId);
        assertThat(user.getRole()).isEqualTo(SystemRole.USER);
        
        // ✅ CRITICAL: Hibernate-managed fields should be null
        assertThat(user.getId()).isNull();
        assertThat(user.getVersion()).isNull();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }
    
    @Test
    @DisplayName("Should set default values when creating user")
    void shouldSetDefaultValues_whenCreatingUser() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john@test.com")
                .role("USER") // String representation of role
                .build();
        
        // When
        User user = userMapper.fromCreateRequest(request, TEST_TENANT_ID, "ADMIN");
        
        // Then
        assertThat(user.getStatus()).isNotNull();
        assertThat(user.getRegistrationType()).isNotNull();
        assertThat(user.getUserContext()).isNotNull();
    }
    
    // ═════════════════════════════════════════════════════
    // BATCH MAPPING TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should map list of users to response list")
    void shouldMapListOfUsers_toResponseList() {
        // Given
        List<User> users = createMultipleUsers(3);
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        List<UserResponse> responses = userMapper.toResponseList(users);
        
        // Then
        assertThat(responses).hasSize(3);
    }
    
    @Test
    @DisplayName("Should handle empty user list")
    void shouldHandleEmptyUserList() {
        // Given
        List<User> users = List.of();
        
        // When
        List<UserResponse> responses = userMapper.toResponseList(users);
        
        // Then
        assertThat(responses).isEmpty();
    }
    
    // ═════════════════════════════════════════════════════
    // SPECIAL CASES
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should use display name when available")
    void shouldUseDisplayName_whenAvailable() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        user.setDisplayName("Johnny");
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        UserResponse response = userMapper.toResponse(user);
        
        // Then
        assertThat(response.getDisplayName()).isEqualTo("Johnny");
    }
    
    @Test
    @DisplayName("Should generate display name when not set")
    void shouldGenerateDisplayName_whenNotSet() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        user.setDisplayName(null);
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        UserResponse response = userMapper.toResponse(user);
        
        // Then
        assertThat(response.getDisplayName()).isEqualTo("John Doe");
    }
    
    @Test
    @DisplayName("Should map all user roles correctly")
    void shouldMapAllUserRoles_correctly() {
        // Given
        User superAdmin = createSuperAdmin("Super", "Admin");
        User tenantAdmin = createTenantAdmin("Tenant", "Admin");
        User regularUser = createRegularUser("Regular", "User");
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        UserResponse superAdminResponse = userMapper.toResponse(superAdmin);
        UserResponse tenantAdminResponse = userMapper.toResponse(tenantAdmin);
        UserResponse regularUserResponse = userMapper.toResponse(regularUser);
        
        // Then
        assertThat(superAdminResponse.getRole()).isEqualTo(SystemRole.SUPER_ADMIN.name());
        assertThat(tenantAdminResponse.getRole()).isEqualTo(SystemRole.TENANT_ADMIN.name());
        assertThat(regularUserResponse.getRole()).isEqualTo(SystemRole.USER.name());
    }
    
    @Test
    @DisplayName("Should map all user statuses correctly")
    void shouldMapAllUserStatuses_correctly() {
        // Given
        User activeUser = createUserWithStatus("Active", "User", UserStatus.ACTIVE);
        User pendingUser = createUserWithStatus("Pending", "User", UserStatus.PENDING_VERIFICATION);
        User suspendedUser = createUserWithStatus("Suspended", "User", UserStatus.SUSPENDED);
        
        when(contactServiceClient.getContactsByOwner(any()))
                .thenReturn(ApiResponse.success(null));
        
        // When
        UserResponse activeResponse = userMapper.toResponse(activeUser);
        UserResponse pendingResponse = userMapper.toResponse(pendingUser);
        UserResponse suspendedResponse = userMapper.toResponse(suspendedUser);
        
        // Then
        assertThat(activeResponse.getStatus()).isEqualTo(UserStatus.ACTIVE.name());
        assertThat(pendingResponse.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION.name());
        assertThat(suspendedResponse.getStatus()).isEqualTo(UserStatus.SUSPENDED.name());
    }
}

