package com.fabricmanagement.contact.application.service;

import com.fabricmanagement.contact.domain.valueobject.ContactType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Notification Service
 * 
 * Handles sending notifications (email, SMS, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    // TODO: Add actual email/SMS service integrations
    // For now, just log the notifications
    
    /**
     * Sends verification code to a contact
     */
    public void sendVerificationCode(String contactValue, String code, ContactType type) {
        log.info("Sending verification code to {} ({}): {}", contactValue, type, code);
        
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
    }
    
    /**
     * Sends email verification
     */
    private void sendEmailVerification(String email, String code) {
        // TODO: Integrate with email service (SendGrid, AWS SES, etc.)
        log.info("📧 Email verification sent to {}: Your verification code is: {}", email, code);
    }
    
    /**
     * Sends SMS verification
     */
    private void sendSmsVerification(String phone, String code) {
        // TODO: Integrate with SMS service (Twilio, AWS SNS, etc.)
        log.info("📱 SMS verification sent to {}: Your verification code is: {}", phone, code);
    }
}
