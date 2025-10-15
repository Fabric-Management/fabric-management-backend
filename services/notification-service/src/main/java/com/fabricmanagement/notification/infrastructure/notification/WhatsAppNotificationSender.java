package com.fabricmanagement.notification.infrastructure.notification;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.infrastructure.config.PlatformFallbackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * WhatsApp Notification Sender
 * 
 * Sends WhatsApp messages via Business API.
 * 
 * Features:
 * - Template messages
 * - Rich media support (future)
 * - Delivery tracking
 * - Fallback to Email if not configured
 * 
 * Implementation Status: PLACEHOLDER
 * TODO: Integrate with WhatsApp Business API when tenant provides credentials
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationSender implements NotificationSender {
    
    private final PlatformFallbackConfig platformConfig;
    
    @Override
    public void send(NotificationSendRequestEvent event, NotificationConfig config) throws NotificationException {
        log.warn("⚠️ WhatsApp not implemented yet, falling back to Email");
        
        // Check if WhatsApp is configured
        boolean tenantConfigured = config != null && 
            config.getApiKey() != null && 
            !config.getApiKey().isEmpty();
        
        boolean platformConfigured = platformConfig.isWhatsAppConfigured();
        
        if (!tenantConfigured && !platformConfigured) {
            throw new NotificationException(
                "WhatsApp not configured for tenant or platform",
                "WHATSAPP",
                event.getRecipient(),
                false // not retryable - needs config
            );
        }
        
        // TODO: Implement WhatsApp Business API integration
        // 1. Format phone number to E.164
        // 2. Call WhatsApp API with template or text message
        // 3. Handle rate limiting and delivery status
        
        log.info("📱 WhatsApp (placeholder): {} → {} (tenant: {})", 
            config != null ? config.getFromNumber() : platformConfig.getPlatformWhatsappFromNumber(),
            event.getRecipient(),
            event.getTenantId());
    }
    
    @Override
    public boolean supports(String channel) {
        return "WHATSAPP".equalsIgnoreCase(channel);
    }
}

