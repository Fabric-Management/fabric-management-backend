package com.fabricmanagement.common.infrastructure.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the base URL for frontend-driven flows (e.g. email setup links).
 *
 * <p>Configuration hierarchy (highest priority first):</p>
 * <ol>
 *   <li>{@code FRONTEND_URL}</li>
 *   <li>{@code APP_BASE_URL}</li>
 *   <li>Fallback {@code http://localhost:3000}</li>
 * </ol>
 *
 * <p>The resolved value is cached for application lifetime.</p>
 */
@Component
@Getter
@Slf4j
public class FrontendUrlProvider {

    private final String baseUrl;

    public FrontendUrlProvider(@Value("${application.frontend.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        if (baseUrl.startsWith("http://localhost")) {
            log.warn("⚠️ Frontend base URL is using local fallback ({}). Set FRONTEND_URL or APP_BASE_URL for production.", baseUrl);
        } else {
            log.info("✅ Frontend base URL set to {}", baseUrl);
        }
    }

    /**
     * Build absolute URL targeting the frontend.
     *
     * @param pathWithQuery Path + optional query (must start with '/')
     * @return concatenated absolute URL
     */
    public String buildUrl(String pathWithQuery) {
        if (pathWithQuery == null || pathWithQuery.isBlank()) {
            return baseUrl;
        }
        if (pathWithQuery.startsWith("/")) {
            return baseUrl + pathWithQuery;
        }
        return baseUrl + "/" + pathWithQuery;
    }
}

