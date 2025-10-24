package com.fabricmanagement.common.infrastructure.persistence;

import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

/**
 * Thread-local context for managing current tenant information.
 *
 * <p>Provides a thread-safe way to store and retrieve the current tenant ID
 * throughout the request lifecycle. This is used for:
 * <ul>
 *   <li>Automatic tenant_id injection in {@link BaseEntity}</li>
 *   <li>Row-Level Security (RLS) filtering in PostgreSQL</li>
 *   <li>Multi-tenant data isolation</li>
 *   <li>Audit logging with tenant context</li>
 * </ul>
 *
 * <h2>Usage Flow:</h2>
 * <ol>
 *   <li>Request arrives with JWT token</li>
 *   <li>TenantFilter extracts tenant_id from JWT</li>
 *   <li>TenantContext.setCurrentTenantId(tenantId) is called</li>
 *   <li>All subsequent operations use this tenant context</li>
 *   <li>TenantContext.clear() is called at request end</li>
 * </ol>
 *
 * <h2>Example Usage:</h2>
 * <pre>{@code
 * // In filter or interceptor
 * UUID tenantId = extractTenantFromJwt(token);
 * TenantContext.setCurrentTenantId(tenantId);
 * TenantContext.setCurrentTenantUid("ACME-001");
 *
 * try {
 *     // Process request - all entities will use this tenant
 *     Material material = materialService.create(request);
 *     // material.getTenantId() == tenantId (automatically set)
 * } finally {
 *     TenantContext.clear();  // Always clear in finally block
 * }
 * }</pre>
 *
 * <h2>Thread Safety:</h2>
 * <p>Uses {@link ThreadLocal} to ensure each thread has its own isolated
 * tenant context. This is safe in multi-threaded servlet containers.</p>
 *
 * @see BaseEntity
 */
@Slf4j
public final class TenantContext {

    /**
     * Default tenant ID for system operations (no specific tenant)
     */
    public static final UUID SYSTEM_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    /**
     * ThreadLocal storage for current tenant ID
     */
    private static final ThreadLocal<UUID> CURRENT_TENANT_ID = new InheritableThreadLocal<>();

    /**
     * ThreadLocal storage for current tenant UID (human-readable)
     */
    private static final ThreadLocal<String> CURRENT_TENANT_UID = new InheritableThreadLocal<>();

    /**
     * ThreadLocal storage for current user ID
     */
    private static final ThreadLocal<UUID> CURRENT_USER_ID = new InheritableThreadLocal<>();

    /**
     * Private constructor to prevent instantiation
     */
    private TenantContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Sets the current tenant ID for this thread
     *
     * @param tenantId the tenant ID to set
     * @throws IllegalArgumentException if tenantId is null
     */
    public static void setCurrentTenantId(UUID tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("Tenant ID cannot be null");
        }
        CURRENT_TENANT_ID.set(tenantId);
        log.trace("Set tenant ID: {} for thread: {}", tenantId, Thread.currentThread().getName());
    }

    /**
     * Sets the current tenant UID (human-readable identifier)
     *
     * @param tenantUid the tenant UID to set
     */
    public static void setCurrentTenantUid(String tenantUid) {
        CURRENT_TENANT_UID.set(tenantUid);
        log.trace("Set tenant UID: {} for thread: {}", tenantUid, Thread.currentThread().getName());
    }

    /**
     * Sets the current user ID for this thread
     *
     * @param userId the user ID to set
     */
    public static void setCurrentUserId(UUID userId) {
        CURRENT_USER_ID.set(userId);
        log.trace("Set user ID: {} for thread: {}", userId, Thread.currentThread().getName());
    }

    /**
     * Gets the current tenant ID for this thread
     *
     * @return the current tenant ID, or SYSTEM_TENANT_ID if not set
     */
    public static UUID getCurrentTenantId() {
        UUID tenantId = CURRENT_TENANT_ID.get();
        if (tenantId == null) {
            log.warn("No tenant ID set for thread: {}, returning SYSTEM_TENANT_ID", Thread.currentThread().getName());
            return SYSTEM_TENANT_ID;
        }
        return tenantId;
    }

    /**
     * Gets the current tenant ID for this thread, or null if not set
     *
     * @return the current tenant ID, or null if not set
     */
    public static UUID getCurrentTenantIdOrNull() {
        return CURRENT_TENANT_ID.get();
    }

    /**
     * Gets the current tenant UID for this thread
     *
     * @return the current tenant UID, or null if not set
     */
    public static String getCurrentTenantUid() {
        return CURRENT_TENANT_UID.get();
    }

    /**
     * Gets the current user ID for this thread
     *
     * @return the current user ID, or null if not set
     */
    public static UUID getCurrentUserId() {
        return CURRENT_USER_ID.get();
    }

    /**
     * Checks if a tenant context is set for this thread
     *
     * @return true if tenant context is set, false otherwise
     */
    public static boolean isSet() {
        return CURRENT_TENANT_ID.get() != null;
    }

    /**
     * Checks if the current tenant is the system tenant
     *
     * @return true if current tenant is system tenant
     */
    public static boolean isSystemTenant() {
        UUID tenantId = CURRENT_TENANT_ID.get();
        return tenantId != null && SYSTEM_TENANT_ID.equals(tenantId);
    }

    /**
     * Clears the tenant context for this thread
     * <p><b>IMPORTANT:</b> Always call this in a finally block to prevent context leakage!</p>
     */
    public static void clear() {
        UUID tenantId = CURRENT_TENANT_ID.get();
        if (tenantId != null) {
            log.trace("Clearing tenant context for thread: {}, tenant: {}", Thread.currentThread().getName(), tenantId);
        }
        CURRENT_TENANT_ID.remove();
        CURRENT_TENANT_UID.remove();
        CURRENT_USER_ID.remove();
    }

    /**
     * Executes a runnable with a specific tenant context
     *
     * @param tenantId the tenant ID to use
     * @param runnable the code to execute
     */
    public static void executeInTenantContext(UUID tenantId, Runnable runnable) {
        UUID previousTenantId = getCurrentTenantIdOrNull();
        try {
            setCurrentTenantId(tenantId);
            runnable.run();
        } finally {
            if (previousTenantId != null) {
                setCurrentTenantId(previousTenantId);
            } else {
                clear();
            }
        }
    }

    /**
     * Executes a supplier with a specific tenant context
     *
     * @param tenantId the tenant ID to use
     * @param supplier the code to execute
     * @param <T> the return type
     * @return the result from the supplier
     */
    public static <T> T executeInTenantContext(UUID tenantId, java.util.function.Supplier<T> supplier) {
        UUID previousTenantId = getCurrentTenantIdOrNull();
        try {
            setCurrentTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenantId != null) {
                setCurrentTenantId(previousTenantId);
            } else {
                clear();
            }
        }
    }
}

