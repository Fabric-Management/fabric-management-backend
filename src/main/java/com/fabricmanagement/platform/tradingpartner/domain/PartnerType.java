package com.fabricmanagement.platform.tradingpartner.domain;

/**
 * Trading partner relationship type.
 *
 * <p>Defines the business relationship between a tenant and a trading partner. A single partner can
 * have multiple relationship types with the same tenant (e.g., both supplier and customer). Use
 * {@link #BOTH} for such cases.
 *
 * <h2>Trading Relationships:</h2>
 *
 * <ul>
 *   <li>{@link #SUPPLIER} - Product or service supplier
 *   <li>{@link #CUSTOMER} - Product buyer
 *   <li>{@link #FASON} - Subcontractor (outsourced production)
 *   <li>{@link #SERVICE_PROVIDER} - Service provider (logistics, maintenance, etc.)
 *   <li>{@link #BOTH} - Both supplier and customer
 * </ul>
 *
 * <h2>Group & Affiliate Relationships:</h2>
 *
 * <ul>
 *   <li>{@link #SISTER_COMPANY} - Sibling company under the same parent group
 *   <li>{@link #SUBSIDIARY} - Company owned/controlled by this tenant
 *   <li>{@link #PARENT_COMPANY} - Parent / holding company of this tenant
 * </ul>
 *
 * <h2>BOTH Upgrade:</h2>
 *
 * <p>When the same partner is added with a different type, the existing record is upgraded to BOTH
 * rather than creating a duplicate. The UNIQUE constraint (tenant_id, registry_id) enforces this.
 */
public enum PartnerType {

  // ── Trading relationships ──────────────────────────────────────────────

  /** Product or service supplier */
  SUPPLIER,

  /** Product buyer */
  CUSTOMER,

  /** Subcontractor (fason) - outsourced production */
  FASON,

  /** Service provider (logistics, maintenance, lab, etc.) */
  SERVICE_PROVIDER,

  /** Both supplier and customer - bidirectional relationship */
  BOTH,

  // ── Group & affiliate relationships ───────────────────────────────────

  /**
   * Sister company — sibling under the same parent group / holding.
   *
   * <p>Both companies are separately registered tenants (or external entities) that share a common
   * parent but operate independently.
   */
  SISTER_COMPANY,

  /**
   * Subsidiary — a company owned or controlled by this tenant.
   *
   * <p>Examples: a wholly-owned spinoff, a joint-venture where this tenant holds majority stake.
   */
  SUBSIDIARY,

  /**
   * Parent / holding company — the entity that owns or controls this tenant.
   *
   * <p>There should normally be only one PARENT_COMPANY per tenant.
   */
  PARENT_COMPANY;

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

  /**
   * Check if this type represents a group / affiliate relationship (not a trading relationship).
   *
   * @return true if SISTER_COMPANY, SUBSIDIARY, or PARENT_COMPANY
   */
  public boolean isGroupRelationship() {
    return this == SISTER_COMPANY || this == SUBSIDIARY || this == PARENT_COMPANY;
  }
}
