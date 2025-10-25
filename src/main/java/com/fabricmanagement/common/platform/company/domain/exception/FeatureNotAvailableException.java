package com.fabricmanagement.common.platform.company.domain.exception;

/**
 * Exception thrown when a feature is not available in the tenant's subscription tier.
 *
 * <p>This is thrown by Policy Engine Layer 2 when checking feature entitlements.</p>
 *
 * <h2>Example:</h2>
 * <pre>{@code
 * // Tenant with YarnOS Starter tries to use blend management (Professional+ feature)
 * throw new FeatureNotAvailableException(
 *     "Blend management is only available in Professional and Enterprise tiers",
 *     "yarn.blend.management",
 *     "PROFESSIONAL"
 * );
 * }</pre>
 *
 * <h2>HTTP Response:</h2>
 * <pre>
 * Status: 402 Payment Required (or 403 Forbidden)
 * {
 *   "error": "FEATURE_NOT_AVAILABLE",
 *   "message": "Blend management is only available in Professional and Enterprise tiers",
 *   "featureId": "yarn.blend.management",
 *   "minimumTier": "PROFESSIONAL",
 *   "upgradeUrl": "/subscriptions/upgrade/YarnOS/PROFESSIONAL"
 * }
 * </pre>
 */
public class FeatureNotAvailableException extends RuntimeException {

    private final String featureId;
    private final String minimumTier;

    /**
     * Create exception with a custom message.
     *
     * @param message the error message
     */
    public FeatureNotAvailableException(String message) {
        super(message);
        this.featureId = null;
        this.minimumTier = null;
    }

    /**
     * Create exception with feature details.
     *
     * @param message the error message
     * @param featureId the feature ID that is not available
     * @param minimumTier the minimum tier required to access this feature
     */
    public FeatureNotAvailableException(String message, String featureId, String minimumTier) {
        super(message);
        this.featureId = featureId;
        this.minimumTier = minimumTier;
    }

    /**
     * Get the feature ID that is not available.
     *
     * @return the feature ID (e.g., "yarn.blend.management"), or null if not specified
     */
    public String getFeatureId() {
        return featureId;
    }

    /**
     * Get the minimum tier required to access this feature.
     *
     * @return the tier name (e.g., "PROFESSIONAL"), or null if not specified
     */
    public String getMinimumTier() {
        return minimumTier;
    }
}

