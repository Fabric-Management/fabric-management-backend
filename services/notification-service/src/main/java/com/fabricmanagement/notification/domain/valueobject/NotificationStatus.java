package com.fabricmanagement.notification.domain.valueobject;

/**
 * Notification Delivery Status
 * 
 * Tracks the lifecycle of a notification from creation to delivery.
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public enum NotificationStatus {
    
    /**
     * Notification queued, waiting to be sent
     */
    PENDING,
    
    /**
     * Notification currently being processed
     */
    PROCESSING,
    
    /**
     * Notification sent successfully
     */
    SENT,
    
    /**
     * Notification delivery failed
     */
    FAILED,
    
    /**
     * Notification retrying after failure
     */
    RETRYING,
    
    /**
     * Notification cancelled (e.g. user verified before sending)
     */
    CANCELLED
}

