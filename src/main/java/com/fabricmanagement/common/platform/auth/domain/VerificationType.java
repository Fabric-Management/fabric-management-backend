package com.fabricmanagement.common.platform.auth.domain;

/**
 * Verification code type classification.
 */
public enum VerificationType {

    /**
     * Registration verification
     * <p>Sent during user registration flow</p>
     */
    REGISTRATION,

    /**
     * Password reset verification
     * <p>Sent when user requests password reset</p>
     */
    PASSWORD_RESET,

    /**
     * Email verification
     * <p>Sent to verify email ownership</p>
     */
    EMAIL_VERIFICATION,

    /**
     * Phone verification
     * <p>Sent to verify phone ownership</p>
     */
    PHONE_VERIFICATION
}

