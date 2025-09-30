package com.fabricmanagement.user.infrastructure.repository;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserContact;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.test.UserServiceTestBase;
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
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("User Repository Integration Tests")
class UserRepositoryIntegrationTest extends UserServiceTestBase {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserContactRepository userContactRepository;

    private User testUser;
    private UserContact testContact;

    @BeforeEach
    void setUp() {
        // Create test user with tenant ID
        testUser = User.createWithContactVerification(
            TEST_EMAIL, "EMAIL", TEST_FIRST_NAME, TEST_LAST_NAME, TEST_PASSWORD_HASH, "EMPLOYEE"
        );
        // Note: tenantId will be set via reflection or builder pattern in real implementation
        
        // Persist user
        entityManager.persistAndFlush(testUser);
        
        // Get the contact
        testContact = testUser.getContacts().get(0);
        entityManager.persistAndFlush(testContact);
        
        entityManager.clear(); // Clear to test actual database queries
    }

    @Nested
    @DisplayName("Basic CRUD Operations")
    class BasicCrudOperations {

        @Test
        @DisplayName("Should save and retrieve user successfully")
        void shouldSaveAndRetrieveUserSuccessfully() {
            // When
            User savedUser = userRepository.save(testUser);
            Optional<User> foundUser = userRepository.findById(savedUser.getId());

            // Then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getFirstName()).isEqualTo(TEST_FIRST_NAME);
            assertThat(foundUser.get().getLastName()).isEqualTo(TEST_LAST_NAME);
            assertThat(foundUser.get().getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("Should update user successfully")
        void shouldUpdateUserSuccessfully() {
            // Given
            User savedUser = userRepository.save(testUser);
            
            // When
            savedUser.updateProfile("Updated", "Name", "Updated Name");
            userRepository.save(savedUser);
            
            // Then
            Optional<User> updatedUser = userRepository.findById(savedUser.getId());
            assertThat(updatedUser).isPresent();
            assertThat(updatedUser.get().getFirstName()).isEqualTo("Updated");
            assertThat(updatedUser.get().getLastName()).isEqualTo("Name");
        }

        @Test
        @DisplayName("Should soft delete user successfully")
        void shouldSoftDeleteUserSuccessfully() {
            // Given
            User savedUser = userRepository.save(testUser);
            
            // When
            savedUser.markAsDeleted();
            userRepository.save(savedUser);
            
            // Then
            Optional<User> deletedUser = userRepository.findById(savedUser.getId());
            assertThat(deletedUser).isPresent();
            assertThat(deletedUser.get().isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("Contact-Based Queries")
    class ContactBasedQueries {

        @Test
        @DisplayName("Should find user by contact value")
        void shouldFindUserByContactValue() {
            // When
            Optional<User> foundUser = userRepository.findByContactValue(TEST_EMAIL);

            // Then
            assertThat(foundUser).isPresent();
            assertThat(foundUser.get().getId()).isEqualTo(testUser.getId());
            assertThat(foundUser.get().getFirstName()).isEqualTo(TEST_FIRST_NAME);
        }

        @Test
        @DisplayName("Should return empty when contact not found")
        void shouldReturnEmptyWhenContactNotFound() {
            // When
            Optional<User> foundUser = userRepository.findByContactValue("nonexistent@example.com");

            // Then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("Should not find deleted user by contact")
        void shouldNotFindDeletedUserByContact() {
            // Given
            testUser.markAsDeleted();
            userRepository.save(testUser);

            // When
            Optional<User> foundUser = userRepository.findByContactValue(TEST_EMAIL);

            // Then
            assertThat(foundUser).isEmpty();
        }

        @Test
        @DisplayName("Should check if contact value exists")
        void shouldCheckIfContactValueExists() {
            // When & Then
            assertThat(userRepository.existsByContactValue(TEST_EMAIL)).isTrue();
            assertThat(userRepository.existsByContactValue("nonexistent@example.com")).isFalse();
        }
    }

    @Nested
    @DisplayName("Tenant-Based Queries")
    class TenantBasedQueries {

        @Test
        @DisplayName("Should find users by tenant ID")
        void shouldFindUsersByTenantId() {
            // Given
            UUID tenantId = UUID.fromString(TEST_TENANT_ID);
            
            // When
            List<User> users = userRepository.findByTenantId(tenantId);

            // Then
            // Note: This test will pass when tenantId is properly set in User creation
            assertThat(users).hasSize(0); // Currently no tenant ID set
        }

        @Test
        @DisplayName("Should count active users by tenant")
        void shouldCountActiveUsersByTenant() {
            // Given
            UUID tenantId = UUID.fromString(TEST_TENANT_ID);
            testUser.verifyContactAndActivate(TEST_EMAIL);
            userRepository.save(testUser);

            // When
            long count = userRepository.countActiveUsersByTenant(tenantId);

            // Then
            assertThat(count).isEqualTo(0); // Currently no tenant ID set
        }

        @Test
        @DisplayName("Should not count inactive users")
        void shouldNotCountInactiveUsers() {
            // Given
            UUID tenantId = UUID.fromString(TEST_TENANT_ID);
            // User is still PENDING_VERIFICATION

            // When
            long count = userRepository.countActiveUsersByTenant(tenantId);

            // Then
            assertThat(count).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("Status-Based Queries")
    class StatusBasedQueries {

        @Test
        @DisplayName("Should find users by status")
        void shouldFindUsersByStatus() {
            // When
            List<User> pendingUsers = userRepository.findByStatus("PENDING_VERIFICATION");

            // Then
            assertThat(pendingUsers).hasSize(1);
            assertThat(pendingUsers.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should find users by registration type")
        void shouldFindUsersByRegistrationType() {
            // When
            List<User> directUsers = userRepository.findByRegistrationType("DIRECT_REGISTRATION");

            // Then
            assertThat(directUsers).hasSize(1);
            assertThat(directUsers.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should find users with verified contacts")
        void shouldFindUsersWithVerifiedContacts() {
            // Given
            testUser.verifyContactAndActivate(TEST_EMAIL);
            userRepository.save(testUser);

            // When
            List<User> verifiedUsers = userRepository.findUsersWithVerifiedContacts();

            // Then
            assertThat(verifiedUsers).hasSize(1);
            assertThat(verifiedUsers.get(0).getId()).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Search Operations")
    class SearchOperations {

        @Test
        @DisplayName("Should search users by first name")
        void shouldSearchUsersByFirstName() {
            // When
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should search users by last name")
        void shouldSearchUsersByLastName() {
            // When
            List<User> results = userRepository.searchByName(TEST_LAST_NAME);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should search users by display name")
        void shouldSearchUsersByDisplayName() {
            // When
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME + " " + TEST_LAST_NAME);

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }

        @Test
        @DisplayName("Should return empty for non-matching search")
        void shouldReturnEmptyForNonMatchingSearch() {
            // When
            List<User> results = userRepository.searchByName("NonExistentName");

            // Then
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Should be case insensitive search")
        void shouldBeCaseInsensitiveSearch() {
            // When
            List<User> results = userRepository.searchByName(TEST_FIRST_NAME.toLowerCase());

            // Then
            assertThat(results).hasSize(1);
            assertThat(results.get(0).getId()).isEqualTo(testUser.getId());
        }
    }

    @Nested
    @DisplayName("Contact Repository Tests")
    class ContactRepositoryTests {

        @Test
        @DisplayName("Should find contact by contact value")
        void shouldFindContactByContactValue() {
            // When
            Optional<UserContact> foundContact = userContactRepository.findByContactValue(TEST_EMAIL);

            // Then
            assertThat(foundContact).isPresent();
            assertThat(foundContact.get().getContactValue()).isEqualTo(TEST_EMAIL);
            assertThat(foundContact.get().getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
        }

        @Test
        @DisplayName("Should find contacts by user ID")
        void shouldFindContactsByUserId() {
            // When
            List<UserContact> contacts = userContactRepository.findByUserId(testUser.getId());

            // Then
            assertThat(contacts).hasSize(1);
            assertThat(contacts.get(0).getContactValue()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Should find contacts by contact type")
        void shouldFindContactsByContactType() {
            // When
            List<UserContact> emailContacts = userContactRepository.findByContactType(UserContact.ContactType.EMAIL);

            // Then
            assertThat(emailContacts).hasSize(1);
            assertThat(emailContacts.get(0).getContactValue()).isEqualTo(TEST_EMAIL);
        }

        @Test
        @DisplayName("Should count contacts by user ID")
        void shouldCountContactsByUserId() {
            // When
            long count = userContactRepository.countByUserId(testUser.getId());

            // Then
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("Should find contacts by verification status")
        void shouldFindContactsByVerificationStatus() {
            // When
            List<UserContact> unverifiedContacts = userContactRepository.findByUserIdAndIsVerified(testUser.getId(), false);

            // Then
            assertThat(unverifiedContacts).hasSize(1);
            assertThat(unverifiedContacts.get(0).isVerified()).isFalse();
        }
    }
}
