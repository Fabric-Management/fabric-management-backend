package com.fabricmanagement.shared.security.context;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Tenant Context Holder
 *
 * Thread-safe tenant context management using ThreadLocal.
 * Follows best practices for multi-tenant applications.
 *
 * @see <a href="https://docs.spring.io/spring-security/reference/features/authentication.html">Spring Security Context</a>
 */
@Slf4j
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();
    private static final ThreadLocal<UUID> CURRENT_USER = new ThreadLocal<>();

    private TenantContext() {
        // Private constructor to prevent instantiation
    }

    /**
     * Sets the current tenant ID
     *
     * @param tenantId the tenant ID to set
     */
    public static void setTenantId(UUID tenantId) {
        if (tenantId == null) {
            log.warn("Attempted to set null tenant ID");
            return;
        }
        CURRENT_TENANT.set(tenantId);
        log.debug("Tenant context set: {}", tenantId);
    }

    /**
     * Gets the current tenant ID
     *
     * @return the current tenant ID
     * @throws IllegalStateException if tenant ID is not set
     */
    public static UUID getTenantId() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Tenant ID not set in context");
        }
        return tenantId;
    }

    /**
     * Gets the current tenant ID or null if not set
     *
     * @return the current tenant ID or null
     */
    public static UUID getTenantIdOrNull() {
        return CURRENT_TENANT.get();
    }

    /**
     * Sets the current user ID
     *
     * @param userId the user ID to set
     */
    public static void setUserId(UUID userId) {
        if (userId == null) {
            log.warn("Attempted to set null user ID");
            return;
        }
        CURRENT_USER.set(userId);
        log.debug("User context set: {}", userId);
    }

    /**
     * Gets the current user ID
     *
     * @return the current user ID
     * @throws IllegalStateException if user ID is not set
     */
    public static UUID getUserId() {
        UUID userId = CURRENT_USER.get();
        if (userId == null) {
            throw new IllegalStateException("User ID not set in context");
        }
        return userId;
    }

    /**
     * Gets the current user ID or null if not set
     *
     * @return the current user ID or null
     */
    public static UUID getUserIdOrNull() {
        return CURRENT_USER.get();
    }

    /**
     * Clears the current tenant and user context
     * IMPORTANT: Must be called after request processing to prevent memory leaks
     */
    public static void clear() {
        CURRENT_TENANT.remove();
        CURRENT_USER.remove();
        log.debug("Tenant context cleared");
    }

    /**
     * Checks if tenant context is set
     *
     * @return true if tenant ID is set
     */
    public static boolean hasTenantId() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * Checks if user context is set
     *
     * @return true if user ID is set
     */
    public static boolean hasUserId() {
        return CURRENT_USER.get() != null;
    }
}
