package com.fabricmanagement.platform.communication.domain;

/**
 * Address type category for grouping and validation.
 *
 * <p>Groups address types into high-level categories for:
 *
 * <ul>
 *   <li>Type-safe validation (personal vs corporate)
 *   <li>UI categorization and filtering
 *   <li>Business logic decisions
 *   <li>Reporting and analytics
 * </ul>
 */
public enum AddressTypeCategory {

  /**
   * Personal address types
   *
   * <p>Used for: Employee personal addresses (HOME, BILLING, MAILING, TEMPORARY, ALTERNATE)
   */
  PERSONAL,

  /**
   * Corporate/operational address types
   *
   * <p>Used for: Company and office addresses (OFFICE, HEADQUARTERS, BRANCH, WAREHOUSE, FACTORY,
   * SHIPPING, BILLING)
   */
  CORPORATE,

  /**
   * Field/work travel address types
   *
   * <p>Used for: On-site and remote work locations (WORKSITE, REMOTE)
   */
  FIELD
}
