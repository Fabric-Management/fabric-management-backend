package com.fabricmanagement.notification.domain.valueobject;

/**
 * Notification Channel Types
 * 
 * Supported communication channels for notifications.
 * Priority: WhatsApp > Email > SMS (cost optimization)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public enum NotificationChannel {
    
    /**
     * Email notification via SMTP
     * Priority: 1 (fallback from WhatsApp)
     */
    EMAIL,
    
    /**
     * SMS notification (future implementation)
     * Priority: 2 (most expensive, last resort)
     */
    SMS,
    
    /**
     * WhatsApp notification
     * Priority: 0 (preferred - lowest cost)
     */
    WHATSAPP
}

