package com.fabricmanagement.contact.infrastructure.messaging;

import com.fabricmanagement.contact.domain.valueobject.ContactType;
import com.fabricmanagement.shared.infrastructure.constants.NotificationConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    
    @Value("${kafka.topics.email-notifications:" + NotificationConstants.TOPIC_EMAIL_NOTIFICATIONS + "}")
    private String emailNotificationTopic;
    
    @Value("${kafka.topics.sms-notifications:" + NotificationConstants.TOPIC_SMS_NOTIFICATIONS + "}")
    private String smsNotificationTopic;
    
    @Value("${notification.enabled:true}")
    private boolean notificationEnabled;
    
    @Value("${notification.code-expiry-minutes:" + NotificationConstants.DEFAULT_CODE_EXPIRY_MINUTES + "}")
    private int codeExpiryMinutes;
    
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
        }
    }
    
    private void sendEmailVerification(String email, String code) {
        Map<String, Object> emailPayload = new HashMap<>();
        emailPayload.put("to", email);
        emailPayload.put("subject", NotificationConstants.SUBJECT_VERIFICATION_CODE);
        emailPayload.put("template", NotificationConstants.TEMPLATE_VERIFICATION_CODE);
        emailPayload.put("code", code);
        emailPayload.put("expiresInMinutes", codeExpiryMinutes);
        emailPayload.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send(emailNotificationTopic, email, emailPayload);
        log.info("📧 Email verification queued for {}", email);
    }
    
    private void sendSmsVerification(String phone, String code) {
        Map<String, Object> smsPayload = new HashMap<>();
        smsPayload.put("to", phone);
        smsPayload.put("message", String.format(NotificationConstants.SMS_VERIFICATION_TEMPLATE, code, codeExpiryMinutes));
        smsPayload.put("code", code);
        smsPayload.put("expiresInMinutes", codeExpiryMinutes);
        smsPayload.put("timestamp", LocalDateTime.now().toString());
        
        kafkaTemplate.send(smsNotificationTopic, phone, smsPayload);
        log.info("📱 SMS verification queued for {}", phone);
    }
    
    public void sendContactVerifiedNotification(String contactValue, ContactType type) {
        if (!notificationEnabled) {
            return;
        }
        
        log.info("Sending contact verified notification to {} ({})", contactValue, type);
        
        try {
            if (type == ContactType.EMAIL) {
                Map<String, Object> emailPayload = new HashMap<>();
                emailPayload.put("to", contactValue);
                emailPayload.put("subject", NotificationConstants.SUBJECT_CONTACT_VERIFIED);
                emailPayload.put("template", NotificationConstants.TEMPLATE_CONTACT_VERIFIED);
                emailPayload.put("timestamp", LocalDateTime.now().toString());
                
                kafkaTemplate.send(emailNotificationTopic, contactValue, emailPayload);
                log.info("📧 Contact verified notification queued for {}", contactValue);
            }
        } catch (Exception e) {
            log.error("Failed to send verified notification to {}: {}", contactValue, e.getMessage(), e);
        }
    }
}

