package com.fabricmanagement.platform.communication.infra.webhook;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Validates Meta webhook POST payloads using X-Hub-Signature-256 HMAC-SHA256 signature.
 *
 * <p>Meta signs every webhook POST with an HMAC using the App Secret. This filter intercepts
 * requests to /api/webhooks/whatsapp, reads the raw body, computes the expected signature, and
 * rejects requests where the signature is missing or invalid.
 *
 * <p>GET requests (webhook verification) are passed through without signature checks.
 *
 * @see <a
 *     href="https://developers.facebook.com/docs/graph-api/webhooks/getting-started#verification-requests">Meta
 *     Webhook Docs</a>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@Slf4j
public class WebhookSignatureFilter extends OncePerRequestFilter {

  private static final String HMAC_SHA256 = "HmacSHA256";
  private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
  private static final String SIGNATURE_PREFIX = "sha256=";
  private static final String WEBHOOK_PATH = "/api/webhooks/whatsapp";

  @Value("${application.whatsapp.app-secret:}")
  private String appSecret;

  @Value("${application.whatsapp.webhook-signature-enabled:true}")
  private boolean signatureEnabled;

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    // Only filter POST requests to the webhook path
    return !WEBHOOK_PATH.equals(path) || !"POST".equalsIgnoreCase(method);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    if (!signatureEnabled || appSecret == null || appSecret.isBlank()) {
      log.warn(
          "Webhook signature verification DISABLED — "
              + "set application.whatsapp.app-secret and "
              + "application.whatsapp.webhook-signature-enabled=true for production");
      filterChain.doFilter(request, response);
      return;
    }

    byte[] body = request.getInputStream().readAllBytes();

    String signatureHeader = request.getHeader(SIGNATURE_HEADER);
    if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
      log.warn("Webhook rejected: missing or malformed {} header", SIGNATURE_HEADER);
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("Missing signature");
      return;
    }

    String receivedSignature = signatureHeader.substring(SIGNATURE_PREFIX.length());
    String expectedSignature = computeHmacSha256(body, appSecret);

    if (expectedSignature == null || !constantTimeEquals(expectedSignature, receivedSignature)) {
      log.warn(
          "Webhook rejected: invalid {} signature (payload manipulation suspected)",
          SIGNATURE_HEADER);
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.getWriter().write("Invalid signature");
      return;
    }

    log.debug("Webhook signature verified successfully");

    // Wrap request so downstream can re-read the body
    filterChain.doFilter(new CachedBodyRequestWrapper(request, body), response);
  }

  private String computeHmacSha256(byte[] data, String secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_SHA256);
      SecretKeySpec keySpec =
          new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
      mac.init(keySpec);
      byte[] hash = mac.doFinal(data);
      return bytesToHex(hash);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      log.error("Failed to compute HMAC-SHA256", e);
      return null;
    }
  }

  /** Constant-time comparison to prevent timing attacks. */
  private boolean constantTimeEquals(String a, String b) {
    if (a.length() != b.length()) {
      return false;
    }
    int result = 0;
    for (int i = 0; i < a.length(); i++) {
      result |= a.charAt(i) ^ b.charAt(i);
    }
    return result == 0;
  }

  private static String bytesToHex(byte[] bytes) {
    StringBuilder sb = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      sb.append(String.format("%02x", b));
    }
    return sb.toString();
  }

  /**
   * Wraps the original request with a cached body so that downstream handlers (e.g. @RequestBody)
   * can read the body after the filter already consumed it.
   */
  private static class CachedBodyRequestWrapper extends HttpServletRequestWrapper {
    private final byte[] cachedBody;

    CachedBodyRequestWrapper(HttpServletRequest request, byte[] body) {
      super(request);
      this.cachedBody = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
      return new ServletInputStream() {
        @Override
        public boolean isFinished() {
          return bais.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          // no-op for cached stream
        }

        @Override
        public int read() {
          return bais.read();
        }
      };
    }
  }
}
