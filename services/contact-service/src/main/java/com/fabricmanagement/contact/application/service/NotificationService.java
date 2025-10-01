package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.valueobject.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Notification Service
 * 
 * Handles sending notifications (email, SMS, etc.) via Kafka messaging
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topics.email-notifications:email-notifications}")
    private String emailNotificationTopic;
    
    @Value("${kafka.topics.sms-notifications:sms-notifications}")
    private String smsNotificationTopic;
    
    @Value("${notification.enabled:true}")
    private boolean notificationEnabled;
    
    /**
     * Sends verification code to a contact
     */
    public void sendVerificationCode(String contactValue, String code, ContactType type) {
        if (!notificationEnabled) {
            log.warn("Notifications are disabled. Verification code for {} ({}): {}", contactValue, type, code);
            return;
        }
        
        log.info("Sending verification code to {} ({})", contactValue, type);
        
        try {
            switch (type) {
                case EMAIL:
                    sendEmailVerification(contactValue, code);
                    break;
                case PHONE:
                    sendSmsVerification(contactValue, code);
                    break;
                default:
                    log.warn("Unsupported contact type for verification: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to send verification code to {} ({}): {}", contactValue, type, e.getMessage(), e);
            // In production, you might want to implement retry mechanism or store failed notifications
        }
    }
    
    /**
     * Sends email verification via Kafka
     */
    private void sendEmailVerification(String email, String code) {
        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("to", email);
        emailPayload.put("subject", "Verification Code - Fabric Management");
        emailPayload.put("template", "verification-code");
        emailPayload.put("code", code);
        emailPayload.put("expiresInMinutes", 15);
        emailPayload.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send(emailNotificationTopic, email, emailPayload);
        log.info("📧 Email verification queued for {}", email);
    }
    
    /**
     * Sends SMS verification via Kafka
     */
    private void sendSmsVerification(String phone, String code) {
        Map<String, Object> smsPayload = new HashMap<>();
        smsPayload.put("to", phone);
        smsPayload.put("message", String.format("Your verification code is: %s. This code will expire in 15 minutes.", code));
        smsPayload.put("code", code);
        smsPayload.put("expiresInMinutes", 15);
        smsPayload.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send(smsNotificationTopic, phone, smsPayload);
        log.info("📱 SMS verification queued for {}", phone);
    }
    
    /**
     * Sends a notification when contact is verified
     */
    public void sendContactVerifiedNotification(String contactValue, ContactType type) {
        if (!notificationEnabled) {
            return;
        }
        
        log.info("Sending contact verified notification to {} ({})", contactValue, type);
        
        try {
            if (type == ContactType.EMAIL) {
                Map<String, Object> emailPayload = new HashMap<>();
                emailPayload.put("to", contactValue);
                emailPayload.put("subject", "Contact Verified - Fabric Management");
                emailPayload.put("template", "contact-verified");
                emailPayload.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send(emailNotificationTopic, contactValue, emailPayload);
                log.info("📧 Contact verified notification queued for {}", contactValue);
            }
        } catch (Exception e) {
            log.error("Failed to send verified notification to {}: {}", contactValue, e.getMessage(), e);
        }
    }
}
