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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;

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
 * NOTE: Only applies to servlet-based services (user, company, contact).
 * Reactive services (api-gateway) use WebFlux security filters.
 * 
 * @author Fabric Management Team
 * @since 1.0 (Refactored to annotation-based v3.2.0 - Oct 13, 2025)
 */
@Slf4j
@Component
@Order(0) // Before JwtAuthenticationFilter (Order 1)
@RequiredArgsConstructor
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InternalAuthenticationFilter implements Filter {
    
    private final InternalEndpointRegistry endpointRegistry;
    
    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        log.trace("🔍 [InternalAuth] Processing: {} {}", method, path);

        // Check if this is an internal endpoint (annotation or config)
        if (endpointRegistry.isInternalEndpoint(path, method)) {
            log.debug("🔒 [InternalAuth] Internal endpoint detected: {} {}", method, path);

            String providedKey = httpRequest.getHeader(InternalApiConstants.INTERNAL_API_KEY_HEADER);

            // ✅ DUAL MODE support: If internal key is provided, validate it
            if (providedKey != null && !providedKey.isEmpty()) {
                // Key provided - MUST be valid
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
                // ✅ DUAL MODE: No internal key → Fall through to JWT authentication
                // This allows endpoints to be called with either internal key OR JWT
                log.debug("➡️ [InternalAuth] No internal key provided, falling through to JWT auth: {} {}", method, path);
            }
        } else {
            log.trace("➡️ [InternalAuth] Not internal, continue to JWT auth: {} {}", method, path);
        }

        chain.doFilter(request, response);
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

