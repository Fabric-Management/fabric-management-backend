package com.fabricmanagement.notification.hub.infra.email;

import com.fabricmanagement.common.platform.communication.app.EmailOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * EMAIL kanalı — mevcut EmailOutboxService (Transactional Outbox pattern) üzerine köprü.
 *
 * <p>Notification sisteminden email gönderim talebi geldiğinde, EmailOutbox'a yazar. Gerçek iletim
 * EmailOutboxService.processEmailQueue() tarafından yapılır. Bu sayede email de notification
 * kuyruğunun retry mekanizmasından bağımsız, kendi retry'ıyla çalışır.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailNotificationSender {

  private final EmailOutboxService emailOutboxService;

  /**
   * Bildirimi email olarak kuyruğa alır.
   *
   * @param recipientEmail Alıcı email adresi
   * @param title Email subject (i18n'den render edilmiş)
   * @param body Email body (HTML formatlanabilir)
   */
  public void send(String recipientEmail, String title, String body) {
    if (recipientEmail == null || recipientEmail.isBlank()) {
      log.warn("EMAIL notification skipped — recipient email is blank");
      return;
    }
    try {
      String htmlBody = buildHtmlBody(body);
      emailOutboxService.queueEmail(recipientEmail, title, htmlBody);
      log.debug("EMAIL notification queued for recipient={}", maskEmail(recipientEmail));
    } catch (Exception ex) {
      log.error(
          "Failed to queue EMAIL notification for recipient={}", maskEmail(recipientEmail), ex);
      throw ex;
    }
  }

  private String buildHtmlBody(String plainBody) {
    return """
        <!DOCTYPE html>
        <html>
        <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
          <div style="background: #f8f9fa; border-radius: 8px; padding: 24px;">
            <p style="color: #333; font-size: 14px; line-height: 1.6; margin: 0;">%s</p>
          </div>
          <p style="color: #999; font-size: 11px; margin-top: 16px;">
            This is an automated email from Fabric Management.
          </p>
        </body>
        </html>
        """
        .formatted(plainBody.replace("\n", "<br>"));
  }

  private String maskEmail(String email) {
    if (email == null || !email.contains("@")) return "***";
    int at = email.indexOf('@');
    return email.substring(0, Math.min(3, at)) + "***" + email.substring(at);
  }
}
