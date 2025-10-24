package com.fabricmanagement.user.integration.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.fabricmanagement.user.fixtures.UserFixtures.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Integration Tests for UserRepository
 * 
 * Uses Testcontainers for REAL PostgreSQL instance
 * - No mocks, real database queries
 * - Production parity
 * - Tests actual SQL, constraints, triggers
 * 
 * Runtime: ~10 seconds (container startup cached)
 * 
 * Coverage Goal: 85%+
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRepository Integration Tests")
class UserRepositoryIT {
    
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("user_test")
            .withUsername("test")
            .withPassword("test");
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        
        // Required JWT properties for context load
        registry.add("jwt.secret", () -> "test-secret-key-for-user-service-minimum-256-bits-required-for-hmac-sha-algorithm");
        registry.add("jwt.expiration", () -> "3600000");
        registry.add("jwt.refresh-expiration", () -> "86400000");
    }
    
    @Autowired
    private UserRepository userRepository;
    
    // ═════════════════════════════════════════════════════
    // BASIC CRUD OPERATIONS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should save and retrieve user with UUID")
    void shouldSaveAndRetrieveUser_withUUID() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        
        // ✅ CRITICAL: Assert ID is null before persist (Hibernate manages it)
        assertThat(user.getId()).isNull();
        
        // When
        User saved = userRepository.save(user);
        
        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
        assertThat(saved.getFirstName()).isEqualTo("John");
        assertThat(saved.getLastName()).isEqualTo("Doe");
    }
    
    @Test
    @DisplayName("Should auto-generate UUID when ID not set")
    void shouldAutoGenerateUUID_whenIdNotSet() {
        // Given
        User user = createActiveUser("Jane", "Smith", "jane@test.com");
        assertThat(user.getId()).isNull(); // Pre-persist check
        
        // When
        User saved = userRepository.save(user);
        
        // Then
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getVersion()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }
    
    @Test
    @DisplayName("Should update existing user")
    void shouldUpdateExistingUser() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        User saved = userRepository.save(user);
        
        // When
        saved.setFirstName("Johnny");
        User updated = userRepository.save(saved);
        
        // Then
        assertThat(updated.getFirstName()).isEqualTo("Johnny");
        assertThat(updated.getId()).isEqualTo(saved.getId());
    }
    
    @Test
    @DisplayName("Should soft delete user")
    void shouldSoftDeleteUser() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        User saved = userRepository.save(user);
        
        // When
        saved.setDeleted(true);
        saved.setStatus(UserStatus.DELETED);
        User deleted = userRepository.save(saved);
        
        // Then
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getStatus()).isEqualTo(UserStatus.DELETED);
    }
    
    // ═════════════════════════════════════════════════════
    // QUERY METHODS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should find users by tenant ID")
    void shouldFindUsers_byTenantId() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User user1 = createUserForTenant("John", "Doe", tenantId);
        User user2 = createUserForTenant("Jane", "Smith", tenantId);
        User user3 = createUserForTenant("Bob", "Wilson", UUID.randomUUID()); // Different tenant
        
        userRepository.saveAll(Arrays.asList(user1, user2, user3));
        
        // When
        List<User> result = userRepository.findByTenantId(tenantId);
        
        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(User::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane");
    }
    
    @Test
    @DisplayName("Should find active user by ID and tenant ID")
    void shouldFindActiveUser_byIdAndTenantId() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User user = createUserForTenant("John", "Doe", tenantId);
        User saved = userRepository.save(user);
        
        // When
        Optional<User> result = userRepository.findActiveByIdAndTenantId(saved.getId(), tenantId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getFirstName()).isEqualTo("John");
    }
    
    @Test
    @DisplayName("Should NOT find deleted user with active query")
    void shouldNotFindDeletedUser_withActiveQuery() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User user = createUserForTenant("John", "Doe", tenantId);
        user.setDeleted(true);
        User saved = userRepository.save(user);
        
        // When
        Optional<User> result = userRepository.findActiveByIdAndTenantId(saved.getId(), tenantId);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("Should count active users by tenant")
    void shouldCountActiveUsers_byTenant() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User activeUser1 = createUserForTenant("John", "Doe", tenantId);
        User activeUser2 = createUserForTenant("Jane", "Smith", tenantId);
        User deletedUser = createUserForTenant("Bob", "Wilson", tenantId);
        deletedUser.setDeleted(true);
        
        userRepository.saveAll(Arrays.asList(activeUser1, activeUser2, deletedUser));
        
        // When
        long count = userRepository.countActiveUsersByTenant(tenantId);
        
        // Then
        assertThat(count).isEqualTo(2);
    }
    
    // ═════════════════════════════════════════════════════
    // SEARCH METHODS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should search users by name")
    void shouldSearchUsers_byName() {
        // Given
        User john = createActiveUser("John", "Doe", "john@test.com");
        User johnny = createActiveUser("Johnny", "Walker", "johnny@test.com");
        User jane = createActiveUser("Jane", "Smith", "jane@test.com");
        
        userRepository.saveAll(Arrays.asList(john, johnny, jane));
        
        // When
        List<User> result = userRepository.searchByName("John");
        
        // Then
        assertThat(result).hasSizeGreaterThanOrEqualTo(2);
        assertThat(result).extracting(User::getFirstName)
                .contains("John", "Johnny");
    }
    
    @Test
    @DisplayName("Should search users case-insensitively")
    void shouldSearchUsers_caseInsensitively() {
        // Given
        User john = createActiveUser("John", "Doe", "john@test.com");
        userRepository.save(john);
        
        // When
        List<User> resultLower = userRepository.searchByName("john");
        List<User> resultUpper = userRepository.searchByName("JOHN");
        
        // Then
        assertThat(resultLower).hasSize(resultUpper.size());
        assertThat(resultLower).isNotEmpty();
    }
    
    // ═════════════════════════════════════════════════════
    // PAGINATION TESTS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should paginate users by tenant")
    void shouldPaginateUsers_byTenant() {
        // Given
        UUID tenantId = UUID.randomUUID();
        List<User> users = createMultipleUsers(15);
        users.forEach(u -> u.setTenantId(tenantId));
        userRepository.saveAll(users);
        
        PageRequest firstPage = PageRequest.of(0, 10);
        
        // When
        Page<User> result = userRepository.findByTenantIdPaginated(tenantId, firstPage);
        
        // Then
        assertThat(result.getContent()).hasSize(10);
        assertThat(result.getTotalElements()).isEqualTo(15);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.hasNext()).isTrue();
    }
    
    @Test
    @DisplayName("Should search users with pagination and criteria")
    void shouldSearchUsers_withPaginationAndCriteria() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User john1 = createUserForTenant("John", "Doe", tenantId);
        User john2 = createUserForTenant("John", "Smith", tenantId);
        User jane = createUserForTenant("Jane", "Doe", tenantId);
        
        userRepository.saveAll(Arrays.asList(john1, john2, jane));
        
        PageRequest page = PageRequest.of(0, 10);
        
        // When
        Page<User> result = userRepository.searchUsersPaginated(
                tenantId, "John", null, null, page
        );
        
        // Then
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent()).extracting(User::getFirstName)
                .containsOnly("John");
    }
    
    // ═════════════════════════════════════════════════════
    // BATCH OPERATIONS
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should batch get users by IDs")
    void shouldBatchGetUsers_byIds() {
        // Given
        UUID tenantId = UUID.randomUUID();
        List<User> users = createMultipleUsers(5);
        users.forEach(u -> u.setTenantId(tenantId));
        List<User> saved = userRepository.saveAll(users);
        
        List<UUID> userIds = saved.stream()
                .limit(3)
                .map(User::getId)
                .toList();
        
        // When
        List<User> result = userRepository.findAllByIdInAndTenantIdAndStatus(
                userIds, tenantId, UserStatus.ACTIVE
        );
        
        // Then
        assertThat(result).hasSize(3);
    }
    
    @Test
    @DisplayName("Should exclude inactive users in batch query")
    void shouldExcludeInactiveUsers_inBatchQuery() {
        // Given
        UUID tenantId = UUID.randomUUID();
        User activeUser = createUserForTenant("Active", "User", tenantId);
        User inactiveUser = createUserForTenant("Inactive", "User", tenantId);
        inactiveUser.setStatus(UserStatus.INACTIVE);
        
        List<User> saved = userRepository.saveAll(Arrays.asList(activeUser, inactiveUser));
        
        List<UUID> userIds = saved.stream().map(User::getId).toList();
        
        // When
        List<User> result = userRepository.findAllByIdInAndTenantIdAndStatus(
                userIds, tenantId, UserStatus.ACTIVE
        );
        
        // Then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFirstName()).isEqualTo("Active");
    }
    
    // ═════════════════════════════════════════════════════
    // STATUS FILTERING
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should find users by status")
    void shouldFindUsers_byStatus() {
        // Given
        List<User> users = createUsersWithDifferentStatuses();
        userRepository.saveAll(users);
        
        // When
        List<User> activeUsers = userRepository.findByStatus(UserStatus.ACTIVE.name());
        
        // Then
        assertThat(activeUsers).isNotEmpty();
        assertThat(activeUsers).allMatch(u -> u.getStatus() == UserStatus.ACTIVE);
    }
    
    // ═════════════════════════════════════════════════════
    // TRANSACTIONAL BEHAVIOR
    // ═════════════════════════════════════════════════════
    
    @Test
    @DisplayName("Should maintain version for optimistic locking")
    void shouldMaintainVersion_forOptimisticLocking() {
        // Given
        User user = createActiveUser("John", "Doe", "john@test.com");
        User saved = userRepository.save(user);
        Long initialVersion = saved.getVersion();
        
        // When
        saved.setFirstName("Johnny");
        User updated = userRepository.save(saved);
        
        // Then
        assertThat(updated.getVersion()).isGreaterThan(initialVersion);
    }
}

