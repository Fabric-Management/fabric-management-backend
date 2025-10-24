package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Notification Constants
 * 
 * Centralized notification-related constants including templates,
 * subjects, and default messages for email and SMS notifications.
 */
public final class NotificationConstants {
    
    private NotificationConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Email Templates
    public static final String TEMPLATE_VERIFICATION_CODE = "verification-code";
    public static final String TEMPLATE_PASSWORD_RESET = "password-reset";
    public static final String TEMPLATE_CONTACT_VERIFIED = "contact-verified";
    public static final String TEMPLATE_USER_INVITATION = "user-invitation";
    public static final String TEMPLATE_WELCOME = "welcome";
    
    // Email Subjects
    public static final String SUBJECT_VERIFICATION_CODE = "Verification Code - Fabric Management";
    public static final String SUBJECT_PASSWORD_RESET = "Password Reset - Fabric Management";
    public static final String SUBJECT_CONTACT_VERIFIED = "Contact Verified - Fabric Management";
    public static final String SUBJECT_USER_INVITATION = "You've Been Invited - Fabric Management";
    public static final String SUBJECT_WELCOME = "Welcome to Fabric Management";
    
    // SMS Templates
    public static final String SMS_VERIFICATION_TEMPLATE = "Your verification code is: %s. This code will expire in %d minutes.";
    public static final String SMS_PASSWORD_RESET_TEMPLATE = "Your password reset code is: %s. This code will expire in %d minutes.";
    
    // Notification Settings
    public static final int DEFAULT_CODE_EXPIRY_MINUTES = 15;
    public static final String DEFAULT_SENDER_NAME = "Fabric Management System";
    
    // Kafka Topics - DEPRECATED!
    // ❌ DO NOT USE! Use ${kafka.topics.xxx} in @Value instead
    // These constants are kept temporarily for backward compatibility
    // Will be removed in future versions (after full migration)
    @Deprecated(since = "3.3.0", forRemoval = true)
    public static final String TOPIC_EMAIL_NOTIFICATIONS = "email-notifications";
    @Deprecated(since = "3.3.0", forRemoval = true)
    public static final String TOPIC_SMS_NOTIFICATIONS = "sms-notifications";
}

