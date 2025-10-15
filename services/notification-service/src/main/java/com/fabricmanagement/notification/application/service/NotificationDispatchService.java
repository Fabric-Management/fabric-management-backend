package com.fabricmanagement.notification.application.service;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.entity.NotificationLog;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationStatus;
import com.fabricmanagement.notification.infrastructure.config.PlatformFallbackConfig;
import com.fabricmanagement.notification.infrastructure.notification.EmailNotificationSender;
import com.fabricmanagement.notification.infrastructure.notification.NotificationException;
import com.fabricmanagement.notification.infrastructure.notification.SmsNotificationSender;
import com.fabricmanagement.notification.infrastructure.notification.WhatsAppNotificationSender;
import com.fabricmanagement.notification.infrastructure.repository.NotificationConfigRepository;
import com.fabricmanagement.notification.infrastructure.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Notification Dispatch Service
 * 
 * Core business logic for notification delivery with fallback pattern.
 * 
 * Fallback Strategy:
 * 1. Try preferred channel (WhatsApp if available)
 * 2. If fails → Try Email
 * 3. If fails → Try SMS (last resort)
 * 4. Log all attempts
 * 
 * Configuration Priority:
 * 1. Tenant-specific config (from database)
 * 2. Platform fallback config (environment variables)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatchService {
    
    private final NotificationConfigRepository configRepository;
    private final NotificationLogRepository logRepository;
    private final PlatformFallbackConfig platformConfig;
    
    private final EmailNotificationSender emailSender;
    private final WhatsAppNotificationSender whatsAppSender;
    private final SmsNotificationSender smsSender;
    
    /**
     * Dispatch notification with fallback pattern
     * 
     * Idempotency: Uses eventId to prevent duplicate sends
     */
    @Transactional
    public void dispatch(NotificationSendRequestEvent event) {
        // Idempotency check
        if (logRepository.existsByEventId(event.getEventId())) {
            log.warn("⏭️ Duplicate event ignored: {}", event.getEventId());
            return;
        }
        
        log.info("📤 Dispatching notification: {} → {} (tenant: {})", 
            event.getEventType(), event.getRecipient(), event.getTenantId());
        
        // Create log entry
        NotificationLog notificationLog = NotificationLog.builder()
            .eventId(event.getEventId())
            .tenantId(event.getTenantId())
            .channel(event.getPreferredChannel() != null ? event.getPreferredChannel() : NotificationChannel.EMAIL)
            .template(event.getTemplate())
            .recipient(event.getRecipient())
            .subject(event.getSubject())
            .body(event.getBody())
            .status(NotificationStatus.PENDING)
            .attempts(0)
            .triggeredBy(event.getUserId())
            .build();
        
        notificationLog = logRepository.save(notificationLog);
        
        // Determine channel order (fallback chain)
        List<NotificationChannel> fallbackChain = determineFallbackChain(event);
        
        // Try each channel until success
        for (NotificationChannel channel : fallbackChain) {
            try {
                sendViaChannel(event, channel, notificationLog);
                log.info("✅ Notification sent via {}: {}", channel, event.getRecipient());
                return; // Success - stop trying
                
            } catch (NotificationException e) {
                log.warn("⚠️ Channel {} failed: {} - {}", channel, event.getRecipient(), e.getMessage());
                
                // Update log with error
                notificationLog.setAttempts(notificationLog.getAttempts() + 1);
                notificationLog.setErrorMessage(e.getMessage());
                notificationLog.setStatus(e.isRetryable() ? NotificationStatus.RETRYING : NotificationStatus.FAILED);
                logRepository.save(notificationLog);
                
                // Continue to next channel (fallback)
            }
        }
        
        // All channels failed
        notificationLog.setStatus(NotificationStatus.FAILED);
        notificationLog.setErrorMessage("All channels failed");
        logRepository.save(notificationLog);
        
        log.error("❌ All channels failed for: {} (event: {})", event.getRecipient(), event.getEventId());
    }
    
    /**
     * Send via specific channel
     */
    private void sendViaChannel(NotificationSendRequestEvent event, NotificationChannel channel, NotificationLog log) 
            throws NotificationException {
        
        log.setChannel(channel);
        log.setStatus(NotificationStatus.PROCESSING);
        log.setAttempts(log.getAttempts() + 1);
        logRepository.save(log);
        
        // Get tenant config for this channel
        NotificationConfig config = configRepository
            .findActiveConfigByTenantAndChannel(event.getTenantId(), channel)
            .orElse(null); // null = use platform fallback
        
        // Send via appropriate sender
        switch (channel) {
            case EMAIL -> emailSender.send(event, config);
            case WHATSAPP -> whatsAppSender.send(event, config);
            case SMS -> smsSender.send(event, config);
        }
        
        // Success
        log.setStatus(NotificationStatus.SENT);
        log.setSentAt(LocalDateTime.now());
        logRepository.save(log);
    }
    
    /**
     * Determine fallback chain based on availability
     * Priority: WhatsApp > Email > SMS
     */
    private List<NotificationChannel> determineFallbackChain(NotificationSendRequestEvent event) {
        // If preferred channel specified, try it first
        if (event.getPreferredChannel() != null) {
            return List.of(
                event.getPreferredChannel(),
                NotificationChannel.EMAIL,  // Always fallback to email
                NotificationChannel.WHATSAPP,
                NotificationChannel.SMS
            ).stream().distinct().toList();
        }
        
        // Default priority: WhatsApp (cheapest) > Email > SMS (most expensive)
        // Check if WhatsApp/SMS are available for tenant or platform
        boolean hasWhatsApp = configRepository
            .hasConfigForChannel(event.getTenantId(), NotificationChannel.WHATSAPP) ||
            platformConfig.isWhatsAppConfigured();
        
        boolean hasSms = configRepository
            .hasConfigForChannel(event.getTenantId(), NotificationChannel.SMS) ||
            platformConfig.isSmsConfigured();
        
        // Build fallback chain
        if (hasWhatsApp) {
            return List.of(NotificationChannel.WHATSAPP, NotificationChannel.EMAIL, NotificationChannel.SMS);
        } else if (hasSms) {
            return List.of(NotificationChannel.EMAIL, NotificationChannel.SMS);
        } else {
            return List.of(NotificationChannel.EMAIL); // Email always available (platform fallback)
        }
    }
}

