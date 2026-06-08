package com.fabricmanagement.platform.communication.api.controller;

import com.fabricmanagement.platform.communication.app.WhatsAppWebhookService;
import com.fabricmanagement.platform.communication.infra.webhook.WhatsAppWebhookPayload;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * WhatsApp Webhook Controller - Receives delivery status updates from Meta.
 *
 * <p>Meta WhatsApp Business API sends webhook notifications for message status updates.
 *
 * <p><b>Webhook Setup:</b>
 *
 * <ol>
 *   <li>Configure webhook URL in Meta Business Manager: {@code
 *       https://your-domain.com/api/webhooks/whatsapp}
 *   <li>Set verify token in application.yml: {@code application.whatsapp.webhook-verify-token}
 *   <li>Subscribe to "messages" webhook field
 * </ol>
 *
 * <p><b>Security:</b>
 *
 * <ul>
 *   <li>Webhook verification via verify token (GET request)
 *   <li>X-Hub-Signature-256 HMAC-SHA256 validation via {@link
 *       com.fabricmanagement.platform.communication.infra.webhook.WebhookSignatureFilter}
 * </ul>
 *
 * @see <a href="https://developers.facebook.com/docs/whatsapp/cloud-api/webhooks">WhatsApp
 *     Webhooks</a>
 */
@RestController
@RequestMapping("/api/v1/webhooks/whatsapp")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "WhatsApp Webhooks", description = "WhatsApp Business API webhook endpoints")
public class WhatsAppWebhookController {

  private final WhatsAppWebhookService webhookService;

  @Value("${application.whatsapp.webhook-verify-token:}")
  private String webhookVerifyToken;

  /**
   * Webhook verification endpoint (GET).
   *
   * <p>Meta sends a GET request to verify the webhook URL during setup.
   *
   * <p><b>Query Parameters:</b>
   *
   * <ul>
   *   <li>hub.mode - Should be "subscribe"
   *   <li>hub.verify_token - Should match configured verify token
   *   <li>hub.challenge - Random string to echo back
   * </ul>
   *
   * @param mode Verification mode
   * @param token Verify token
   * @param challenge Challenge string
   * @return Challenge string if verification succeeds
   */
  @GetMapping
  @Operation(
      summary = "Webhook verification",
      description = "Meta webhook verification endpoint (GET)")
  public ResponseEntity<String> verifyWebhook(
      @RequestParam("hub.mode") String mode,
      @RequestParam("hub.verify_token") String token,
      @RequestParam("hub.challenge") String challenge) {

    log.info("Webhook verification request: mode={}, token={}", mode, token);

    if ("subscribe".equals(mode) && webhookVerifyToken.equals(token)) {
      log.info("✅ Webhook verification successful");
      return ResponseEntity.ok(challenge);
    }

    log.warn("❌ Webhook verification failed: invalid mode or token");
    return ResponseEntity.status(403).body("Verification failed");
  }

  /**
   * Webhook notification endpoint (POST).
   *
   * <p>Meta sends POST requests with message status updates.
   *
   * <p><b>Status Updates:</b>
   *
   * <ul>
   *   <li>sent - Message sent to WhatsApp server
   *   <li>delivered - Message delivered to recipient
   *   <li>read - Message read by recipient
   *   <li>failed - Message delivery failed
   * </ul>
   *
   * @param payload Webhook payload from Meta
   * @return 200 OK to acknowledge receipt
   */
  @PostMapping
  @Operation(
      summary = "Webhook notification",
      description = "Receives message status updates from Meta")
  public ResponseEntity<Void> handleWebhook(@RequestBody WhatsAppWebhookPayload payload) {

    log.info("Received WhatsApp webhook notification");
    log.debug("Webhook payload: {}", payload);

    try {
      webhookService.processWebhook(payload);
      return ResponseEntity.ok().build();
    } catch (Exception e) {
      log.error("Error processing webhook: {}", e.getMessage(), e);
      return ResponseEntity.ok().build();
    }
  }
}
