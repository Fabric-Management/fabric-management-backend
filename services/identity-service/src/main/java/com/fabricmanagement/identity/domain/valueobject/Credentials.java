package com.fabricmanagement.identity.domain.valueobject;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing user credentials.
 */
public final class Credentials {

    private static final PasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final String passwordHash;
    private final LocalDateTime createdAt;
    private final LocalDateTime lastChangedAt;

    private Credentials(String passwordHash, LocalDateTime createdAt, LocalDateTime lastChangedAt) {
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
        this.lastChangedAt = lastChangedAt;
    }

    /**
     * Creates new credentials with the given password.
     */
    public static Credentials create(String plainPassword) {
        validatePassword(plainPassword);
        String hash = PASSWORD_ENCODER.encode(plainPassword);
        LocalDateTime now = LocalDateTime.now();
        return new Credentials(hash, now, now);
    }

    /**
     * Creates credentials from existing hash (for loading from DB).
     */
    public static Credentials fromHash(String passwordHash, LocalDateTime createdAt, LocalDateTime lastChangedAt) {
        return new Credentials(passwordHash, createdAt, lastChangedAt);
    }

    /**
     * Changes the password.
     */
    public Credentials change(String newPlainPassword) {
        validatePassword(newPlainPassword);
        String newHash = PASSWORD_ENCODER.encode(newPlainPassword);
        return new Credentials(newHash, this.createdAt, LocalDateTime.now());
    }

    /**
     * Checks if the given password matches.
     */
    public boolean matches(String plainPassword) {
        if (plainPassword == null || passwordHash == null) {
            return false;
        }
        return PASSWORD_ENCODER.matches(plainPassword, passwordHash);
    }

    /**
     * Checks if credentials have a password set.
     */
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isEmpty();
    }

    /**
     * Gets the password hash (for persistence).
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastChangedAt() {
        return lastChangedAt;
    }

    /**
     * Checks if password is older than the specified days.
     */
    public boolean isOlderThan(int days) {
        return lastChangedAt.isBefore(LocalDateTime.now().minusDays(days));
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters long");
        }

        // Additional validation rules
        boolean hasUpperCase = false;
        boolean hasLowerCase = false;
        boolean hasDigit = false;
        boolean hasSpecialChar = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) hasUpperCase = true;
            if (Character.isLowerCase(c)) hasLowerCase = true;
            if (Character.isDigit(c)) hasDigit = true;
            if (!Character.isLetterOrDigit(c)) hasSpecialChar = true;
        }

        if (!hasUpperCase || !hasLowerCase || !hasDigit) {
            throw new IllegalArgumentException(
                "Password must contain at least one uppercase letter, one lowercase letter, and one digit"
            );
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Credentials that = (Credentials) o;
        return Objects.equals(passwordHash, that.passwordHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(passwordHash);
    }
}