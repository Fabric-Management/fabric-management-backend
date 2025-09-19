package com.fabricmanagement.identity.domain.valueobject;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Value object representing a verification token for contact verification.
 */
public final class VerificationToken {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int CODE_LENGTH = 6;
    private static final int TOKEN_LENGTH = 32;
    private static final int EXPIRY_MINUTES = 30;

    private final String token;
    private final String code;
    private final LocalDateTime expiresAt;
    private final ContactType contactType;

    private VerificationToken(String token, String code, LocalDateTime expiresAt, ContactType contactType) {
        this.token = token;
        this.code = code;
        this.expiresAt = expiresAt;
        this.contactType = contactType;
    }

    /**
     * Generates a new verification token.
     */
    public static VerificationToken generate(ContactType contactType) {
        String token = generateToken();
        String code = generateCode();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(EXPIRY_MINUTES);
        return new VerificationToken(token, code, expiresAt, contactType);
    }

    /**
     * Creates a token from existing values (for loading from DB).
     */
    public static VerificationToken of(String token, String code, LocalDateTime expiresAt, ContactType contactType) {
        return new VerificationToken(token, code, expiresAt, contactType);
    }

    /**
     * Validates the provided token value.
     */
    public boolean isValid(String value) {
        if (isExpired()) {
            return false;
        }

        // For email, use token; for phone, use code
        if (contactType == ContactType.EMAIL) {
            return token.equals(value);
        } else {
            return code.equals(value);
        }
    }

    /**
     * Checks if the token has expired.
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * Gets the appropriate value based on contact type.
     * For email: returns token (for link)
     * For phone: returns code (for SMS)
     */
    public String getVerificationValue() {
        return contactType == ContactType.EMAIL ? token : code;
    }

    public String getToken() {
        return token;
    }

    public String getCode() {
        return code;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public ContactType getContactType() {
        return contactType;
    }

    private static String generateToken() {
        byte[] bytes = new byte[TOKEN_LENGTH];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VerificationToken that = (VerificationToken) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }
}