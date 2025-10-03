package com.fabricmanagement.company.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Tenant Interceptor
 * 
 * Extracts tenant ID from request and sets it in TenantContext
 */
@Component
@Slf4j
public class TenantInterceptor implements HandlerInterceptor {
    
    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                            @NonNull Object handler) {
        // Try to get tenant ID from header
        String tenantIdHeader = request.getHeader("X-Tenant-ID");
        
        if (tenantIdHeader != null && !tenantIdHeader.isEmpty()) {
            try {
                UUID tenantId = UUID.fromString(tenantIdHeader);
                TenantContext.setCurrentTenant(tenantId);
                log.debug("Tenant ID set from header: {}", tenantId);
                return true;
            } catch (IllegalArgumentException e) {
                log.warn("Invalid tenant ID in header: {}", tenantIdHeader);
            }
        }
        
        // If no tenant ID in header, request should be rejected by authentication layer
        // Gateway should always provide X-Tenant-ID header
        log.warn("No tenant ID found in request headers for: {}", request.getRequestURI());
        return true;
    }
    
    @Override
    public void afterCompletion(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                @NonNull Object handler, @Nullable Exception ex) {
        // Clear tenant context after request
        TenantContext.clear();
    }
}

