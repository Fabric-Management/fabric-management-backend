package com.fabricmanagement.shared.security.filter;

import com.fabricmanagement.shared.application.context.SecurityContext;
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
import java.util.UUID;

/**
 * JWT Authentication Filter for Servlet-based Microservices
 *
 * Validates JWT tokens and sets authentication in Spring Security context.
 * 
 * SPRING SECURITY NATIVE PATTERN:
 * - Creates SecurityContext object with all user info
 * - Sets SecurityContext as Authentication principal
 * - Controllers use @AuthenticationPrincipal SecurityContext to access user info
 * 
 * NO CUSTOM ArgumentResolver needed - Spring Security native @AuthenticationPrincipal!
 *
 * Public endpoints are skipped (configured in SecurityConfig).
 *
 * This filter is automatically applied to all requests through SecurityFilterChain.
 * 
 * Version: 2.0 (Spring Security Native Pattern)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    
    // Header names (set by API Gateway)
    private static final String HEADER_COMPANY_ID = "X-Company-Id";

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
                // SecurityContext object will be available via @AuthenticationPrincipal
                setAuthentication(request, userId, tenantId, role);

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
     * SPRING SECURITY NATIVE PATTERN:
     * - Principal = SecurityContext object (not just userId!)
     * - Controllers use @AuthenticationPrincipal SecurityContext
     * - No custom ArgumentResolver needed!
     * 
     * @param request HTTP request
     * @param userId user ID (UUID as string)
     * @param tenantId tenant ID (UUID as string)
     * @param role user role (e.g., "ADMIN", "SUPER_ADMIN", "USER")
     */
    private void setAuthentication(HttpServletRequest request, String userId, String tenantId, String role) {
        // Extract additional info from headers (set by API Gateway)
        String companyIdHeader = request.getHeader(HEADER_COMPANY_ID);
        UUID companyId = null;
        if (companyIdHeader != null && !companyIdHeader.isEmpty()) {
            try {
                companyId = UUID.fromString(companyIdHeader);
            } catch (IllegalArgumentException e) {
                log.error("Invalid companyId UUID format in header: {}", companyIdHeader);
            }
        }
        
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
        
        // Build SecurityContext object with all user information
        // This will be the Authentication principal!
        SecurityContext securityContext = SecurityContext.builder()
            .userId(userId)
            .tenantId(UUID.fromString(tenantId))
            .roles(new String[]{role != null ? role : "USER"})
            .companyId(companyId)
            .build();

        // SPRING SECURITY NATIVE PATTERN:
        // Set SecurityContext as principal (not just userId!)
        // Now controllers can use @AuthenticationPrincipal SecurityContext
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                    securityContext,  // ← Principal is SecurityContext!
                    null, 
                    authorities
                );

        // Set authentication in Spring Security context
        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Security context set: user={}, tenant={}, company={}, role={}", 
                 userId, tenantId, companyId, role);
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
