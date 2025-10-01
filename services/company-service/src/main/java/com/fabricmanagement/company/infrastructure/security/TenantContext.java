package com.fabricmanagement.company.infrastructure.security;

import java.util.UUID;

/**
 * Tenant Context
 * 
 * Thread-local storage for current tenant ID
 */
public class TenantContext {
    
    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    
    /**
     * Sets the current tenant ID
     */
    public static void setCurrentTenant(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }
    
    /**
     * Gets the current tenant ID
     */
    public static UUID getCurrentTenant() {
        return CURRENT_TENANT.get();
    }
    
    /**
     * Clears the current tenant ID
     */
    public static void clear() {
        CURRENT_TENANT.remove();
    }
}

