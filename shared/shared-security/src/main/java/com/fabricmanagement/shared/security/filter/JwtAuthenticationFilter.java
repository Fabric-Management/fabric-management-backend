package com.fabricmanagement.shared.security.filter;

import com.fabricmanagement.shared.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT Authentication Filter for Servlet-based Microservices
 *
 * Validates JWT tokens and sets authentication in Spring Security context.
 * Extracts tenant ID and user ID from JWT and adds them to request attributes.
 *
 * Public endpoints are skipped (configured in SecurityConfig).
 *
 * This filter is automatically applied to all requests through SecurityFilterChain.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String TENANT_ID_ATTRIBUTE = "X-Tenant-Id";
    private static final String USER_ID_ATTRIBUTE = "X-User-Id";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String token = extractTokenFromRequest(request);

            if (token != null && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.extractUsername(token);
                String tenantId = jwtTokenProvider.extractTenantId(token);

                if (username != null && tenantId != null) {
                    // Set authentication in Security Context
                    setAuthentication(request, username, tenantId);

                    // Add tenant and user info to request attributes for downstream use
                    request.setAttribute(TENANT_ID_ATTRIBUTE, tenantId);
                    request.setAttribute(USER_ID_ATTRIBUTE, username);

                    log.debug("JWT authenticated: user={}, tenant={}, path={}",
                            username, tenantId, request.getRequestURI());
                } else {
                    log.warn("Invalid JWT claims: missing username or tenantId");
                }
            }

        } catch (Exception e) {
            log.error("JWT authentication failed: {}", e.getMessage());
            // Continue filter chain - let Security Config handle authorization
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract JWT token from Authorization header
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }

    /**
     * Set authentication in Spring Security context
     */
    private void setAuthentication(HttpServletRequest request, String username, String tenantId) {
        // Create authorities (can be extended with roles from JWT claims)
        List<SimpleGrantedAuthority> authorities = Collections.singletonList(
                new SimpleGrantedAuthority("ROLE_USER")
        );

        // Create authentication token
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(username, null, authorities);

        // Set request details
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Set authentication in context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.trace("Security context set for user: {}, tenant: {}", username, tenantId);
    }
}
