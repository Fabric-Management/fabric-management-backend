package com.fabricmanagement.shared.security.filter;

import com.fabricmanagement.shared.infrastructure.constants.InternalApiConstants;
import com.fabricmanagement.shared.infrastructure.constants.SecurityConstants;
import com.fabricmanagement.shared.security.service.InternalEndpointRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Internal Authentication Filter
 * 
 * Modern, annotation-based internal endpoint authentication.
 * 
 * Features:
 * ✅ Auto-discovery via @InternalEndpoint annotation
 * ✅ Configuration-based fallback (application.yml)
 * ✅ Zero hardcoded paths
 * ✅ O(1) lookup performance
 * ✅ Self-documenting
 * 
 * Security Pattern:
 * - Endpoints with @InternalEndpoint → Require X-Internal-API-Key
 * - Endpoints in configuration → Require X-Internal-API-Key
 * - Other endpoints → Continue to JWT authentication
 * 
 * @author Fabric Management Team
 * @since 1.0 (Refactored to annotation-based v3.2.0 - Oct 13, 2025)
 */
@Slf4j
@Component
@Order(0) // Before JwtAuthenticationFilter (Order 1)
@RequiredArgsConstructor
public class InternalAuthenticationFilter implements Filter {
    
    private final InternalEndpointRegistry endpointRegistry;
    
    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;
    
    // Public paths that don't need authentication at all
    private static final List<String> PUBLIC_PATHS = List.of(
        "/actuator",
        "/api/public",
        "/api/v1/public",
        "/api/v1/users/auth/login",
        "/api/v1/users/auth/check-contact",
        "/api/v1/users/auth/setup-password"
    );
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        log.trace("🔍 [InternalAuth] Processing: {} {}", method, path);

        // Skip public paths
        if (isPublicPath(path)) {
            log.trace("✅ [InternalAuth] Public path, skipping: {}", path);
            chain.doFilter(request, response);
            return;
        }

        // Check if this is an internal endpoint (annotation or config)
        if (endpointRegistry.isInternalEndpoint(path, method)) {
            log.debug("🔒 [InternalAuth] Internal endpoint detected: {} {}", method, path);

            String providedKey = httpRequest.getHeader(InternalApiConstants.INTERNAL_API_KEY_HEADER);

            if (providedKey == null || providedKey.isEmpty()) {
                log.warn("⚠️ [InternalAuth] Missing API key: {} {}", method, path);
                sendUnauthorizedResponse(httpResponse, InternalApiConstants.MSG_MISSING_INTERNAL_KEY);
                return;
            }

            if (!providedKey.equals(internalApiKey)) {
                log.error("🚨 [InternalAuth] Invalid API key: {} {}", method, path);
                sendUnauthorizedResponse(httpResponse, InternalApiConstants.MSG_INVALID_INTERNAL_KEY);
                return;
            }

            log.info("✅ [InternalAuth] API key validated: {} {}", method, path);

            // Create internal service security context
            com.fabricmanagement.shared.application.context.SecurityContext customSecurityContext =
                com.fabricmanagement.shared.application.context.SecurityContext.builder()
                    .userId(SecurityConstants.INTERNAL_SERVICE_PRINCIPAL)
                    .tenantId(null)
                    .roles(new String[]{SecurityConstants.ROLE_INTERNAL_SERVICE})
                    .build();

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                customSecurityContext,
                null,
                Collections.singletonList(new SimpleGrantedAuthority(SecurityConstants.ROLE_INTERNAL_SERVICE))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);

            log.debug("🔓 [InternalAuth] Internal service authentication set");
        } else {
            log.trace("➡️ [InternalAuth] Not internal, continue to JWT auth: {} {}", method, path);
        }

        chain.doFilter(request, response);
    }
    
    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
    }
    
    private void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(String.format(
            "{\"success\":false,\"message\":\"%s\",\"errorCode\":\"UNAUTHORIZED\"}",
            message
        ));
    }
}

