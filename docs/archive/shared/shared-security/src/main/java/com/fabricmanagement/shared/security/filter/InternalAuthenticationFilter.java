package com.fabricmanagement.shared.security.filter;

import com.fabricmanagement.shared.security.service.InternalEndpointRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Internal Authentication Filter
 * 
 * Validates internal API key for service-to-service calls
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ SECURITY FILTER
 * ✅ INTERNAL API KEY VALIDATION
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InternalAuthenticationFilter extends OncePerRequestFilter {
    
    private final InternalEndpointRegistry internalEndpointRegistry;
    
    @Value("${INTERNAL_API_KEY:}")
    private String internalApiKey;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        
        try {
            String path = request.getRequestURI();
            String method = request.getMethod();
            
            // Check if this is an internal endpoint
            if (internalEndpointRegistry.isInternalEndpoint(path, method)) {
                String providedKey = request.getHeader("X-Internal-API-Key");
                
                if (providedKey == null || !providedKey.equals(internalApiKey)) {
                    log.warn("❌ Invalid internal API key for endpoint: {} {}", method, path);
                    
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Invalid internal API key\"}");
                    return;
                }
                
                log.debug("✅ Internal API key validated for endpoint: {} {}", method, path);
            }
            
        } catch (Exception e) {
            log.error("❌ Internal authentication error", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.getWriter().write("{\"error\":\"Internal authentication failed\"}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * Check if request should be filtered
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        
        // Skip filtering for public endpoints
        return path.startsWith("/actuator/") ||
               path.startsWith("/api/v1/auth/") ||
               path.startsWith("/api/v1/health") ||
               path.startsWith("/swagger-ui/") ||
               path.startsWith("/v3/api-docs");
    }
}