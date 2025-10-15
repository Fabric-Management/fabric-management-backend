package com.fabricmanagement.notification.infrastructure.notification;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;

/**
 * Notification Sender Interface
 * 
 * Strategy pattern for different notification channels.
 * Each implementation handles one channel (Email, SMS, WhatsApp).
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
public interface NotificationSender {
    
    /**
     * Send notification using tenant config or platform fallback
     * 
     * @param event Notification request event
     * @param config Tenant config (nullable - use platform fallback if null)
     * @throws NotificationException if delivery fails
     */
    void send(NotificationSendRequestEvent event, NotificationConfig config) throws NotificationException;
    
    /**
     * Check if this sender supports the given channel
     */
    boolean supports(String channel);
}

