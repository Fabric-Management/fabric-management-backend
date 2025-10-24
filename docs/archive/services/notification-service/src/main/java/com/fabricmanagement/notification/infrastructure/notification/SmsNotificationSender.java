package com.fabricmanagement.notification.infrastructure.notification;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.infrastructure.config.PlatformFallbackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * SMS Notification Sender
 * 
 * Sends SMS messages via provider API (Twilio, Vonage, etc.).
 * 
 * Features:
 * - International delivery
 * - Delivery tracking
 * - Cost optimization (use as last resort)
 * 
 * Implementation Status: PLACEHOLDER
 * TODO: Integrate with SMS provider when tenant provides credentials
 * 
 * Priority: Lowest (WhatsApp > Email > SMS)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsNotificationSender implements NotificationSender {
    
    private final PlatformFallbackConfig platformConfig;
    
    @Override
    public void send(NotificationSendRequestEvent event, NotificationConfig config) throws NotificationException {
        log.warn("⚠️ SMS not implemented yet");
        
        // Check if SMS is configured
        boolean tenantConfigured = config != null && 
            config.getApiKey() != null && 
            !config.getApiKey().isEmpty();
        
        boolean platformConfigured = platformConfig.isSmsConfigured();
        
        if (!tenantConfigured && !platformConfigured) {
            throw new NotificationException(
                "SMS not configured for tenant or platform",
                "SMS",
                event.getRecipient(),
                false // not retryable - needs config
            );
        }
        
        // TODO: Implement SMS provider integration (Twilio/Vonage)
        // 1. Validate phone number format (E.164)
        // 2. Call SMS API
        // 3. Handle rate limiting
        // 4. Track delivery status
        
        log.info("📟 SMS (placeholder): {} → {} (tenant: {})", 
            config != null ? config.getFromNumber() : platformConfig.getPlatformSmsFromNumber(),
            event.getRecipient(),
            event.getTenantId());
    }
    
    @Override
    public boolean supports(String channel) {
        return "SMS".equalsIgnoreCase(channel);
    }
}

