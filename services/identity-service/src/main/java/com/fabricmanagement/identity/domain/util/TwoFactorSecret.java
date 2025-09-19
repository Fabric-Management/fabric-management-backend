package com.fabricmanagement.identity.domain.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Two-factor authentication secret generator.
 */
public class TwoFactorSecret {

    private static final SecureRandom random = new SecureRandom();
    private static final int SECRET_LENGTH = 32;

    public static String generate() {
        byte[] bytes = new byte[SECRET_LENGTH];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String generateTOTP(String secret, long timeCounter) {
        // Simplified TOTP generation - in production use proper TOTP library
        int code = Math.abs((secret + timeCounter).hashCode() % 1000000);
        return String.format("%06d", code);
    }

    public static boolean verifyTOTP(String secret, String code, long timeWindow) {
        long currentTime = System.currentTimeMillis() / 30000;
        for (long i = -timeWindow; i <= timeWindow; i++) {
            String expected = generateTOTP(secret, currentTime + i);
            if (expected.equals(code)) {
                return true;
            }
        }
        return false;
    }
}