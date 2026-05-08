package com.fabricmanagement.common.infrastructure.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC Configuration - Registers global interceptors.
 *
 * <p>Registers JwtContextInterceptor to automatically handle JWT-based tenant context for all
 * requests (except excluded public endpoints).
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebMvcConfig implements WebMvcConfigurer {

  private final JwtContextInterceptor jwtContextInterceptor;
  private final PlaygroundQuotaInterceptor playgroundQuotaInterceptor;

  /**
   * Register global interceptors.
   *
   * <p>JwtContextInterceptor is registered for all paths except public endpoints.
   */
  @Override
  public void addInterceptors(@NonNull InterceptorRegistry registry) {
    registry
        .addInterceptor(jwtContextInterceptor)
        .addPathPatterns("/api/**") // Apply to all /api/** paths
        .excludePathPatterns(
            // Public endpoints - no JWT required
            "/api/health",
            "/api/info",
            "/api/public/**",
            "/api/auth/**",
            // Swagger/OpenAPI docs
            "/api-docs/**",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            // Actuator (monitoring)
            "/actuator/**");

    log.info("✅ JwtContextInterceptor registered globally (with public endpoint exclusions)");

    registry
        .addInterceptor(playgroundQuotaInterceptor)
        .addPathPatterns("/api/**")
        .excludePathPatterns("/api/v1/playground/init");

    log.info("✅ PlaygroundQuotaInterceptor registered globally");
  }
}
