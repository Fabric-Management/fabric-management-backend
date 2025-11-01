package com.fabricmanagement.common.platform.communication.app;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Email Template Renderer - Smart template provider with fallback.
 * 
 * <p>This component decides which template service to use based on configuration
 * and provides automatic fallback to backend templates if frontend is unavailable.</p>
 * 
 * <h2>Priority Order:</h2>
 * <ol>
 *   <li>Frontend templates (if enabled and available)</li>
 *   <li>Backend templates (fallback, always available)</li>
 * </ol>
 * 
 * <h2>Configuration:</h2>
 * <pre>
 * application:
 *   email:
 *     template-provider: frontend  # frontend | backend | auto
 *     frontend:
 *       enabled: true
 *       timeout-ms: 5000
 * </pre>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailTemplateRenderer {

    private final FrontendEmailTemplateService frontendEmailTemplateService;
    private final EmailTemplateService backendEmailTemplateService;

    @Value("${application.email.template-provider:auto}")
    private String templateProvider;

    @Value("${application.email.frontend.enabled:true}")
    private boolean frontendEnabled;

    @Value("${application.email.frontend.timeout-ms:5000}")
    private int frontendTimeoutMs;
    
    // Circuit breaker state (simple in-memory tracking)
    private volatile boolean frontendCircuitOpen = false;
    private volatile long circuitOpenUntil = 0;
    private static final long CIRCUIT_BREAKER_TIMEOUT_MS = 60000; // 1 minute

    /**
     * Render email template with automatic fallback.
     * 
     * @param templateName Template name (e.g., "setup-password")
     * @param variables Template variables
     * @return Rendered HTML
     */
    public String render(String templateName, Map<String, String> variables) {
        // Auto mode: Try frontend first, fallback to backend
        if ("auto".equalsIgnoreCase(templateProvider)) {
            return renderWithFallback(templateName, variables);
        }

        // Explicit frontend mode
        if ("frontend".equalsIgnoreCase(templateProvider) && frontendEnabled) {
            try {
                return renderFromFrontend(templateName, variables);
            } catch (Exception e) {
                log.warn("Frontend template failed, falling back to backend: {}", e.getMessage());
                return renderFromBackend(templateName, variables);
            }
        }

        // Backend mode (default)
        return renderFromBackend(templateName, variables);
    }

    /**
     * Render with automatic fallback: Frontend → Backend.
     * 
     * <p>Performance optimizations:</p>
     * <ul>
     *   <li>Circuit breaker: Skip frontend if recently failed (1 minute cooldown)</li>
     *   <li>Fast fallback: Immediately use backend if circuit is open</li>
     *   <li>Fast timeout: RestTemplate already configured with 5s timeout</li>
     * </ul>
     */
    private String renderWithFallback(String templateName, Map<String, String> variables) {
        if (!frontendEnabled) {
            log.debug("Frontend templates disabled, using backend: {}", templateName);
            return renderFromBackend(templateName, variables);
        }
        
        // Circuit breaker check: Skip frontend if circuit is open
        if (frontendCircuitOpen && System.currentTimeMillis() < circuitOpenUntil) {
            log.debug("Frontend circuit breaker open, using backend fallback: {}", templateName);
            return renderFromBackend(templateName, variables);
        }
        
        // Reset circuit if timeout passed
        if (frontendCircuitOpen && System.currentTimeMillis() >= circuitOpenUntil) {
            frontendCircuitOpen = false;
            log.debug("Frontend circuit breaker reset, attempting frontend: {}", templateName);
        }
        
        try {
            log.debug("Attempting to render from frontend: {}", templateName);
            String result = renderFromFrontend(templateName, variables);
            // Success - reset circuit breaker
            frontendCircuitOpen = false;
            return result;
        } catch (Exception e) {
            // Failure - open circuit breaker
            frontendCircuitOpen = true;
            circuitOpenUntil = System.currentTimeMillis() + CIRCUIT_BREAKER_TIMEOUT_MS;
            
            // Log concisely - this is expected behavior (fallback exists)
            String errorMsg = e.getMessage();
            if (errorMsg != null && errorMsg.length() > 100) {
                errorMsg = errorMsg.substring(0, 100) + "...";
            }
            log.info("⚠️ Frontend template unavailable ({}), using backend fallback. Circuit breaker open for 60s: {}", 
                errorMsg, templateName);
            
            // Fallback to backend template (always succeeds)
            return renderFromBackend(templateName, variables);
        }
    }

    /**
     * Render from frontend React Email templates.
     */
    private String renderFromFrontend(String templateName, Map<String, String> variables) {
        // Convert Map<String, String> to Map<String, Object> for frontend
        Map<String, Object> frontendProps = new java.util.HashMap<>(variables);
        
        // Map backend prop names to frontend prop names if needed
        String frontendTemplateName = mapTemplateName(templateName);
        
        // For welcome template: backend sends "setupUrl" but frontend expects "dashboardUrl"
        if ("welcome".equals(frontendTemplateName) && frontendProps.containsKey("setupUrl")) {
            Object setupUrl = frontendProps.remove("setupUrl");
            frontendProps.put("dashboardUrl", setupUrl);
        }
        
        return frontendEmailTemplateService.render(frontendTemplateName, frontendProps);
    }

    /**
     * Map backend template names to frontend template names.
     */
    private String mapTemplateName(String backendTemplateName) {
        return switch (backendTemplateName) {
            case "self-service-welcome.html" -> "setup-password";
            case "sales-led-welcome.html" -> "welcome";
            case "password-reset.html" -> "password-reset";
            default -> backendTemplateName.replace(".html", "");
        };
    }

    /**
     * Render from backend HTML templates.
     */
    private String renderFromBackend(String templateName, Map<String, String> variables) {
        return backendEmailTemplateService.render(templateName, variables);
    }

    // Convenience methods for common templates

    /**
     * Render setup-password email.
     */
    public String renderSetupPassword(String firstName, String companyName, String email, String setupUrl) {
        Map<String, String> vars = Map.of(
            "firstName", firstName != null ? firstName : "there",
            "companyName", companyName != null ? companyName : "",
            "email", email != null ? email : "",
            "setupUrl", setupUrl
        );
        return render("self-service-welcome.html", vars);
    }

    /**
     * Render welcome email.
     */
    public String renderWelcome(String firstName, String companyName, String subscriptionsList, String setupUrl) {
        Map<String, String> vars = Map.of(
            "firstName", firstName != null ? firstName : "there",
            "companyName", companyName != null ? companyName : "",
            "subscriptionsList", subscriptionsList != null ? subscriptionsList : "",
            "setupUrl", setupUrl
        );
        return render("sales-led-welcome.html", vars);
    }

    /**
     * Render password reset email.
     */
    public String renderPasswordReset(String firstName, String resetUrl, String expiresIn, String verificationCode) {
        Map<String, String> vars = new java.util.HashMap<>();
        vars.put("firstName", firstName != null ? firstName : "there");
        vars.put("resetUrl", resetUrl);
        vars.put("expiresIn", expiresIn != null ? expiresIn : "30 minutes");
        
        // Verification code is optional - only include if provided
        if (verificationCode != null && !verificationCode.isEmpty()) {
            String codeHtml = "<div style='margin-top: 16px; padding: 16px; background-color: #ffffff; border: 1px solid #E5E7EB; border-radius: 8px; text-align: center;'>"
                + "<p style='font-size: 12px; color: #6b7280; margin: 0 0 8px 0; text-transform: uppercase; letter-spacing: 0.5px;'>Verification Code</p>"
                + "<p style='font-size: 32px; font-weight: 700; color: #111827; letter-spacing: 4px; margin: 0; font-family: monospace;'>" + verificationCode + "</p>"
                + "</div>";
            vars.put("verificationCode", codeHtml);
        } else {
            vars.put("verificationCode", "");
        }
        
        return render("password-reset.html", vars);
    }
}

