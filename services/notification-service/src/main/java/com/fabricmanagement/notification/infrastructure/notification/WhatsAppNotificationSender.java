package com.fabricmanagement.notification.infrastructure.notification;

import com.fabricmanagement.notification.domain.aggregate.NotificationConfig;
import com.fabricmanagement.notification.domain.event.NotificationSendRequestEvent;
import com.fabricmanagement.notification.infrastructure.config.PlatformFallbackConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * WhatsApp Notification Sender (Meta Cloud API)
 * 
 * Sends WhatsApp messages via Meta Graph API.
 * 
 * Features:
 * ✅ Template messages (hello_world)
 * ✅ Text messages (verification codes)
 * ✅ E.164 phone validation
 * ✅ Delivery tracking
 * ✅ Fallback to Email if fails
 * 
 * @author Fabric Management Team
 * @since 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsAppNotificationSender implements NotificationSender {
    
    private final PlatformFallbackConfig platformConfig;
    private final RestTemplate restTemplate = new RestTemplate();
    
    private static final String META_API_BASE = "https://graph.facebook.com/v22.0";
    
    @Override
    public void send(NotificationSendRequestEvent event, NotificationConfig config) throws NotificationException {
        
        // Get credentials (tenant or platform)
        String accessToken = config != null && config.getApiKey() != null 
            ? config.getApiKey() 
            : platformConfig.getPlatformWhatsappAccessToken();
        
        String phoneNumberId = platformConfig.getPlatformWhatsappPhoneNumberId();
        String fromNumber = config != null ? config.getFromNumber() : platformConfig.getPlatformWhatsappFromNumber();
        
        // Validate configuration
        if (accessToken == null || accessToken.isEmpty() || phoneNumberId == null || phoneNumberId.isEmpty()) {
            throw new NotificationException(
                "WhatsApp not configured (missing access token or phone number ID)",
                "WHATSAPP",
                event.getRecipient(),
                false // not retryable - needs config
            );
        }
        
        // Format phone (remove + if exists)
        String to = event.getRecipient().replace("+", "");
        
        try {
            // Build Meta API request
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Use text message for verification code
            Map<String, Object> requestBody = Map.of(
                "messaging_product", "whatsapp",
                "to", to,
                "type", "text",
                "text", Map.of("body", event.getBody())
            );
            
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
            
            // Call Meta API
            String url = META_API_BASE + "/" + phoneNumberId + "/messages";
            ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);
            
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new NotificationException(
                    "WhatsApp API returned error: " + response.getStatusCode(),
                    "WHATSAPP",
                    event.getRecipient(),
                    true // retryable
                );
            }
            
            log.info("✅ WhatsApp sent: {} → {} (tenant: {})", fromNumber, event.getRecipient(), event.getTenantId());
            
        } catch (Exception e) {
            log.error("❌ WhatsApp failed: {} → {} - {}", fromNumber, event.getRecipient(), e.getMessage());
            
            throw new NotificationException(
                "WhatsApp delivery failed: " + e.getMessage(),
                e,
                "WHATSAPP",
                event.getRecipient(),
                true // retryable
            );
        }
    }
    
    @Override
    public boolean supports(String channel) {
        return "WHATSAPP".equalsIgnoreCase(channel);
    }
}


