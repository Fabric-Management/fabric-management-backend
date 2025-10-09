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
                String userId = jwtTokenProvider.extractUserId(token);
                String tenantId = jwtTokenProvider.extractTenantId(token);
                String role = jwtTokenProvider.extractRole(token);

                // Validate presence
                if (userId == null || tenantId == null) {
                    log.warn("Invalid JWT claims: missing userId or tenantId");
                    filterChain.doFilter(request, response);
                    return;
                }

                // SECURITY: Validate UUID format (Defense in Depth - Second Line)
                if (!isValidUuid(userId)) {
                    log.error("Invalid userId UUID format in JWT: {}", userId);
                    filterChain.doFilter(request, response);
                    return;
                }
                
                if (!isValidUuid(tenantId)) {
                    log.error("Invalid tenantId UUID format in JWT: {}", tenantId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // Set authentication in Security Context
                setAuthentication(request, userId, tenantId, role);

                // Add tenant and user info to request attributes for downstream use
                request.setAttribute(TENANT_ID_ATTRIBUTE, tenantId);
                request.setAttribute(USER_ID_ATTRIBUTE, userId);

                log.debug("JWT authenticated: user={}, tenant={}, path={}",
                        userId, tenantId, request.getRequestURI());
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
     * 
     * @param request HTTP request
     * @param userId user ID (UUID as string)
     * @param tenantId tenant ID (UUID as string)
     * @param role user role (e.g., "ADMIN", "SUPER_ADMIN", "USER")
     */
    private void setAuthentication(HttpServletRequest request, String userId, String tenantId, String role) {
        // Extract role from JWT and create authorities
        // Spring Security expects "ROLE_" prefix
        List<SimpleGrantedAuthority> authorities;
        
        if (role != null && !role.isEmpty()) {
            String authorityName = role.startsWith("ROLE_") ? role : "ROLE_" + role;
            authorities = Collections.singletonList(new SimpleGrantedAuthority(authorityName));
            log.debug("JWT role extracted: {} → authority: {}", role, authorityName);
        } else {
            // Fallback to ROLE_USER if no role in JWT
            authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
            log.warn("No role in JWT for user: {}, defaulting to ROLE_USER", userId);
        }

        // Create authentication token
        // Principal = userId (UUID as string)
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        // Set tenantId as details (SecurityContextHolder expects tenantId here)
        authentication.setDetails(tenantId);

        // Set authentication in context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.trace("Security context set for user: {}, tenant: {}, authorities: {}", 
                 userId, tenantId, authorities);
    }

    /**
     * Validates UUID format
     * 
     * SECURITY: Defense in Depth - validates UUID format before setting authentication
     * Prevents malformed UUIDs from entering security context
     * 
     * @param uuid string to validate
     * @return true if valid UUID format
     */
    private boolean isValidUuid(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            return false;
        }
        
        try {
            java.util.UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
