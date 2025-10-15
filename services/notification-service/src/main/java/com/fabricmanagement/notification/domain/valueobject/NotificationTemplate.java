package com.fabricmanagement.notification.domain.valueobject;

/**
 * Notification Template Types
 * 
 * Predefined templates for common notification scenarios.
 * Each template has a default subject/body with placeholders.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public enum NotificationTemplate {
    
    /**
     * Email/SMS verification code
     * Placeholders: {code}, {expiryMinutes}, {companyName}
     */
    VERIFICATION_CODE,
    
    /**
     * Welcome message after registration
     * Placeholders: {firstName}, {companyName}
     */
    WELCOME,
    
    /**
     * Password reset code
     * Placeholders: {code}, {expiryMinutes}
     */
    PASSWORD_RESET,
    
    /**
     * Password successfully created
     * Placeholders: {firstName}
     */
    PASSWORD_CREATED,
    
    /**
     * Login alert (suspicious activity)
     * Placeholders: {ipAddress}, {location}, {timestamp}
     */
    LOGIN_ALERT,
    
    /**
     * Generic notification
     * Placeholders: custom
     */
    GENERIC
}

