package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import com.fabricmanagement.platform.communication.app.EmailRecipientPolicy.Resolution;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Simple email service for async sending with basic notification template.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>@Async — non-blocking, does not hold request thread
 *   <li>On error: logs and continues (does not block system)
 *   <li>Template: simple HTML with title, message, link
 * </ul>
 *
 * <p>Email format for notifications:
 *
 * <pre>
 * Subject: "[FabricManagement] " + title
 * Body:    message + "\n\n" + deepLink (or HTML template)
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

  private static final String SUBJECT_PREFIX = "[FabricManagement] ";

  private final EmailOutboxService emailOutboxService;
  private final com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy
      emailStrategy;
  private final EmailRecipientPolicy emailRecipientPolicy;

  @Value("${application.email.use-outbox:true}")
  private boolean useOutbox;

  /**
   * Send email asynchronously. Does not block caller; errors are logged only.
   *
   * @param to Recipient email
   * @param subject Email subject
   * @param body Email body (HTML)
   */
  @Async
  public void sendAsync(String to, String subject, String body) {
    sendAsync(TenantContext.requireTenantId(), to, subject, body);
  }

  @Async
  public void sendAsync(UUID tenantId, String to, String subject, String body) {
    try {
      if (useOutbox) {
        emailOutboxService.queueEmail(tenantId, to, subject, body);
        log.debug("Email queued: to={}", PiiMaskingUtil.maskEmail(to));
      } else {
        Resolution resolution = emailRecipientPolicy.resolveFor(tenantId, to, subject);
        if (resolution.dropped()) {
          return;
        }
        emailStrategy.sendEmail(resolution.recipient(), resolution.subject(), body);
        log.debug("Email sent: to={}", PiiMaskingUtil.maskEmail(resolution.recipient()));
      }
    } catch (Exception e) {
      log.error("Failed to send email to {}: {}", PiiMaskingUtil.maskEmail(to), e.getMessage(), e);
      // Do not rethrow — avoid blocking the system
    }
  }

  /**
   * Send notification-style email with standard format.
   *
   * <p>Subject: "[FabricManagement] " + title
   *
   * <p>Body: Simple HTML with title, message, and optional deep link.
   *
   * @param to Recipient email
   * @param title Notification title
   * @param message Notification message
   * @param deepLink Optional link (e.g. to view details). Null = omitted.
   */
  @Async
  public void sendNotificationEmail(String to, String title, String message, String deepLink) {
    sendNotificationEmail(TenantContext.requireTenantId(), to, title, message, deepLink);
  }

  @Async
  public void sendNotificationEmail(
      UUID tenantId, String to, String title, String message, String deepLink) {
    String subject = SUBJECT_PREFIX + title;
    String body = buildNotificationBody(title, message, deepLink);
    sendAsync(tenantId, to, subject, body);
  }

  /**
   * Build simple HTML body: title + message + link.
   *
   * @param title Notification title
   * @param message Notification message
   * @param deepLink Optional link. Null = plain message only.
   * @return HTML string
   */
  public String buildNotificationBody(String title, String message, String deepLink) {
    StringBuilder html = new StringBuilder();
    html.append("<!DOCTYPE html><html><body style=\"font-family:sans-serif;padding:1rem;\">");
    html.append("<h2>").append(escapeHtml(title)).append("</h2>");
    html.append("<p>").append(escapeHtml(message)).append("</p>");
    if (deepLink != null && !deepLink.isBlank()) {
      html.append("<p><a href=\"").append(escapeHtml(deepLink)).append("\">View details</a></p>");
    }
    html.append("</body></html>");
    return html.toString();
  }

  private String escapeHtml(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
