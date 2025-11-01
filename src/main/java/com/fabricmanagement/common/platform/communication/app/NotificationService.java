package com.fabricmanagement.common.platform.communication.app;

import com.fabricmanagement.common.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Notification Service - Send notifications via email (async for better UX).
 *
 * <p>Email sending is asynchronous to prevent blocking user requests.
 * Users get immediate response while email is sent in background.</p>
 *
 * <h2>Usage:</h2>
 * <pre>{@code
 * // Async (default) - doesn't block user response
 * notificationService.sendNotification(
 *     "user@example.com",
 *     "Welcome to FabricOS",
 *     "Your account has been created..."
 * );
 * 
 * // Sync (for critical emails that must be sent before response)
 * notificationService.sendNotificationSync(
 *     "user@example.com",
 *     "Security Alert",
 *     "Your password was changed..."
 * );
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final EmailStrategy emailStrategy;

    /**
     * Send notification via email (async - doesn't block user response).
     * 
     * <p>Performance: Non-blocking async execution ensures fast user responses.
     * Email sending happens in background thread pool.</p>
     *
     * @param recipient Email address
     * @param subject Email subject
     * @param message Email body
     */
    @Async
    public void sendNotification(String recipient, String subject, String message) {
        log.info("Sending notification (async) to: {}", PiiMaskingUtil.maskEmail(recipient));

        try {
            emailStrategy.sendEmail(recipient, subject, message);
            log.info("✅ Notification sent successfully to: {}", PiiMaskingUtil.maskEmail(recipient));
        } catch (Exception e) {
            log.error("❌ Failed to send notification to: {}", PiiMaskingUtil.maskEmail(recipient), e);
        }
    }

    /**
     * Send notification synchronously (for critical emails).
     *
     * @param recipient Email address
     * @param subject Email subject
     * @param message Email body
     */
    public void sendNotificationSync(String recipient, String subject, String message) {
        log.info("Sending notification (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));

        emailStrategy.sendEmail(recipient, subject, message);
        
        log.info("✅ Notification sent (sync) to: {}", PiiMaskingUtil.maskEmail(recipient));
    }
}

