package com.fabricmanagement.common.platform.communication.domain.strategy;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email verification strategy - Priority 2.
 *
 * <p>Sends verification codes via SMTP email.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailStrategy implements VerificationStrategy {

    private final JavaMailSender mailSender;

    @Value("${application.mail.from-email}")
    private String fromEmail;

    @Value("${application.mail.from-name}")
    private String fromName;

    @Override
    public void sendVerificationCode(String recipient, String code) {
        log.info("Sending verification email to: {}", recipient);

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(String.format("%s <%s>", fromName, fromEmail));
            message.setTo(recipient);
            message.setSubject("Verify your account - " + fromName);
            message.setText(buildEmailBody(code));

            mailSender.send(message);

            log.info("✅ Verification email sent successfully to: {}", recipient);
        } catch (Exception e) {
            log.error("❌ Failed to send verification email to: {}", recipient, e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    /**
     * Send custom email with HTML template (general purpose).
     *
     * @param recipient Email address
     * @param subject Email subject
     * @param body Email body (plain text - will be wrapped in HTML)
     */
    public void sendEmail(String recipient, String subject, String body) {
        log.info("Sending custom email to: {}", recipient);

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setFrom(String.format("%s <%s>", fromName, fromEmail));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(buildHtmlTemplate(subject, body), true);

            mailSender.send(mimeMessage);

            log.info("✅ Custom email sent successfully to: {}", recipient);
        } catch (Exception e) {
            log.error("❌ Failed to send custom email to: {}", recipient, e);
            throw new RuntimeException("Email sending failed: " + e.getMessage(), e);
        }
    }

    /**
     * Build clean, flat design HTML email template.
     */
    private String buildHtmlTemplate(String title, String content) {
        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <style>
                    body { margin: 0; padding: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background-color: #f5f5f5; }
                    .container { max-width: 600px; margin: 40px auto; background: white; border-radius: 8px; overflow: hidden; border: 1px solid #e5e7eb; }
                    .header { background: #667eea; padding: 30px; text-align: center; }
                    .header h1 { color: #ffffff; margin: 0; font-size: 24px; font-weight: 600; }
                    .content { padding: 40px 30px; line-height: 1.8; color: #1f2937; }
                    .footer { background: #f9fafb; padding: 25px 30px; text-align: center; color: #6b7280; font-size: 13px; border-top: 1px solid #e5e7eb; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>%s</h1>
                    </div>
                    <div class="content">
                        %s
                    </div>
                    <div class="footer">
                        <p style="margin: 0;"><strong>FabricOS</strong> - Fabric Management Platform</p>
                        <p style="margin: 8px 0 0 0; font-size: 12px; color: #9ca3af;">
                            If you didn't request this, please ignore this email.
                        </p>
                    </div>
                </div>
            </body>
            </html>
            """, title, formatContent(content));
    }

    /**
     * Format content - wrap in paragraph if not HTML.
     */
    private String formatContent(String content) {
        if (content.contains("<p>") || content.contains("<div>")) {
            return content;
        }
        return "<p>" + content.replace("\n\n", "</p><p>").replace("\n", "<br>") + "</p>";
    }

    @Override
    public boolean isAvailable() {
        // Check if mail sender is configured
        try {
            return mailSender != null;
        } catch (Exception e) {
            log.warn("Email sender not available: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public int priority() {
        return 2; // Priority 2 (after WhatsApp)
    }

    @Override
    public String name() {
        return "Email";
    }

    private String buildEmailBody(String code) {
        return String.format("""
            Welcome to %s!
            
            Hello,
            
            Thank you for registering. Please verify your email address using the code below:
            
            %s
            
            This code will expire in 15 minutes.
            
            If you didn't create this account, please ignore this email.
            
            Best regards,
            %s Team
            
            ---
            This is an automated message, please do not reply.
            """, fromName, code, fromName);
    }
}

