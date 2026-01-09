package com.fabricmanagement.common.platform.company.domain;

/**
 * High-level company categorization.
 *
 * <p>Groups company types into major categories for:
 *
 * <ul>
 *   <li>Simplified filtering and reporting
 *   <li>Policy group assignments
 *   <li>UI categorization
 *   <li>Business analytics
 * </ul>
 */
public enum CompanyCategory {

  /**
   * Platform tenant - Uses platform for their operations
   *
   * <p>Examples: SPINNER, WEAVER, VERTICAL_MILL
   *
   * <p>Has OS subscriptions and user accounts
   */
  TENANT,

  /**
   * Material supplier - Provides raw materials or products
   *
   * <p>Examples: FIBER_SUPPLIER, CHEMICAL_SUPPLIER, MACHINE_SUPPLIER
   *
   * <p>Tracked in procurement and inventory modules
   */
  SUPPLIER,

  /**
   * Service provider - Provides services (not products)
   *
   * <p>Examples: LOGISTICS_PROVIDER, MAINTENANCE_SERVICE, LAB
   *
   * <p>Tracked for service contracts and performance
   */
  SERVICE_PROVIDER,

  /**
   * Business partner - Collaboration or intermediary
   *
   * <p>Examples: FASON, AGENT, TRADER, FINANCE_PARTNER
   *
   * <p>Commercial agreements and revenue sharing
   */
  PARTNER,

  /**
   * Customer - Purchases finished products
   *
   * <p>Final buyer in the supply chain
   */
  CUSTOMER
}
