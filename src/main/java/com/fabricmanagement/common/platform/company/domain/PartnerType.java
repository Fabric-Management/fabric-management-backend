package com.fabricmanagement.common.platform.company.domain;

/**
 * Trading partner relationship type.
 *
 * <p>Defines the business relationship between a tenant and a trading partner. A single partner can
 * have multiple relationship types with the same tenant (e.g., both supplier and customer). Use
 * {@link #BOTH} for such cases.
 *
 * <h2>Usage:</h2>
 *
 * <ul>
 *   <li>{@link #SUPPLIER} - Material or service supplier
 *   <li>{@link #CUSTOMER} - Product buyer
 *   <li>{@link #FASON} - Subcontractor (outsourced production)
 *   <li>{@link #SERVICE_PROVIDER} - Service provider (logistics, maintenance, etc.)
 *   <li>{@link #BOTH} - Both supplier and customer
 * </ul>
 *
 * <h2>BOTH Upgrade:</h2>
 *
 * <p>When the same partner is added with a different type, the existing record is upgraded to BOTH
 * rather than creating a duplicate. The UNIQUE constraint (tenant_id, registry_id) enforces this.
 */
public enum PartnerType {

  /** Material or service supplier */
  SUPPLIER,

  /** Product buyer */
  CUSTOMER,

  /** Subcontractor (fason) - outsourced production */
  FASON,

  /** Service provider (logistics, maintenance, lab, etc.) */
  SERVICE_PROVIDER,

  /** Both supplier and customer - bidirectional relationship */
  BOTH;

  /**
   * Check if this type includes supplier role.
   *
   * @return true if SUPPLIER or BOTH
   */
  public boolean isSupplier() {
    return this == SUPPLIER || this == BOTH;
  }

  /**
   * Check if this type includes customer role.
   *
   * @return true if CUSTOMER or BOTH
   */
  public boolean isCustomer() {
    return this == CUSTOMER || this == BOTH;
  }
}
