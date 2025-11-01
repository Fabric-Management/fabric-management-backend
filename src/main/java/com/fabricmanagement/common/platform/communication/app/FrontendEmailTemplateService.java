package com.fabricmanagement.common.platform.communication.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Frontend Email Template Service - Uses React Email templates from frontend.
 * 
 * <p>This service calls the frontend API endpoint to render React Email templates
 * into HTML. This ensures email designs are managed in one place (frontend) and
 * stay consistent with the design system.</p>
 * 
 * <p><b>Note:</b> Requires RestTemplate bean to be configured.
 * This is optional - use {@link EmailTemplateService} for backend templates instead.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * Map<String, Object> props = Map.of(
 *     "firstName", "Ahmet",
 *     "setupUrl", "http://localhost:3000/setup?token=xyz"
 * );
 * String html = frontendEmailTemplateService.render("setup-password", props);
 * }</pre>
 * 
 * <p>Available templates:</p>
 * <ul>
 *   <li><b>setup-password:</b> Password setup email</li>
 *   <li><b>welcome:</b> Welcome email after registration</li>
 *   <li><b>password-reset:</b> Password reset email</li>
 * </ul>
 */
@Service
@Slf4j
public class FrontendEmailTemplateService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${application.email.frontend.url:${FRONTEND_URL:${APP_BASE_URL:http://localhost:3000}}}")
    private String frontendUrl;

    public FrontendEmailTemplateService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Render email template from frontend API.
     * 
     * @param templateName Template name (e.g., "setup-password", "welcome")
     * @param props Template props (React component props)
     * @return Rendered HTML string
     */
    public String render(String templateName, Map<String, Object> props) {
        try {
            log.debug("Rendering email template: {}, props: {}", templateName, props);

            // Build request body
            Map<String, Object> requestBody = Map.of(
                "template", templateName,
                "props", props != null ? props : Map.of()
            );

            // Call frontend API
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            String responseBody = restTemplate.postForObject(
                frontendUrl + "/api/emails/render",
                request,
                String.class
            );

            if (responseBody == null || responseBody.isEmpty()) {
                throw new RuntimeException("Empty response from frontend email API");
            }

            // Parse JSON response
            JsonNode jsonResponse = objectMapper.readTree(responseBody);
            
            if (!jsonResponse.has("success") || !jsonResponse.get("success").asBoolean()) {
                String error = jsonResponse.has("error") 
                    ? jsonResponse.get("error").asText() 
                    : "Unknown error";
                throw new RuntimeException("Frontend email API error: " + error);
            }

            String html = jsonResponse.get("html").asText();
            log.debug("✅ Email template rendered successfully: {}", templateName);
            
            return html;

        } catch (Exception e) {
            // Log at debug level since fallback will handle it gracefully
            // Full stack trace only if debug is enabled (reduces log noise)
            if (log.isDebugEnabled()) {
                log.debug("Failed to render email template from frontend: {} - {}", templateName, e.getMessage(), e);
            } else {
                log.warn("Failed to render email template from frontend: {} - {} (fallback to backend)", 
                    templateName, e.getMessage());
            }
            throw new RuntimeException("Failed to render email template: " + e.getMessage(), e);
        }
    }

    /**
     * Convenience method for setup-password email.
     */
    public String renderSetupPasswordEmail(String firstName, String setupUrl, String expiresIn) {
        Map<String, Object> props = Map.of(
            "firstName", firstName != null ? firstName : "there",
            "setupUrl", setupUrl,
            "expiresIn", expiresIn != null ? expiresIn : "24 hours"
        );
        return render("setup-password", props);
    }

    /**
     * Convenience method for welcome email.
     * 
     * <p>Note: Frontend WelcomeEmail expects "dashboardUrl" but sales-led flow
     * uses "setupUrl" for password setup. We map setupUrl → dashboardUrl for consistency.</p>
     */
    public String renderWelcomeEmail(String firstName, String companyName, String setupUrl) {
        Map<String, Object> props = Map.of(
            "firstName", firstName != null ? firstName : "there",
            "companyName", companyName != null ? companyName : "",
            "dashboardUrl", setupUrl  // Map setupUrl to dashboardUrl (frontend expects dashboardUrl)
        );
        return render("welcome", props);
    }
}

