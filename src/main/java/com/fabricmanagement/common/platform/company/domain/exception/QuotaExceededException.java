package com.fabricmanagement.common.platform.company.domain.exception;

/**
 * Exception thrown when a usage quota has been exceeded.
 *
 * <p>This is thrown by Policy Engine Layer 3 when checking usage quotas.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * // Tenant has exceeded their API call limit for the month
 * throw new QuotaExceededException(
 *     "API call limit exceeded. 100,000/100,000 used.",
 *     "api_calls",
 *     100_000L,
 *     100_000L
 * );
 * }</pre>
 *
 * <h2>HTTP Response:</h2>
 * <pre>
 * Status: 429 Too Many Requests
 * {
 *   "error": "QUOTA_EXCEEDED",
 *   "message": "API call limit exceeded. 100,000/100,000 used.",
 *   "quotaType": "api_calls",
 *   "limit": 100000,
 *   "used": 100000,
 *   "resetAt": "2025-11-01T00:00:00Z",
 *   "upgradeUrl": "/subscriptions/add-ons"
 * }
 * </pre>
 */
public class QuotaExceededException extends RuntimeException {

    private final String quotaType;
    private final Long limit;
    private final Long used;

    /**
     * Create exception with a custom message.
     *
     * @param message the error message
     */
    public QuotaExceededException(String message) {
        super(message);
        this.quotaType = null;
        this.limit = null;
        this.used = null;
    }

    /**
     * Create exception with quota details.
     *
     * @param message the error message
     * @param quotaType the type of quota exceeded (e.g., "api_calls")
     * @param limit the quota limit
     * @param used the current usage
     */
    public QuotaExceededException(String message, String quotaType, Long limit, Long used) {
        super(message);
        this.quotaType = quotaType;
        this.limit = limit;
        this.used = used;
    }

    /**
     * Get the quota type that was exceeded.
     *
     * @return the quota type (e.g., "api_calls"), or null if not specified
     */
    public String getQuotaType() {
        return quotaType;
    }

    /**
     * Get the quota limit.
     *
     * @return the limit value, or null if not specified
     */
    public Long getLimit() {
        return limit;
    }

    /**
     * Get the current usage.
     *
     * @return the used value, or null if not specified
     */
    public Long getUsed() {
        return used;
    }
}

