package com.fabricmanagement.company.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
        
        // Try to get tenant ID from authentication
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            // In a real implementation, tenant ID would be extracted from JWT claims
            // For now, we'll use a default tenant ID
            UUID defaultTenantId = UUID.randomUUID();
            TenantContext.setCurrentTenant(defaultTenantId);
            log.debug("Using default tenant ID: {}", defaultTenantId);
        }
        
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, 
                                Object handler, Exception ex) {
        // Clear tenant context after request
        TenantContext.clear();
    }
}

