package com.fabricmanagement.user.domain.aggregate;

import com.fabricmanagement.user.domain.valueobject.UserContact;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.domain.event.UserCreatedEvent;
import com.fabricmanagement.user.domain.event.UserUpdatedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for User Aggregate Root
 * 
 * Tests all business logic and domain rules
 */
@DisplayName("User Aggregate Tests")
class UserTest {

    private static final String VALID_EMAIL = "test@example.com";
    private static final String VALID_PHONE = "+905551234567";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final String PASSWORD_HASH = "hashedPassword123";

    @Nested
    @DisplayName("User Creation Tests")
    class UserCreationTests {

        @Test
        @DisplayName("Should create user with contact verification successfully")
        void shouldCreateUserWithContactVerification() {
            // When
            User user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );

            // Then
            assertThat(user).isNotNull();
            assertThat(user.getFirstName()).isEqualTo(FIRST_NAME);
            assertThat(user.getLastName()).isEqualTo(LAST_NAME);
            assertThat(user.getDisplayName()).isEqualTo(FIRST_NAME + " " + LAST_NAME);
            assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_VERIFICATION);
            assertThat(user.getRegistrationType()).isEqualTo(RegistrationType.DIRECT_REGISTRATION);
            assertThat(user.getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(user.getContacts()).hasSize(1);
            
            UserContact contact = user.getContacts().get(0);
            assertThat(contact.getContactValue()).isEqualTo(VALID_EMAIL);
            assertThat(contact.getContactType()).isEqualTo(UserContact.ContactType.EMAIL);
            assertThat(contact.isVerified()).isFalse();
            assertThat(contact.isPrimary()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when contact value is null")
        void shouldThrowExceptionWhenContactValueIsNull() {
            // When & Then
            assertThatThrownBy(() -> 
                User.createWithContactVerification(null, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessage("Contact value cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception when contact value is empty")
        void shouldThrowExceptionWhenContactValueIsEmpty() {
            // When & Then
            assertThatThrownBy(() -> 
                User.createWithContactVerification("", "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessage("Contact value cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception when first name is null")
        void shouldThrowExceptionWhenFirstNameIsNull() {
            // When & Then
            assertThatThrownBy(() -> 
                User.createWithContactVerification(VALID_EMAIL, "EMAIL", null, LAST_NAME, PASSWORD_HASH, "EMPLOYEE")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessage("First name cannot be null or empty");
        }

        @Test
        @DisplayName("Should throw exception when password hash is null")
        void shouldThrowExceptionWhenPasswordHashIsNull() {
            // When & Then
            assertThatThrownBy(() -> 
                User.createWithContactVerification(VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, null, "EMPLOYEE")
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessage("Password hash cannot be null or empty");
        }

        @Test
        @DisplayName("Should publish UserCreatedEvent when user is created")
        void shouldPublishUserCreatedEventWhenUserIsCreated() {
            // When
            User user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );

            // Then
            List<Object> events = user.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(UserCreatedEvent.class);
            
            UserCreatedEvent event = (UserCreatedEvent) events.get(0);
            assertThat(event.getContactValue()).isEqualTo(VALID_EMAIL);
            assertThat(event.getFirstName()).isEqualTo(FIRST_NAME);
            assertThat(event.getLastName()).isEqualTo(LAST_NAME);
        }
    }

    @Nested
    @DisplayName("Contact Verification Tests")
    class ContactVerificationTests {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            user.getAndClearDomainEvents(); // Clear events from setup
        }

        @Test
        @DisplayName("Should verify contact and activate user successfully")
        void shouldVerifyContactAndActivateUser() {
            // When
            user.verifyContactAndActivate(VALID_EMAIL);

            // Then
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            
            UserContact contact = user.getContacts().get(0);
            assertThat(contact.isVerified()).isTrue();
            
            List<Object> events = user.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(UserUpdatedEvent.class);
        }

        @Test
        @DisplayName("Should throw exception when user is not in PENDING_VERIFICATION status")
        void shouldThrowExceptionWhenUserNotInPendingVerificationStatus() {
            // Given
            user.verifyContactAndActivate(VALID_EMAIL); // User is now ACTIVE

            // When & Then
            assertThatThrownBy(() -> user.verifyContactAndActivate(VALID_EMAIL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User must be in PENDING_VERIFICATION status");
        }

        @Test
        @DisplayName("Should throw exception when contact value not found")
        void shouldThrowExceptionWhenContactValueNotFound() {
            // When & Then
            assertThatThrownBy(() -> user.verifyContactAndActivate("nonexistent@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contact not found");
        }
    }

    @Nested
    @DisplayName("Password Reset Tests")
    class PasswordResetTests {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            user.verifyContactAndActivate(VALID_EMAIL); // Activate user
            user.getAndClearDomainEvents(); // Clear events from setup
        }

        @Test
        @DisplayName("Should reset password successfully")
        void shouldResetPasswordSuccessfully() {
            // Given
            String newPasswordHash = "newHashedPassword456";

            // When
            user.resetPassword(newPasswordHash);

            // Then
            assertThat(user.getPasswordHash()).isEqualTo(newPasswordHash);
            
            List<Object> events = user.getAndClearDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(UserUpdatedEvent.class);
        }

        @Test
        @DisplayName("Should throw exception when user is not active")
        void shouldThrowExceptionWhenUserNotActive() {
            // Given
            User inactiveUser = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            // User is still PENDING_VERIFICATION

            // When & Then
            assertThatThrownBy(() -> inactiveUser.resetPassword("newPassword"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User must be active to reset password");
        }
    }

    @Nested
    @DisplayName("Contact Management Tests")
    class ContactManagementTests {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            user.verifyContactAndActivate(VALID_EMAIL); // Activate user
            user.getAndClearDomainEvents(); // Clear events from setup
        }

        @Test
        @DisplayName("Should add new contact successfully")
        void shouldAddNewContactSuccessfully() {
            // When
            user.addContact(VALID_PHONE, UserContact.ContactType.PHONE);

            // Then
            assertThat(user.getContacts()).hasSize(2);
            
            UserContact phoneContact = user.getContacts().stream()
                .filter(c -> c.getContactValue().equals(VALID_PHONE))
                .findFirst()
                .orElseThrow();
            
            assertThat(phoneContact.getContactValue()).isEqualTo(VALID_PHONE);
            assertThat(phoneContact.getContactType()).isEqualTo(UserContact.ContactType.PHONE);
            assertThat(phoneContact.isVerified()).isFalse();
            assertThat(phoneContact.isPrimary()).isFalse();
        }

        @Test
        @DisplayName("Should throw exception when contact already exists")
        void shouldThrowExceptionWhenContactAlreadyExists() {
            // When & Then
            assertThatThrownBy(() -> user.addContact(VALID_EMAIL, UserContact.ContactType.EMAIL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contact already exists");
        }

        @Test
        @DisplayName("Should throw exception when user is not active")
        void shouldThrowExceptionWhenUserNotActive() {
            // Given
            User inactiveUser = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            // User is still PENDING_VERIFICATION

            // When & Then
            assertThatThrownBy(() -> inactiveUser.addContact(VALID_PHONE, UserContact.ContactType.PHONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User must be active to add contacts");
        }

        @Test
        @DisplayName("Should verify additional contact successfully")
        void shouldVerifyAdditionalContactSuccessfully() {
            // Given
            user.addContact(VALID_PHONE, UserContact.ContactType.PHONE);

            // When
            user.verifyContact(VALID_PHONE);

            // Then
            UserContact phoneContact = user.getContacts().stream()
                .filter(c -> c.getContactValue().equals(VALID_PHONE))
                .findFirst()
                .orElseThrow();
            
            assertThat(phoneContact.isVerified()).isTrue();
        }

        @Test
        @DisplayName("Should throw exception when verifying non-existent contact")
        void shouldThrowExceptionWhenVerifyingNonExistentContact() {
            // When & Then
            assertThatThrownBy(() -> user.verifyContact("nonexistent@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Contact not found");
        }
    }

    @Nested
    @DisplayName("User Status Tests")
    class UserStatusTests {

        private User user;

        @BeforeEach
        void setUp() {
            user = User.createWithContactVerification(
                VALID_EMAIL, "EMAIL", FIRST_NAME, LAST_NAME, PASSWORD_HASH, "EMPLOYEE"
            );
            user.getAndClearDomainEvents(); // Clear events from setup
        }

        @Test
        @DisplayName("Should be active when status is ACTIVE and not deleted")
        void shouldBeActiveWhenStatusIsActiveAndNotDeleted() {
            // Given
            user.verifyContactAndActivate(VALID_EMAIL);

            // When & Then
            assertThat(user.isActive()).isTrue();
        }

        @Test
        @DisplayName("Should not be active when status is PENDING_VERIFICATION")
        void shouldNotBeActiveWhenStatusIsPendingVerification() {
            // When & Then
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should not be active when user is deleted")
        void shouldNotBeActiveWhenUserIsDeleted() {
            // Given
            user.verifyContactAndActivate(VALID_EMAIL);
            user.markAsDeleted();

            // When & Then
            assertThat(user.isActive()).isFalse();
        }

        @Test
        @DisplayName("Should get full name correctly")
        void shouldGetFullNameCorrectly() {
            // When & Then
            assertThat(user.getFullName()).isEqualTo(FIRST_NAME + " " + LAST_NAME);
        }

        @Test
        @DisplayName("Should get primary contact correctly")
        void shouldGetPrimaryContactCorrectly() {
            // When & Then
            assertThat(user.getPrimaryContact()).isEqualTo(VALID_EMAIL);
        }

        @Test
        @DisplayName("Should get verified contacts only")
        void shouldGetVerifiedContactsOnly() {
            // Given
            user.verifyContactAndActivate(VALID_EMAIL);
            user.addContact(VALID_PHONE, UserContact.ContactType.PHONE);
            // Phone contact is not verified

            // When
            List<UserContact> verifiedContacts = user.getVerifiedContacts();

            // Then
            assertThat(verifiedContacts).hasSize(1);
            assertThat(verifiedContacts.get(0).getContactValue()).isEqualTo(VALID_EMAIL);
        }
    }
}
