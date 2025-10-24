package com.fabricmanagement.common.platform.company.domain;

/**
 * Pricing tier for OS subscriptions.
 *
 * <p>Determines subscription cost and feature availability.
 * Higher tiers unlock more features and higher limits.</p>
 */
public enum PricingTier {

    /**
     * Free tier - Basic features only
     * <p>Limited features, low limits</p>
     */
    FREE,

    /**
     * Basic tier - Entry-level paid
     * <p>Essential features for small operations</p>
     */
    BASIC,

    /**
     * Professional tier - Full features
     * <p>Complete feature set for medium businesses</p>
     */
    PROFESSIONAL,

    /**
     * Enterprise tier - Unlimited
     * <p>All features + premium support + custom limits</p>
     */
    ENTERPRISE
}

