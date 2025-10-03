package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.util.TestDataBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * User Repository Integration Tests
 * 
 * Tests database operations with H2 in-memory database (PostgreSQL mode).
 * Uses TestDataBuilder for consistent test data creation.
 * 
 * For tests requiring real PostgreSQL, use @Import(TestContainersConfiguration.class)
 * and @AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("User Repository Integration Tests")
class UserRepositoryIntegrationTest {

    private static final String TEST_FIRST_NAME = "John";
    private static final String TEST_LAST_NAME = "Doe";
    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        // Clear any existing data
        entityManager.clear();
        
        // Use TestDataBuilder for consistent test data
        testUser = TestDataBuilder.customUser()
                .withTenantId(TEST_TENANT_ID.toString())
                .withName(TEST_FIRST_NAME, TEST_LAST_NAME)
                .withStatus(UserStatus.PENDING_VERIFICATION)
                .withRegistrationType(RegistrationType.DIRECT_REGISTRATION)
                .build();
        
        // Persist and flush to ensure it's in the database
        testUser = entityManager.persistAndFlush(testUser);
        entityManager.clear(); // Clear to ensure fresh fetch from DB
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @BeforeEach
        void setUpNested() {
            // Ensure fresh test data for each nested test
            entityManager.clear();
        }

        @Test
        @DisplayName("Should save and retrieve user successfully")
        void shouldSaveAndRetrieveUserSuccessfully() {
            Optional<User> foundUser = userRepository.findById(testUser.getId());

            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getFirstName()).isEqualTo(TEST_FIRST_NAME);
            assertThat(foundUser.get().getLastName()).isEqualTo(TEST_LAST_NAME);
            assertThat(foundUser.get().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            testUser.updateProfile("Updated", "Name", "Updated Name");
            userRepository.save(testUser);
            
            Optional<User> updatedUser = userRepository.findById(testUser.getId());
            assertThat(updatedUser).isPresent();
            assertThat(updatedUser.get().getFirstName()).isEqualTo("Updated");
            assertThat(updatedUser.get().getLastName()).isEqualTo("Name");
        }

        @Test
        @DisplayName("Should soft delete user successfully")
        void shouldSoftDeleteUserSuccessfully() {
            testUser.markAsDeleted();
            userRepository.save(testUser);
            
            Optional<User> deletedUser = userRepository.findById(testUser.getId());
            assertThat(deletedUser).isPresent();
            assertThat(deletedUser.get().isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Tenant-Based Queries")
    class TenantBasedQueries {

        @BeforeEach
        void setUpNested() {
            entityManager.clear();
        }

        @Test
        @DisplayName("Should find users by tenant ID")
        void shouldFindUsersByTenantId() {
            List<User> users = userRepository.findByTenantId(TEST_TENANT_ID);

            assertThat(users).hasSize(1);
            assertThat(users.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should count active users by tenant")
        void shouldCountActiveUsersByTenant() {
            testUser.activate();
            userRepository.save(testUser);

            long count = userRepository.countActiveUsersByTenant(TEST_TENANT_ID);

            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Should not count inactive users")
        void shouldNotCountInactiveUsers() {
            long count = userRepository.countActiveUsersByTenant(TEST_TENANT_ID);
            assertThat(count).isEqualTo(0); // User is PENDING_VERIFICATION
        }
    }

    @Nested
    @DisplayName("Status-Based Queries")
    class StatusBasedQueries {

        @BeforeEach
        void setUpNested() {
            entityManager.clear();
        }

        @Test
        @DisplayName("Should find users by status")
        void shouldFindUsersByStatus() {
            List<User> pendingUsers = userRepository.findByStatus("PENDING_VERIFICATION");

            assertThat(pendingUsers).hasSize(1);
            assertThat(pendingUsers.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should find users by registration type")
        void shouldFindUsersByRegistrationType() {
            List<User> directUsers = userRepository.findByRegistrationType("DIRECT_REGISTRATION");

            assertThat(directUsers).hasSize(1);
            assertThat(directUsers.get(0).getId()).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {

        @BeforeEach
        void setUpNested() {
            entityManager.clear();
        }

        @Test
        @DisplayName("Should search users by first name")
        void shouldSearchUsersByFirstName() {
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should search users by last name")
        void shouldSearchUsersByLastName() {
            List<User> results = userRepository.searchByName(TEST_LAST_NAME);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should search users by display name")
        void shouldSearchUsersByDisplayName() {
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME + " " + TEST_LAST_NAME);

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should return empty for non-matching search")
        void shouldReturnEmptyForNonMatchingSearch() {
            List<User> results = userRepository.searchByName("NonExistentName");
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should be case insensitive search")
        void shouldBeCaseInsensitiveSearch() {
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME.toLowerCase());

            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }
    }
}
