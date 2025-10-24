package com.fabricmanagement.notification.infrastructure.notification;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.infrastructure.config.PlatformFallbackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;

/**
 * Email Notification Sender
 * 
 * Sends emails via SMTP (tenant-specific or platform fallback).
 * 
 * Features:
 * - Dynamic SMTP configuration per tenant
 * - HTML email support
 * - Template rendering
 * - Fallback to platform SMTP
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationSender implements NotificationSender {
    
    private final JavaMailSender defaultMailSender;
    private final PlatformFallbackConfig platformConfig;
    
    @Override
    public void send(NotificationSendRequestEvent event, NotificationConfig config) throws NotificationException {
        try {
            // Select mail sender (tenant-specific or platform)
            JavaMailSender mailSender = config != null 
                ? createTenantMailSender(config)
                : defaultMailSender;
            
            // Get from address
            String from = config != null && config.getFromEmail() != null
                ? config.getFromEmail()
                : platformConfig.getPlatformEmailFrom();
            
            String fromName = config != null && config.getFromName() != null
                ? config.getFromName()
                : platformConfig.getPlatformEmailFromName();
            
            // Create message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(from, fromName);
            helper.setTo(event.getRecipient());
            helper.setSubject(event.getSubject());
            helper.setText(renderBody(event), true); // HTML enabled
            
            // Send
            mailSender.send(message);
            
            log.info("✅ Email sent: {} → {} (tenant: {})", 
                from, event.getRecipient(), event.getTenantId());
            
        } catch (MailException | MessagingException e) {
            log.error("❌ Email failed: {} → {} (tenant: {}) - {}", 
                config != null ? config.getFromEmail() : platformConfig.getPlatformEmailFrom(),
                event.getRecipient(),
                event.getTenantId(),
                e.getMessage());
            
            throw new NotificationException(
                "Email delivery failed: " + e.getMessage(),
                e,
                "EMAIL",
                event.getRecipient(),
                true // retryable
            );
        } catch (Exception e) {
            log.error("❌ Email fatal error: {}", e.getMessage(), e);
            throw new NotificationException(
                "Email fatal error: " + e.getMessage(),
                e,
                "EMAIL",
                event.getRecipient(),
                false // not retryable
            );
        }
    }
    
    @Override
    public boolean supports(String channel) {
        return "EMAIL".equalsIgnoreCase(channel);
    }
    
    /**
     * Create tenant-specific mail sender with custom SMTP config
     */
    private JavaMailSender createTenantMailSender(NotificationConfig config) {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        
        mailSender.setHost(config.getSmtpHost());
        mailSender.setPort(config.getSmtpPort());
        mailSender.setUsername(config.getSmtpUsername());
        mailSender.setPassword(config.getSmtpPassword());
        
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", config.getSmtpHost());
        
        return mailSender;
    }
    
    /**
     * Render email body from template or raw body
     */
    private String renderBody(NotificationSendRequestEvent event) {
        if (event.getTemplate() != null && event.getVariables() != null) {
            return renderTemplate(event);
        }
        return event.getBody();
    }
    
    /**
     * Render template with variables
     */
    private String renderTemplate(NotificationSendRequestEvent event) {
        String body = event.getBody() != null ? event.getBody() : "";
        
        // Simple template rendering (replace {variable} placeholders)
        if (event.getVariables() != null) {
            for (var entry : event.getVariables().entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                body = body.replace(placeholder, entry.getValue());
            }
        }
        
        // Wrap in HTML template
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .code { font-size: 24px; font-weight: bold; color: #007bff; letter-spacing: 2px; }
                </style>
            </head>
            <body>
                <div class="container">
                    %s
                </div>
            </body>
            </html>
            """.formatted(body);
    }
}

