package com.fabricmanagement.identity.application.service;

import com.fabricmanagement.identity.domain.valueobject.ContactType;
import com.fabricmanagement.identity.domain.valueobject.VerificationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Service for sending notifications (email/SMS).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;

    @Value("${app.notification.email.from:noreply@fabricmanagement.com}")
    private String fromEmail;

    @Value("${app.notification.sms.from:FABRIC}")
    private String smsFrom;

    /**
     * Sends verification email.
     */
    public void sendVerificationEmail(String email, VerificationToken token) {
        log.info("Sending verification email to: {}", email);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setFrom(fromEmail);
            message.setSubject("Verify Your Email - Fabric Management");
            message.setText(buildVerificationEmailContent(token.getVerificationValue()));

            mailSender.send(message);
            log.info("Verification email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send verification email to: {}", email, e);
            throw new RuntimeException("Failed to send verification email", e);
        }
    }

    /**
     * Sends verification SMS.
     */
    public void sendVerificationSms(String phone, VerificationToken token) {
        log.info("Sending verification SMS to: {}", phone);

        try {
            // In a real implementation, you would use Twilio or another SMS service
            String message = String.format("Your verification code is: %s. Valid for 30 minutes.", 
                token.getVerificationValue());
            
            // For now, just log the SMS content
            log.info("SMS to {}: {}", phone, message);
            
            // TODO: Implement actual SMS sending with Twilio
            // twilioService.sendSms(phone, message);
            
        } catch (Exception e) {
            log.error("Failed to send verification SMS to: {}", phone, e);
            throw new RuntimeException("Failed to send verification SMS", e);
        }
    }

    /**
     * Sends password reset email.
     */
    public void sendPasswordResetEmail(String email, VerificationToken token) {
        log.info("Sending password reset email to: {}", email);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setFrom(fromEmail);
            message.setSubject("Reset Your Password - Fabric Management");
            message.setText(buildPasswordResetEmailContent(token.getVerificationValue()));

            mailSender.send(message);
            log.info("Password reset email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}", email, e);
            throw new RuntimeException("Failed to send password reset email", e);
        }
    }

    /**
     * Sends password reset SMS.
     */
    public void sendPasswordResetSms(String phone, VerificationToken token) {
        log.info("Sending password reset SMS to: {}", phone);

        try {
            String message = String.format("Your password reset code is: %s. Valid for 30 minutes.", 
                token.getVerificationValue());
            
            log.info("SMS to {}: {}", phone, message);
            
            // TODO: Implement actual SMS sending with Twilio
            
        } catch (Exception e) {
            log.error("Failed to send password reset SMS to: {}", phone, e);
            throw new RuntimeException("Failed to send password reset SMS", e);
        }
    }

    /**
     * Sends welcome email after successful registration.
     */
    public void sendWelcomeEmail(String email, String username) {
        log.info("Sending welcome email to: {}", email);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setFrom(fromEmail);
            message.setSubject("Welcome to Fabric Management!");
            message.setText(buildWelcomeEmailContent(username));

            mailSender.send(message);
            log.info("Welcome email sent successfully to: {}", email);
        } catch (Exception e) {
            log.error("Failed to send welcome email to: {}", email, e);
            // Don't throw exception for welcome email as it's not critical
        }
    }

    /**
     * Builds verification email content.
     */
    private String buildVerificationEmailContent(String verificationCode) {
        return String.format("""
            Hello,
            
            Thank you for registering with Fabric Management!
            
            Please verify your email address by clicking the link below:
            %s
            
            Or use this verification code: %s
            
            This link will expire in 30 minutes.
            
            If you didn't create an account, please ignore this email.
            
            Best regards,
            Fabric Management Team
            """, 
            "https://app.fabricmanagement.com/verify?code=" + verificationCode,
            verificationCode);
    }

    /**
     * Builds password reset email content.
     */
    private String buildPasswordResetEmailContent(String resetCode) {
        return String.format("""
            Hello,
            
            You requested to reset your password for Fabric Management.
            
            Please use this reset code: %s
            
            Or click the link below:
            https://app.fabricmanagement.com/reset-password?code=%s
            
            This code will expire in 30 minutes.
            
            If you didn't request this, please ignore this email.
            
            Best regards,
            Fabric Management Team
            """, 
            resetCode,
            resetCode);
    }

    /**
     * Builds welcome email content.
     */
    private String buildWelcomeEmailContent(String username) {
        return String.format("""
            Hello %s,
            
            Welcome to Fabric Management!
            
            Your account has been successfully created and verified.
            
            You can now log in and start using our services.
            
            If you have any questions, please don't hesitate to contact our support team.
            
            Best regards,
            Fabric Management Team
            """, 
            username);
    }
}