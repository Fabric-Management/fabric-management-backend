package com.fabricmanagement.common.platform.company.domain;

/**
 * Operating Subscription type classification.
 *
 * <p>Different OS types provide different feature sets and capabilities.
 * Used for subscription packaging and feature gating.</p>
 */
public enum OSType {

    /**
     * Base OS - Foundational features only
     * <p>Minimal feature set, lowest price</p>
     */
    BASE,

    /**
     * Lite OS - Essential features
     * <p>Core features for small operations</p>
     */
    LITE,

    /**
     * Full OS - Complete feature set
     * <p>All standard features included</p>
     */
    FULL,

    /**
     * Premium OS - Advanced features + AI
     * <p>Full features + AI/ML capabilities + premium support</p>
     */
    PREMIUM
}

