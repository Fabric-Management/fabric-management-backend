package com.fabricmanagement.notification.infrastructure.messaging;

import com.fabricmanagement.notification.application.service.NotificationDispatchService;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.domain.valueobject.NotificationChannel;
import com.fabricmanagement.notification.domain.valueobject.NotificationTemplate;
import com.fabricmanagement.shared.domain.event.UserCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * User Event Listener
 * 
 * Listens to user-related Kafka events and triggers notifications.
 * 
 * Events:
 * - UserCreatedEvent → Send verification email
 * - UserPasswordResetEvent → Send reset code (future)
 * - UserPasswordChangedEvent → Send confirmation (future)
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {
    
    private final NotificationDispatchService dispatchService;
    
    @Value("${platform.branding.name:FabriCode}")
    private String platformName;
    
    @Value("${platform.branding.company:Akkayalar Group}")
    private String platformCompany;
    
    /**
     * Handle user created event - send verification email
     */
    @KafkaListener(
        topics = "${kafka.topics.user-created:user.created}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onUserCreated(UserCreatedEvent event) {
        log.info("📨 Received UserCreatedEvent: {} (tenant: {}, preferredChannel: {})", 
            event.getUserId(), event.getTenantId(), event.getPreferredChannel());
        
        try {
            // Determine channel (mobile → WhatsApp, web → Email)
            NotificationChannel channel = determineChannel(event);
            
            // Determine recipient (WhatsApp/SMS → phone, Email → email)
            String recipient = (channel == NotificationChannel.EMAIL) 
                ? event.getEmail() 
                : event.getPhone();
            
            // Build notification request
            NotificationSendRequestEvent notificationEvent = NotificationSendRequestEvent.builder()
                .eventId(event.getEventId())
                .eventType("USER_CREATED")
                .tenantId(event.getTenantId())
                .userId(event.getUserId())
                .preferredChannel(channel) // ✅ Dynamic channel selection
                .template(NotificationTemplate.VERIFICATION_CODE)
                .recipient(recipient)
                .subject("Verify your account - " + event.getCompanyName())
                .body(buildVerificationBody(event, channel))
                .variables(Map.of(
                    "firstName", event.getFirstName() != null ? event.getFirstName() : "",
                    "code", event.getVerificationCode() != null ? event.getVerificationCode() : "",
                    "expiryMinutes", "15",
                    "companyName", event.getCompanyName() != null ? event.getCompanyName() : "Store and Sale"
                ))
                .build();
            
            // Dispatch notification
            dispatchService.dispatch(notificationEvent);
            
        } catch (Exception e) {
            log.error("❌ Failed to process UserCreatedEvent: {} - {}", event.getUserId(), e.getMessage(), e);
        }
    }
    
    /**
     * Determine notification channel from event
     * Mobile → WhatsApp (default), Web → Email (default)
     */
    private NotificationChannel determineChannel(UserCreatedEvent event) {
        if (event.getPreferredChannel() == null || event.getPreferredChannel().isEmpty()) {
            // Default: WhatsApp for mobile, Email for web
            return NotificationChannel.WHATSAPP;
        }
        
        try {
            return NotificationChannel.valueOf(event.getPreferredChannel().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("⚠️ Invalid channel: {}, defaulting to WHATSAPP", event.getPreferredChannel());
            return NotificationChannel.WHATSAPP;
        }
    }
    
    /**
     * Build verification body (email or SMS/WhatsApp)
     */
    private String buildVerificationBody(UserCreatedEvent event, NotificationChannel channel) {
        if (channel == NotificationChannel.EMAIL) {
            return buildVerificationEmailBody(event);
        } else {
            // SMS/WhatsApp: Short message
            return buildVerificationSmsBody(event);
        }
    }
    
    /**
     * Build verification SMS/WhatsApp body (short format)
     */
    private String buildVerificationSmsBody(UserCreatedEvent event) {
        String code = event.getVerificationCode() != null ? event.getVerificationCode() : "N/A";
        
        return String.format(
            "%s verification code: %s\n\nExpires in 15 min.\n\nPowered by %s",
            platformName,
            code,
            platformCompany
        );
    }
    
    /**
     * Build verification email body
     */
    private String buildVerificationEmailBody(UserCreatedEvent event) {
        String firstName = event.getFirstName() != null ? event.getFirstName() : "there";
        String code = event.getVerificationCode() != null ? event.getVerificationCode() : "N/A";
        
        return """
            <h2>Welcome to %s!</h2>
            <p>Hi %s,</p>
            <p>Thank you for registering. Please verify your email address using the code below:</p>
            <p class="code" style="font-size: 24px; font-weight: bold; color: #2563eb; padding: 20px; background: #f3f4f6; border-radius: 8px; text-align: center; letter-spacing: 4px;">%s</p>
            <p>This code will expire in 15 minutes.</p>
            <p style="color: #6b7280; font-size: 14px;">If you didn't create this account, please ignore this email.</p>
            <br>
            <p>Best regards,<br><strong>%s Team</strong></p>
            <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 20px 0;">
            <p style="color: #9ca3af; font-size: 12px; text-align: center;">Powered by %s</p>
            """.formatted(
                platformName,
                firstName,
                code,
                platformName,
                platformCompany
            );
    }
}

