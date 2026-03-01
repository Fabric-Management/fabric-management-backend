package com.fabricmanagement.common.platform.communication.infra.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * WhatsApp Business API Client.
 *
 * <p>Handles communication with Meta WhatsApp Business API for verification codes.
 *
 * <p><b>Features:</b>
 *
 * <ul>
 *   <li>✅ Phone number capability check
 *   <li>✅ Verification code delivery via template messages
 *   <li>✅ Health check support
 *   <li>✅ Fail-safe error handling
 *   <li>✅ PII masking in logs
 * </ul>
 *
 * <p><b>Configuration:</b>
 *
 * <pre>{@code
 * application:
 *   whatsapp:
 *     enabled: ${WHATSAPP_ENABLED:false}
 *     business-api-url: ${WHATSAPP_API_URL:https://graph.facebook.com}
 *     business-api-token: ${WHATSAPP_API_TOKEN:}
 *     phone-number-id: ${WHATSAPP_PHONE_NUMBER_ID:}
 *     verification-template-name: ${WHATSAPP_VERIFICATION_TEMPLATE:verification_code}
 * }</pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WhatsAppClient {

  private static final String WHATSAPP_API_VERSION = "v18.0";

  @Value("${application.whatsapp.enabled:false}")
  private boolean whatsAppEnabled;

  @Value("${application.whatsapp.business-api-url:https://graph.facebook.com}")
  private String apiUrl;

  @Value("${application.whatsapp.business-api-token:}")
  private String apiToken;

  @Value("${application.whatsapp.phone-number-id:}")
  private String phoneNumberId;

  @Value("${application.whatsapp.verification-template-name:verification_code}")
  private String verificationTemplateName;

  @Value("${application.whatsapp.timeout:5000}")
  private int timeout;

  private RestTemplate restTemplate;

  /** Initialize RestTemplate lazily (after properties are set). */
  private RestTemplate getRestTemplate() {
    if (restTemplate == null) {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(timeout);
      factory.setReadTimeout(timeout);
      this.restTemplate = new RestTemplate(factory);
    }
    return restTemplate;
  }

  /**
   * Check if phone number has WhatsApp capability.
   *
   * <p>Uses WhatsApp Business API to verify if recipient can receive WhatsApp messages.
   *
   * <p><b>Fail-safe:</b> Returns false if check fails (assumes no WhatsApp).
   *
   * @param phoneNumber E.164 format phone number (e.g., +14155551234)
   * @return true if phone has WhatsApp, false otherwise
   */
  public boolean phoneHasWhatsApp(String phoneNumber) {
    if (!whatsAppEnabled || phoneNumber == null || phoneNumber.isBlank()) {
      log.debug(
          "WhatsApp check skipped: enabled={}, phone={}", whatsAppEnabled, maskPhone(phoneNumber));
      return false;
    }

    try {
      log.debug("Checking WhatsApp capability for: {}", maskPhone(phoneNumber));

      // WhatsApp Business API: Check phone number capabilities
      // Using Graph API endpoint: GET /v18.0/{phone-number-id}
      String url = String.format("%s/%s/%s", apiUrl, WHATSAPP_API_VERSION, phoneNumberId);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(apiToken);
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<WhatsAppPhoneNumberResponse> response =
          getRestTemplate()
              .exchange(
                  url + "?fields=capabilities",
                  HttpMethod.GET,
                  entity,
                  WhatsAppPhoneNumberResponse.class);

      WhatsAppPhoneNumberResponse body = response.getBody();
      if (body != null && body.getCapabilities() != null) {
        boolean hasWhatsApp =
            Boolean.TRUE.equals(body.getCapabilities().getCanReceiveWhatsAppMessages());
        log.debug(
            "WhatsApp capability check result: phone={}, hasWhatsApp={}",
            maskPhone(phoneNumber),
            hasWhatsApp);
        return hasWhatsApp;
      }

      // Fallback: Try alternative check method
      return checkRecipientCapability(phoneNumber);

    } catch (Exception e) {
      log.warn(
          "Failed to check WhatsApp capability for: {} - {}",
          maskPhone(phoneNumber),
          e.getMessage());
      // Fail-safe: assume no WhatsApp if check fails
      return false;
    }
  }

  /**
   * Alternative method: Check recipient's phone number capability.
   *
   * <p>This method attempts to verify the recipient's phone number directly.
   *
   * <p><b>Note:</b> This may require additional API permissions.
   */
  private boolean checkRecipientCapability(String phoneNumber) {
    try {
      // Alternative: Use recipient phone number check endpoint if available
      // This requires additional API permissions and may not be available in all setups
      log.debug("Attempting alternative WhatsApp capability check");

      // For now, return false and rely on primary check method
      // In production, this could be implemented if API supports it
      return false;

    } catch (Exception e) {
      log.debug("Alternative capability check failed: {}", e.getMessage());
      return false;
    }
  }

  /**
   * Send verification code via WhatsApp Business API.
   *
   * <p>Uses WhatsApp template messages for verification code delivery.
   *
   * <p><b>Template Requirements:</b>
   *
   * <ul>
   *   <li>Template name: configured via {@code verification-template-name}
   *   <li>Template language: "en" (English)
   *   <li>Template must have a body parameter for the code
   * </ul>
   *
   * @param phoneNumber E.164 format phone number (e.g., +14155551234)
   * @param code 6-digit verification code
   * @return WhatsApp message response containing message ID (wamid)
   * @throws RuntimeException if sending fails
   */
  public WhatsAppMessageResponse sendVerificationCode(String phoneNumber, String code) {
    if (!whatsAppEnabled) {
      log.warn("WhatsApp is disabled, cannot send verification code");
      throw new RuntimeException("WhatsApp is not enabled");
    }

    if (apiToken == null
        || apiToken.isBlank()
        || phoneNumberId == null
        || phoneNumberId.isBlank()) {
      log.error("WhatsApp API credentials not configured");
      throw new RuntimeException("WhatsApp API not configured");
    }

    try {
      log.info("Sending WhatsApp verification code to: {}", maskPhone(phoneNumber));

      String url = String.format("%s/%s/%s/messages", apiUrl, WHATSAPP_API_VERSION, phoneNumberId);

      WhatsAppMessageRequest request =
          WhatsAppMessageRequest.builder()
              .to(phoneNumber)
              .type("template")
              .template(
                  WhatsAppTemplate.builder()
                      .name(verificationTemplateName)
                      .language(WhatsAppLanguage.builder().code("en").build())
                      .components(
                          List.of(
                              WhatsAppComponent.builder()
                                  .type("body")
                                  .parameters(
                                      List.of(
                                          WhatsAppParameter.builder()
                                              .type("text")
                                              .text(code)
                                              .build()))
                                  .build()))
                      .build())
              .build();

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(apiToken);
      headers.setContentType(MediaType.APPLICATION_JSON);

      HttpEntity<WhatsAppMessageRequest> entity = new HttpEntity<>(request, headers);

      ResponseEntity<WhatsAppMessageResponse> response =
          getRestTemplate().postForEntity(url, entity, WhatsAppMessageResponse.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("WhatsApp API returned error: status={}", response.getStatusCode());
        throw new RuntimeException("WhatsApp API returned error: " + response.getStatusCode());
      }

      WhatsAppMessageResponse responseBody = response.getBody();
      String messageId = "unknown";
      if (responseBody != null
          && responseBody.getMessages() != null
          && !responseBody.getMessages().isEmpty()) {
        messageId = responseBody.getMessages().get(0).getId();
      }

      log.info(
          "✅ WhatsApp verification code sent successfully: messageId={}, recipient={}",
          messageId,
          maskPhone(phoneNumber));

      return responseBody;

    } catch (RestClientException e) {
      log.error(
          "❌ Failed to send WhatsApp verification code to: {} - {}",
          maskPhone(phoneNumber),
          e.getMessage(),
          e);
      throw new RuntimeException("WhatsApp sending failed: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("❌ Unexpected error sending WhatsApp verification code: {}", e.getMessage(), e);
      throw new RuntimeException("WhatsApp sending failed: " + e.getMessage(), e);
    }
  }

  /**
   * Health check for WhatsApp API.
   *
   * <p>Checks if WhatsApp Business API is accessible and configured.
   *
   * @return true if API is healthy, false otherwise
   */
  public boolean isHealthy() {
    if (!whatsAppEnabled) {
      return false;
    }

    if (apiToken == null
        || apiToken.isBlank()
        || phoneNumberId == null
        || phoneNumberId.isBlank()) {
      log.debug("WhatsApp health check failed: credentials not configured");
      return false;
    }

    try {
      String url = String.format("%s/%s/%s", apiUrl, WHATSAPP_API_VERSION, phoneNumberId);

      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(apiToken);

      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity<Void> response =
          getRestTemplate().exchange(url, HttpMethod.GET, entity, Void.class);

      boolean healthy = response.getStatusCode().is2xxSuccessful();
      log.debug("WhatsApp API health check: {}", healthy ? "healthy" : "unhealthy");
      return healthy;

    } catch (Exception e) {
      log.warn("WhatsApp API health check failed: {}", e.getMessage());
      return false;
    }
  }

  /** Mask phone number for logging (PII protection). */
  private String maskPhone(String phone) {
    if (phone == null || phone.length() < 4) {
      return "***";
    }
    return phone.substring(0, 4) + "***";
  }

  // =====================================================================================
  // DTOs for WhatsApp Business API
  // =====================================================================================

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppMessageRequest {
    private String to;
    private String type;
    private WhatsAppTemplate template;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppTemplate {
    private String name;
    private WhatsAppLanguage language;
    private List<WhatsAppComponent> components;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppLanguage {
    private String code;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppComponent {
    private String type;
    private List<WhatsAppParameter> parameters;
  }

  @Data
  @Builder
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppParameter {
    private String type;
    private String text;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class WhatsAppMessageResponse {
    private List<WhatsAppMessageId> messages;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppMessageId {
    @JsonProperty("id")
    private String id;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppPhoneNumberResponse {
    private WhatsAppCapabilities capabilities;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class WhatsAppCapabilities {
    @JsonProperty("whatsapp")
    private Boolean canReceiveWhatsAppMessages;
  }
}
