package com.fabricmanagement.common.infrastructure.config;

import java.util.Set;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the base URL for frontend-driven flows (e.g. email setup links).
 *
 * <p>Configuration hierarchy (highest priority first):
 *
 * <ol>
 *   <li>{@code FRONTEND_URL}
 *   <li>{@code APP_BASE_URL}
 *   <li>Fallback {@code http://localhost:3000}
 * </ol>
 *
 * <p>The resolved value is cached for application lifetime.
 */
@Component
@Getter
@Slf4j
public class FrontendUrlProvider {

  private final String baseUrl;

  private static final Set<String> LOCAL_PROFILES = Set.of("local", "dev");

  public FrontendUrlProvider(
      @Value("${application.frontend.base-url}") String baseUrl,
      @Value("${spring.profiles.active:local}") String activeProfile) {
    this.baseUrl = baseUrl;
    if (baseUrl.startsWith("http://localhost") && !LOCAL_PROFILES.contains(activeProfile)) {
      log.warn(
          "⚠️ Frontend base URL is using local fallback ({}). Set FRONTEND_URL or APP_BASE_URL for production.",
          baseUrl);
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
