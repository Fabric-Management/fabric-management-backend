package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
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
 * Tests database operations with real JPA/Hibernate
 * Note: Contact-related tests removed - use ContactServiceClient instead
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("User Repository Integration Tests")
class UserRepositoryIntegrationTest {

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_FIRST_NAME = "John";
    private static final String TEST_LAST_NAME = "Doe";
    private static final String TEST_PASSWORD_HASH = "$2a$10$hashedpassword123";
    private static final UUID TEST_TENANT_ID = UUID.randomUUID();

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(UUID.randomUUID())
                .tenantId(TEST_TENANT_ID.toString())
                .firstName(TEST_FIRST_NAME)
                .lastName(TEST_LAST_NAME)
                .displayName(TEST_FIRST_NAME + " " + TEST_LAST_NAME)
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .passwordHash(TEST_PASSWORD_HASH)
                .deleted(false)
                .version(0L)
                .build();
        
        entityManager.persistAndFlush(testUser);
        entityManager.clear();
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

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
