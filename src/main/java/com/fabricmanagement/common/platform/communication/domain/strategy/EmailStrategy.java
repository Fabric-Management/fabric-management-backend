package com.fabricmanagement.common.platform.communication.domain.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
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

    @Value("${spring.mail.from:info@storeandsale.shop}")
    private String fromEmail;

    @Value("${spring.mail.from-name:Fabricode}")
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
            Welcome to FabriCode!
            
            Hi Admin,
            
            Thank you for registering. Please verify your email address using the code below:
            
            %s
            
            This code will expire in 15 minutes.
            
            If you didn't create this account, please ignore this email.
            
            Best regards,
            FabriCode Team
            
            ---
            Powered by Akkayalar Group
            """, code);
    }
}

