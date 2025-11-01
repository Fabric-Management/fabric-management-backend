package com.fabricmanagement.common.platform.communication.app;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Email Template Service - Load and render email templates.
 * 
 * <p>Simple template engine using placeholder replacement.
 * Templates are stored in {@code resources/templates/emails/}.</p>
 * 
 * <h2>Usage:</h2>
 * <pre>{@code
 * Map<String, String> variables = Map.of(
 *     "firstName", "Ahmet",
 *     "companyName", "ABC Tekstil",
 *     "setupUrl", "http://localhost:3000/setup?token=xyz"
 * );
 * String html = emailTemplateService.render("self-service-welcome.html", variables);
 * }</pre>
 */
@Service
@Slf4j
public class EmailTemplateService {

    private static final String TEMPLATE_BASE_PATH = "templates/emails/";

    /**
     * Load and render email template with variables.
     * 
     * @param templateName Template filename (e.g., "self-service-welcome.html")
     * @param variables Variable map for placeholder replacement
     * @return Rendered HTML content
     */
    public String render(String templateName, Map<String, String> variables) {
        try {
            String template = loadTemplate(templateName);
            return replaceVariables(template, variables);
        } catch (IOException e) {
            log.error("Failed to load email template: {}", templateName, e);
            throw new RuntimeException("Email template not found: " + templateName, e);
        }
    }

    /**
     * Load template from classpath.
     */
    private String loadTemplate(String templateName) throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_BASE_PATH + templateName);
        
        if (!resource.exists()) {
            throw new IOException("Template not found: " + TEMPLATE_BASE_PATH + templateName);
        }

        try (var inputStream = resource.getInputStream()) {
            return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
        }
    }

    /**
     * Replace {{variable}} placeholders with actual values.
     */
    private String replaceVariables(String template, Map<String, String> variables) {
        String result = template;
        
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }

        // Log any remaining placeholders (for debugging)
        if (result.contains("{{")) {
            log.warn("⚠️ Unreplaced placeholders found in template");
        }

        return result;
    }

    /**
     * Convenience method for single variable replacement.
     */
    public String render(String templateName, String key, String value) {
        Map<String, String> variables = new HashMap<>();
        variables.put(key, value);
        return render(templateName, variables);
    }
}

