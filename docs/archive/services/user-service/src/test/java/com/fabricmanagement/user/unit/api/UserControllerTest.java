package com.fabricmanagement.user.unit.api;

import com.fabricmanagement.shared.application.context.SecurityContext;
import com.fabricmanagement.shared.application.response.ApiResponse;
import com.fabricmanagement.shared.application.response.PagedResponse;
import com.fabricmanagement.user.api.UserController;
import com.fabricmanagement.user.api.dto.request.CreateUserRequest;
import com.fabricmanagement.user.api.dto.request.InviteUserRequest;
import com.fabricmanagement.user.api.dto.request.UpdateUserRequest;
import com.fabricmanagement.user.api.dto.response.UserInvitationResponse;
import com.fabricmanagement.user.api.dto.response.UserResponse;
import com.fabricmanagement.user.application.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit Tests for UserController
 * 
 * Tests HTTP layer behavior without Spring context
 * 
 * Strategy:
 * - Mock UserService
 * - Test request/response handling
 * - Verify API contracts
 * - Test authorization mapping (role-based)
 * 
 * Coverage Goal: 85%+
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController - Unit Tests")
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private static final UUID TEST_TENANT_ID = UUID.fromString("a1b2c3d4-e5f6-7890-1234-567890abcdef");
    private static final String TEST_USER_ID = "test-user-123";

    private SecurityContext createSecurityContext() {
        return SecurityContext.builder()
                .userId(TEST_USER_ID)
                .tenantId(TEST_TENANT_ID)
                .roles(new String[]{"USER"})
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // GET USER TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get User Tests")
    class GetUserTests {

        @Test
        @DisplayName("Should return user when valid ID provided")
        @SuppressWarnings("null")
        void shouldReturnUser_whenValidId() {
            // Given
            UUID userId = UUID.randomUUID();
            UserResponse expectedResponse = UserResponse.builder()
                    .id(userId)
                    .firstName("John")
                    .lastName("Doe")
                    .build();

            when(userService.getUser(userId, TEST_TENANT_ID))
                    .thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<UserResponse>> response = 
                userController.getUser(userId, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            verify(userService).getUser(userId, TEST_TENANT_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // USER EXISTS TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("User Exists Tests")
    class UserExistsTests {

        @Test
        @DisplayName("Should return true when user exists")
        @SuppressWarnings("null")
        void shouldReturnTrue_whenUserExists() {
            // Given
            UUID userId = UUID.randomUUID();
            when(userService.userExists(userId, TEST_TENANT_ID)).thenReturn(true);

            // When
            ResponseEntity<ApiResponse<Boolean>> response = 
                userController.userExists(userId, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).isTrue();
            verify(userService).userExists(userId, TEST_TENANT_ID);
        }

        @Test
        @DisplayName("Should return false when user does not exist")
        @SuppressWarnings("null")
        void shouldReturnFalse_whenUserDoesNotExist() {
            // Given
            UUID userId = UUID.randomUUID();
            when(userService.userExists(userId, TEST_TENANT_ID)).thenReturn(false);

            // When
            ResponseEntity<ApiResponse<Boolean>> response = 
                userController.userExists(userId, createSecurityContext());

            // Then
            assertThat(response.getBody().getData()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════
    // CREATE USER TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Create User Tests")
    class CreateUserTests {

        @Test
        @DisplayName("Should create user and return 201")
        @SuppressWarnings("null")
        void shouldCreateUser() {
            // Given
            CreateUserRequest request = CreateUserRequest.builder()
                    .firstName("John")
                    .lastName("Doe")
                    .email("john@example.com")
                    .build();

            UUID userId = UUID.randomUUID();
            when(userService.createUser(any(), any(), any())).thenReturn(userId);

            // When
            ResponseEntity<ApiResponse<UUID>> response = 
                userController.createUser(request, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(userId);
            verify(userService).createUser(request, TEST_TENANT_ID, TEST_USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // INVITE USER TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Invite User Tests")
    class InviteUserTests {

        @Test
        @DisplayName("Should invite user and return 201")
        @SuppressWarnings("null")
        void shouldInviteUser() {
            // Given
            InviteUserRequest request = InviteUserRequest.builder()
                    .email("newuser@example.com")
                    .firstName("Jane")
                    .lastName("Smith")
                    .build();

            UserInvitationResponse expectedResponse = UserInvitationResponse.builder()
                    .userId(UUID.randomUUID())
                    .emailContactId(UUID.randomUUID())
                    .verificationSent(true)
                    .message("Invitation sent successfully")
                    .build();

            when(userService.inviteUser(any(), any(), any())).thenReturn(expectedResponse);

            // When
            ResponseEntity<ApiResponse<UserInvitationResponse>> response = 
                userController.inviteUser(request, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            assertThat(response.getBody().getData()).isEqualTo(expectedResponse);
            verify(userService).inviteUser(request, TEST_TENANT_ID, TEST_USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // UPDATE USER TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Update User Tests")
    class UpdateUserTests {

        @Test
        @DisplayName("Should update user and return 200")
        @SuppressWarnings("null")
        void shouldUpdateUser() {
            // Given
            UUID userId = UUID.randomUUID();
            UpdateUserRequest request = UpdateUserRequest.builder()
                    .firstName("Updated")
                    .lastName("Name")
                    .build();

            // When
            ResponseEntity<ApiResponse<Void>> response = 
                userController.updateUser(userId, request, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(userService).updateUser(userId, request, TEST_TENANT_ID, TEST_USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // DELETE USER TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Delete User Tests")
    class DeleteUserTests {

        @Test
        @DisplayName("Should delete user and return 200")
        @SuppressWarnings("null")
        void shouldDeleteUser() {
            // Given
            UUID userId = UUID.randomUUID();

            // When
            ResponseEntity<ApiResponse<Void>> response = 
                userController.deleteUser(userId, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isTrue();
            verify(userService).deleteUser(userId, TEST_TENANT_ID, TEST_USER_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // LIST USERS TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("List Users Tests")
    class ListUsersTests {

        @Test
        @DisplayName("Should return list of users")
        @SuppressWarnings("null")
        void shouldReturnUserList() {
            // Given
            List<UserResponse> users = Arrays.asList(
                UserResponse.builder().id(UUID.randomUUID()).firstName("User1").build(),
                UserResponse.builder().id(UUID.randomUUID()).firstName("User2").build()
            );

            when(userService.listUsers(TEST_TENANT_ID)).thenReturn(users);

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.listUsers(createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(2);
            verify(userService).listUsers(TEST_TENANT_ID);
        }

        @Test
        @DisplayName("Should return empty list when no users")
        @SuppressWarnings("null")
        void shouldReturnEmptyList_whenNoUsers() {
            // Given
            when(userService.listUsers(TEST_TENANT_ID)).thenReturn(List.of());

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.listUsers(createSecurityContext());

            // Then
            assertThat(response.getBody().getData()).isEmpty();
        }
    }

    // ═══════════════════════════════════════════════════════
    // LIST USERS PAGINATED TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("List Users Paginated Tests")
    class ListUsersPaginatedTests {

        @Test
        @DisplayName("Should return paginated users with default params")
        @SuppressWarnings("null")
        void shouldReturnPaginatedUsers_withDefaults() {
            // Given
            List<UserResponse> content = List.of(UserResponse.builder().id(UUID.randomUUID()).build());
            PagedResponse<UserResponse> pagedResponse = PagedResponse.of(content, 0, 20, 1, 1);

            when(userService.listUsersPaginated(any(), any())).thenReturn(pagedResponse);

            // When
            ResponseEntity<PagedResponse<UserResponse>> response = 
                userController.listUsersPaginated(0, 20, "createdAt,desc", createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            verify(userService).listUsersPaginated(eq(TEST_TENANT_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("Should handle custom page and size")
        @SuppressWarnings("null")
        void shouldHandleCustomPageAndSize() {
            // Given
            PagedResponse<UserResponse> pagedResponse = PagedResponse.of(List.of(), 2, 50, 0, 5);

            when(userService.listUsersPaginated(any(), any())).thenReturn(pagedResponse);

            // When
            ResponseEntity<PagedResponse<UserResponse>> response = 
                userController.listUsersPaginated(2, 50, "firstName,asc", createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userService).listUsersPaginated(eq(TEST_TENANT_ID), any(Pageable.class));
        }
    }

    // ═══════════════════════════════════════════════════════
    // SEARCH USERS TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search Users Tests")
    class SearchUsersTests {

        @Test
        @DisplayName("Should search users by firstName")
        @SuppressWarnings("null")
        void shouldSearchByFirstName() {
            // Given
            List<UserResponse> users = List.of(
                UserResponse.builder().id(UUID.randomUUID()).firstName("John").build()
            );

            when(userService.searchUsers(TEST_TENANT_ID, "John", null, null, null))
                    .thenReturn(users);

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.searchUsers("John", null, null, null, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(1);
            verify(userService).searchUsers(TEST_TENANT_ID, "John", null, null, null);
        }

        @Test
        @DisplayName("Should search users by multiple criteria")
        @SuppressWarnings("null")
        void shouldSearchByMultipleCriteria() {
            // Given
            when(userService.searchUsers(TEST_TENANT_ID, "John", "Doe", "john@test.com", "ACTIVE"))
                    .thenReturn(List.of());

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.searchUsers("John", "Doe", "john@test.com", "ACTIVE", createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(userService).searchUsers(TEST_TENANT_ID, "John", "Doe", "john@test.com", "ACTIVE");
        }
    }

    // ═══════════════════════════════════════════════════════
    // SEARCH USERS PAGINATED TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Search Users Paginated Tests")
    class SearchUsersPaginatedTests {

        @Test
        @DisplayName("Should search users with pagination")
        @SuppressWarnings("null")
        void shouldSearchWithPagination() {
            // Given
            List<UserResponse> content = List.of(UserResponse.builder().id(UUID.randomUUID()).firstName("John").build());
            PagedResponse<UserResponse> pagedResponse = PagedResponse.of(content, 0, 20, 1, 1);

            when(userService.searchUsersPaginated(any(), any(), any(), any(), any()))
                    .thenReturn(pagedResponse);

            // When
            ResponseEntity<PagedResponse<UserResponse>> response = 
                userController.searchUsersPaginated("John", null, null, 0, 20, "createdAt,desc", createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getContent()).hasSize(1);
            verify(userService).searchUsersPaginated(eq(TEST_TENANT_ID), eq("John"), isNull(), isNull(), any(Pageable.class));
        }
    }

    // ═══════════════════════════════════════════════════════
    // GET USERS BY COMPANY TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Users By Company Tests")
    class GetUsersByCompanyTests {

        @Test
        @DisplayName("Should return users for company")
        @SuppressWarnings("null")
        void shouldReturnUsersForCompany() {
            // Given
            UUID companyId = UUID.randomUUID();
            List<UserResponse> users = List.of(
                UserResponse.builder().id(UUID.randomUUID()).build()
            );

            when(userService.getUsersByTenant(TEST_TENANT_ID)).thenReturn(users);

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.getUsersByCompany(companyId, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(1);
            verify(userService).getUsersByTenant(TEST_TENANT_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // GET USER COUNT FOR COMPANY TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get User Count For Company Tests")
    class GetUserCountForCompanyTests {

        @Test
        @DisplayName("Should return user count for company")
        @SuppressWarnings("null")
        void shouldReturnUserCount() {
            // Given
            UUID companyId = UUID.randomUUID();
            when(userService.getUserCountForTenant(TEST_TENANT_ID)).thenReturn(42);

            // When
            ResponseEntity<ApiResponse<Integer>> response = 
                userController.getUserCountForCompany(companyId, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).isEqualTo(42);
            verify(userService).getUserCountForTenant(TEST_TENANT_ID);
        }
    }

    // ═══════════════════════════════════════════════════════
    // GET USERS BATCH TESTS
    // ═══════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Users Batch Tests")
    class GetUsersBatchTests {

        @Test
        @DisplayName("Should return batch of users")
        @SuppressWarnings("null")
        void shouldReturnBatchOfUsers() {
            // Given
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            List<UUID> userIds = Arrays.asList(id1, id2);

            List<UserResponse> users = Arrays.asList(
                UserResponse.builder().id(id1).build(),
                UserResponse.builder().id(id2).build()
            );

            when(userService.getUsersBatch(userIds, TEST_TENANT_ID)).thenReturn(users);

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.getUsersBatch(userIds, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getData()).hasSize(2);
            verify(userService).getUsersBatch(userIds, TEST_TENANT_ID);
        }

        @Test
        @DisplayName("Should return empty list when empty IDs")
        @SuppressWarnings("null")
        void shouldReturnEmptyList_whenEmptyIds() {
            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.getUsersBatch(List.of(), createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().getData()).isEmpty();
        }

        @Test
        @DisplayName("Should reject batch request exceeding 100 users")
        @SuppressWarnings("null")
        void shouldRejectLargeBatch() {
            // Given
            List<UUID> tooManyIds = new java.util.ArrayList<>();
            for (int i = 0; i < 101; i++) {
                tooManyIds.add(UUID.randomUUID());
            }

            // When
            ResponseEntity<ApiResponse<List<UserResponse>>> response = 
                userController.getUsersBatch(tooManyIds, createSecurityContext());

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getSuccess()).isFalse();
            assertThat(response.getBody().getErrorCode()).isEqualTo("BATCH_SIZE_EXCEEDED");
        }
    }
}

