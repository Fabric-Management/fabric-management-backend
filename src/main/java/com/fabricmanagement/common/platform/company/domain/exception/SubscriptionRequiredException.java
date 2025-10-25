package com.fabricmanagement.common.platform.company.domain.exception;

/**
 * Exception thrown when an operation requires an OS subscription that the tenant doesn't have.
 *
 * <p>This is thrown by Policy Engine Layer 1 when checking OS subscription access.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * // Tenant tries to create a fiber entity without YarnOS subscription
 * throw new SubscriptionRequiredException("YarnOS subscription required to create fiber entities");
 * }</pre>
 *
 * <h2>HTTP Response:</h2>
 * <pre>
 * Status: 402 Payment Required (or 403 Forbidden)
 * {
 *   "error": "SUBSCRIPTION_REQUIRED",
 *   "message": "YarnOS subscription required to create fiber entities",
 *   "requiredOs": "YarnOS",
 *   "upgradeUrl": "/subscriptions/add/YarnOS"
 * }
 * </pre>
 */
public class SubscriptionRequiredException extends RuntimeException {

    private final String requiredOs;

    /**
     * Create exception with a custom message.
     *
     * @param message the error message
     */
    public SubscriptionRequiredException(String message) {
        super(message);
        this.requiredOs = null;
    }

    /**
     * Create exception with required OS code.
     *
     * @param message the error message
     * @param requiredOs the OS code required (e.g., "YarnOS")
     */
    public SubscriptionRequiredException(String message, String requiredOs) {
        super(message);
        this.requiredOs = requiredOs;
    }

    /**
     * Get the OS code that is required.
     *
     * @return the OS code (e.g., "YarnOS"), or null if not specified
     */
    public String getRequiredOs() {
        return requiredOs;
    }
}

