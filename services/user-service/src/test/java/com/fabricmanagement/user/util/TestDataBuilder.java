package com.fabricmanagement.user.util;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.PasswordResetToken;
import com.fabricmanagement.user.domain.valueobject.RegistrationType;
import com.fabricmanagement.user.domain.valueobject.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Test Data Builder for User Service Tests
 * 
 * Provides convenient methods to create test data with sensible defaults.
 * Follows the Builder pattern for flexible test data creation.
 */
public class TestDataBuilder {

    /**
     * Creates a default active user for testing
     * Note: ID is not set - let JPA generate it
     */
    public static User defaultUser() {
        return User.builder()
                .tenantId(UUID.randomUUID().toString())
                .firstName("John")
                .lastName("Doe")
                .displayName("John Doe")
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .passwordHash("$2a$10$hashedpassword123")
                .role("USER")
                .build();
    }

    /**
     * Creates a pending verification user
     */
    public static User pendingUser() {
        return User.builder()
                .tenantId(UUID.randomUUID().toString())
                .firstName("Jane")
                .lastName("Smith")
                .displayName("Jane Smith")
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .build();
    }

    /**
     * Creates a self-registered user (external partner)
     */
    public static User selfRegisteredUser() {
        return User.builder()
                .tenantId(UUID.randomUUID().toString())
                .firstName("Bob")
                .lastName("Johnson")
                .displayName("Bob Johnson")
                .status(UserStatus.PENDING_VERIFICATION)
                .registrationType(RegistrationType.SELF_REGISTRATION)
                .build();
    }

    /**
     * Creates a suspended user
     */
    public static User suspendedUser() {
        return User.builder()
                .tenantId(UUID.randomUUID().toString())
                .firstName("Suspended")
                .lastName("User")
                .displayName("Suspended User")
                .status(UserStatus.SUSPENDED)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .passwordHash("$2a$10$hashedpassword123")
                .build();
    }

    /**
     * Creates a user with specific tenant
     */
    public static User userWithTenant(String tenantId) {
        return User.builder()
                .tenantId(tenantId)
                .firstName("Test")
                .lastName("User")
                .displayName("Test User")
                .status(UserStatus.ACTIVE)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .passwordHash("$2a$10$hashedpassword123")
                .role("USER")
                .build();
    }

    /**
     * Creates a user with specific status
     */
    public static User userWithStatus(UserStatus status) {
        return User.builder()
                .tenantId(UUID.randomUUID().toString())
                .firstName("Status")
                .lastName("Test")
                .displayName("Status Test")
                .status(status)
                .registrationType(RegistrationType.DIRECT_REGISTRATION)
                .passwordHash("$2a$10$hashedpassword123")
                .build();
    }

    /**
     * Creates a password reset token for testing
     */
    public static PasswordResetToken defaultPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .contactValue("test@example.com")
                .resetMethod(PasswordResetToken.ResetMethod.EMAIL_CODE)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(false)
                .attemptsRemaining(3)
                .build();
    }

    /**
     * Creates an expired password reset token
     */
    public static PasswordResetToken expiredPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .contactValue("test@example.com")
                .resetMethod(PasswordResetToken.ResetMethod.EMAIL_CODE)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .isUsed(false)
                .attemptsRemaining(3)
                .build();
    }

    /**
     * Creates a used password reset token
     */
    public static PasswordResetToken usedPasswordResetToken(User user) {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .contactValue("test@example.com")
                .resetMethod(PasswordResetToken.ResetMethod.EMAIL_CODE)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(true)
                .attemptsRemaining(0)
                .usedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Builder for customized User creation
     */
    public static class UserBuilder {
        private String tenantId = UUID.randomUUID().toString();
        private String firstName = "Test";
        private String lastName = "User";
        private String displayName = "Test User";
        private UserStatus status = UserStatus.ACTIVE;
        private RegistrationType registrationType = RegistrationType.DIRECT_REGISTRATION;
        private String passwordHash = "$2a$10$hashedpassword123";
        private String role = "USER";

        public UserBuilder withTenantId(String tenantId) {
            this.tenantId = tenantId;
            return this;
        }

        public UserBuilder withName(String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
            this.displayName = firstName + " " + lastName;
            return this;
        }

        public UserBuilder withStatus(UserStatus status) {
            this.status = status;
            return this;
        }

        public UserBuilder withRegistrationType(RegistrationType registrationType) {
            this.registrationType = registrationType;
            return this;
        }

        public UserBuilder withRole(String role) {
            this.role = role;
            return this;
        }

        public User build() {
            return User.builder()
                    .tenantId(tenantId)
                    .firstName(firstName)
                    .lastName(lastName)
                    .displayName(displayName)
                    .status(status)
                    .registrationType(registrationType)
                    .passwordHash(passwordHash)
                    .role(role)
                    .build();
        }
    }

    /**
     * Creates a custom user builder
     */
    public static UserBuilder customUser() {
        return new UserBuilder();
    }
}

