package com.fabricmanagement.identity.application.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for sending notifications (email/SMS).
 * Stub implementation - integrate with actual providers in production.
 */
@Slf4j
@Service
public class NotificationService {

    public void sendEmail(String to, String subject, String body) {
        log.info("Sending email to: {}, subject: {}", to, subject);
        // TODO: Integrate with email provider (SendGrid, SES, etc.)
    }

    public void sendSms(String to, String message) {
        log.info("Sending SMS to: {}, message: {}", to, message);
        // TODO: Integrate with SMS provider (Twilio, SNS, etc.)
    }

    public void sendVerificationCode(String contact, String code, boolean isEmail) {
        if (isEmail) {
            sendEmail(contact, "Verification Code", 
                "Your verification code is: " + code + "\nThis code will expire in 15 minutes.");
        } else {
            sendSms(contact, "Your verification code is: " + code);
        }
    }

    public void sendPasswordResetToken(String contact, String token, boolean isEmail) {
        if (isEmail) {
            sendEmail(contact, "Password Reset", 
                "Use this token to reset your password: " + token + "\nThis token will expire in 30 minutes.");
        } else {
            sendSms(contact, "Password reset token: " + token);
        }
    }
}