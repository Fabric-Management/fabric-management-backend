package com.fabricmanagement.identity.domain.model;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.UserRole;
import com.fabricmanagement.identity.domain.valueobject.UserStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for User domain model.
 */
class UserTest {

    @Test
    void shouldCreateUserSuccessfully() {
        // Given
        UUID tenantId = UUID.randomUUID();
        String username = "testuser";
        String firstName = "John";
        String lastName = "Doe";
        UserRole role = UserRole.USER;
        String createdBy = "system";

        // When
        User user = User.create(tenantId, username, firstName, lastName, role, createdBy);

        // Then
        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals(tenantId, user.getTenantId());
        assertEquals(username, user.getUsername());
        assertEquals(firstName, user.getFirstName());
        assertEquals(lastName, user.getLastName());
        assertEquals(role, user.getRole());
        assertEquals(UserStatus.PENDING_ACTIVATION, user.getStatus());
        assertFalse(user.isTwoFactorEnabled());
        assertEquals(0, user.getFailedLoginAttempts());
        assertFalse(user.isPasswordMustChange());
        assertTrue(user.getContacts().isEmpty());
        assertFalse(user.getDomainEvents().isEmpty());
    }

    @Test
    void shouldAddContactSuccessfully() {
        // Given
        User user = createTestUser();
        String email = "test@example.com";

        // When
        user.addContact(ContactType.EMAIL, email, "system");

        // Then
        assertEquals(1, user.getContacts().size());
        UserContact contact = user.getContacts().get(0);
        assertEquals(ContactType.EMAIL, contact.getType());
        assertEquals(email, contact.getValue());
        assertTrue(contact.isPrimary()); // First contact becomes primary
        assertFalse(contact.isVerified());
    }

    @Test
    void shouldVerifyContactSuccessfully() {
        // Given
        User user = createTestUser();
        String email = "test@example.com";
        user.addContact(ContactType.EMAIL, email, "system");
        
        var verificationToken = user.initiateContactVerification(email);

        // When
        boolean result = user.verifyContact(email, verificationToken.getVerificationValue());

        // Then
        assertTrue(result);
        UserContact contact = user.getContacts().get(0);
        assertTrue(contact.isVerified());
        assertEquals(UserStatus.ACTIVE, user.getStatus()); // User activated after first verification
    }

    @Test
    void shouldCreateInitialPasswordAfterVerification() {
        // Given
        User user = createTestUser();
        String email = "test@example.com";
        user.addContact(ContactType.EMAIL, email, "system");
        
        var verificationToken = user.initiateContactVerification(email);
        user.verifyContact(email, verificationToken.getVerificationValue());

        String password = "TestPassword123!";

        // When
        user.createInitialPassword(password);

        // Then
        assertNotNull(user.getCredentials());
        assertTrue(user.getCredentials().hasPassword());
        assertTrue(user.getCredentials().matches(password));
        assertFalse(user.isPasswordMustChange());
    }

    @Test
    void shouldAuthenticateSuccessfully() {
        // Given
        User user = createTestUserWithPassword();
        String email = "test@example.com";
        String password = "TestPassword123!";

        // When
        AuthenticationResult result = user.authenticate(email, password, "127.0.0.1");

        // Then
        assertTrue(result.isSuccess());
        assertEquals(0, user.getFailedLoginAttempts());
        assertNotNull(user.getLastLoginAt());
        assertEquals("127.0.0.1", user.getLastLoginIp());
    }

    @Test
    void shouldFailAuthenticationWithWrongPassword() {
        // Given
        User user = createTestUserWithPassword();
        String email = "test@example.com";
        String wrongPassword = "WrongPassword";

        // When
        AuthenticationResult result = user.authenticate(email, wrongPassword, "127.0.0.1");

        // Then
        assertFalse(result.isSuccess());
        assertEquals(1, user.getFailedLoginAttempts());
        assertEquals("Invalid credentials", result.getReason());
    }

    @Test
    void shouldLockAccountAfterMaxFailedAttempts() {
        // Given
        User user = createTestUserWithPassword();
        String email = "test@example.com";
        String wrongPassword = "WrongPassword";

        // When - Simulate 5 failed attempts
        for (int i = 0; i < 5; i++) {
            user.authenticate(email, wrongPassword, "127.0.0.1");
        }

        // Then
        assertTrue(user.isAccountLocked());
        assertNotNull(user.getLockedUntil());
    }

    @Test
    void shouldEnableTwoFactorAuthentication() {
        // Given
        User user = createTestUserWithPassword();

        // When
        String secret = user.enableTwoFactor();

        // Then
        assertNotNull(secret);
        assertTrue(user.isTwoFactorEnabled());
        assertEquals(secret, user.getTwoFactorSecret());
    }

    @Test
    void shouldDisableTwoFactorAuthentication() {
        // Given
        User user = createTestUserWithPassword();
        user.enableTwoFactor();

        // When
        user.disableTwoFactor();

        // Then
        assertFalse(user.isTwoFactorEnabled());
        assertNull(user.getTwoFactorSecret());
    }

    private User createTestUser() {
        return User.create(
            UUID.randomUUID(),
            "testuser",
            "John",
            "Doe",
            UserRole.USER,
            "system"
        );
    }

    private User createTestUserWithPassword() {
        User user = createTestUser();
        String email = "test@example.com";
        user.addContact(ContactType.EMAIL, email, "system");
        
        var verificationToken = user.initiateContactVerification(email);
        user.verifyContact(email, verificationToken.getVerificationValue());
        user.createInitialPassword("TestPassword123!");
        
        return user;
    }
}
