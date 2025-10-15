package com.fabricmanagement.shared.infrastructure.config;

import com.fabricmanagement.shared.infrastructure.constants.InternalApiConstants;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Base Feign Client Configuration - SHARED
 * 
 * Standard configuration for ALL microservices.
 * Provides secure inter-service communication.
 * 
 * Features:
 * 1. Internal API Key authentication (service-to-service)
 * 2. JWT token propagation (user context)
 * 3. Correlation ID propagation (distributed tracing)
 * 
 * Security Pattern:
 * - ALL inter-service calls must include X-Internal-API-Key
 * - JWT is propagated if available (for user context)
 * - Graceful handling if request context not available
 * 
 * Usage:
 * Services can extend this or import directly:
 * 
 * Option 1 (Direct Import):
 *   @FeignClient(configuration = BaseFeignClientConfig.class)
 * 
 * Option 2 (Service-Specific Extension):
 *   @Configuration
 *   public class UserServiceFeignConfig extends BaseFeignClientConfig {
 *       // Add service-specific interceptors if needed
 *   }
 * 
 * @author Fabric Management Team
 * @since 3.0 (Clean Architecture Refactor)
 */
@Configuration
@Slf4j
public class BaseFeignClientConfig {
    
    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;

    /**
     * Internal API Key + JWT Propagation Interceptor
     * 
     * SECURITY:
     * 1. Adds X-Internal-API-Key to ALL inter-service requests (REQUIRED)
     * 2. Propagates JWT if available (OPTIONAL - for user context)
     * 3. Propagates correlation ID for distributed tracing
     * 
     * This ensures:
     * - Secure service-to-service communication
     * - User context preservation across services
     * - Request tracing across microservices
     * 
     * Pattern: @Lazy to prevent circular dependencies during FeignClient initialization
     */
    @Bean
    @Lazy  // ✅ Lazy bean to break circular dependency cycles
    public RequestInterceptor internalAuthenticationInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                try {
                    // 1. ADD INTERNAL API KEY (REQUIRED for all inter-service calls)
                    if (internalApiKey != null && !internalApiKey.isEmpty()) {
                        template.header(InternalApiConstants.INTERNAL_API_KEY_HEADER, internalApiKey);
                        log.trace("Added Internal-API-Key to request: {}", template.url());
                    } else {
                        log.warn("INTERNAL_API_KEY not configured! Inter-service calls may fail.");
                    }
                    
                    // Get request context
                    ServletRequestAttributes attributes =
                        (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                    if (attributes != null) {
                        HttpServletRequest request = attributes.getRequest();
                        
                        // 2. PROPAGATE JWT (OPTIONAL - for user context)
                        String authHeader = request.getHeader("Authorization");
                        if (authHeader != null && authHeader.startsWith("Bearer ")) {
                            template.header("Authorization", authHeader);
                            log.trace("Propagated JWT token to: {}", template.url());
                        }
                        
                        // 3. PROPAGATE CORRELATION ID (for distributed tracing)
                        String correlationId = request.getHeader("X-Correlation-ID");
                        if (correlationId != null) {
                            template.header("X-Correlation-ID", correlationId);
                        }
                        
                        // 4. PROPAGATE REQUEST ID (for request tracking)
                        String requestId = request.getHeader("X-Request-ID");
                        if (requestId != null) {
                            template.header("X-Request-ID", requestId);
                        }
                    } else {
                        log.debug("No request context available (async/scheduled job). " +
                            "Only Internal-API-Key will be sent.");
                    }
                } catch (Exception e) {
                    // Gracefully handle if request context is not available
                    // Internal API key will still be sent
                    log.debug("Error in Feign interceptor (gracefully handled): {}", e.getMessage());
                }
            }
        };
    }
}

