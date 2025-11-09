package com.fabricmanagement.common.platform.communication.domain;

/**
 * Address type category for grouping and validation.
 *
 * <p>Groups address types into high-level categories for:
 * <ul>
 *   <li>Type-safe validation (personal vs corporate)</li>
 *   <li>UI categorization and filtering</li>
 *   <li>Business logic decisions</li>
 *   <li>Reporting and analytics</li>
 * </ul>
 */
public enum AddressTypeCategory {

    /**
     * Personal address types
     * <p>Used for: Employee personal addresses (HOME, BILLING, MAILING, TEMPORARY, ALTERNATE)</p>
     */
    PERSONAL,

    /**
     * Corporate/operational address types
     * <p>Used for: Company and office addresses (OFFICE, HEADQUARTERS, BRANCH, WAREHOUSE, FACTORY, SHIPPING, BILLING)</p>
     */
    CORPORATE,

    /**
     * Field/work travel address types
     * <p>Used for: On-site and remote work locations (WORKSITE, REMOTE)</p>
     */
    FIELD
}

