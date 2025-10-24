package com.fabricmanagement.notification.domain.valueobject;

/**
 * Notification Provider Types
 * 
 * Third-party or internal providers for sending notifications.
 * Each provider requires specific configuration.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public enum NotificationProvider {
    
    /**
     * Standard SMTP email provider
     * Config: host, port, username, password, from
     */
    SMTP,
    
    /**
     * Gmail SMTP (specific configuration)
     * Config: app password required, port 587
     */
    GMAIL,
    
    /**
     * Twilio SMS/WhatsApp provider (future)
     * Config: account SID, auth token, from number
     */
    TWILIO,
    
    /**
     * Custom WhatsApp Business API (future)
     * Config: API key, webhook, from number
     */
    WHATSAPP_BUSINESS,
    
    /**
     * Platform default (fallback)
     * Uses platform-defined credentials
     */
    PLATFORM_DEFAULT
}

