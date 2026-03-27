package com.fabricmanagement.platform.organization.domain;

/**
 * Organization type classification - Internal organizational units only.
 *
 * <p><b>IMPORTANT:</b> This enum only contains types for organizations that can be platform tenants
 * (internal structure). External partners (suppliers, customers, etc.) are now handled by {@link
 * com.fabricmanagement.platform.tradingpartner.domain.PartnerType}.
 *
 * <h2>Architecture:</h2>
 *
 * <pre>
 * OrganizationType (this enum)
 * └── Internal structures: SPINNER, WEAVER, etc.
 *
 * PartnerType (in tradingpartner module - TradingPartner)
 * └── External partners: SUPPLIER, CUSTOMER, SERVICE_PROVIDER, etc.
 * </pre>
 *
 * <h2>Usage:</h2>
 *
 * <ul>
 *   <li>Multi-tenant isolation (tenant companies)
 *   <li>OS subscription recommendations
 *   <li>Access control policies
 *   <li>Reporting and analytics
 * </ul>
 */
public enum OrganizationType {

  // ========================================
  // TEXTILE MANUFACTURING TYPES
  // ========================================

  /** Yarn producer from fiber - SpinnerOS, YarnOS */
  SPINNER,

  /** Fabric producer from yarn (shuttle looms) - WeaverOS, LoomOS */
  WEAVER,

  /** Fabric producer from yarn (knitting machines) - KnitterOS, KnitOS */
  KNITTER,

  /** Dyeing and finishing operations - DyeOS, FinishOS */
  DYER_FINISHER,

  /** Integrated mill (spinning + weaving/knitting + dyeing) - FabricOS */
  VERTICAL_MILL,

  /** Garment/apparel manufacturer - GarmentOS */
  GARMENT_MANUFACTURER,

  // ========================================
  // EXTERNAL PARTNER TYPE
  // ========================================

  /**
   * External trading partner organization.
   *
   * <p>Auto-created when a TradingPartner is registered. Used to link external partner users to the
   * Organization hierarchy without mixing with internal organization types.
   *
   * <p>No OS subscription suggested — external partners use the platform via their own tenant or
   * limited portal access.
   */
  EXTERNAL_PARTNER;

  /**
   * Get suggested OS codes for this organization type.
   *
   * @return array of suggested OS codes
   */
  public String[] getSuggestedOS() {
    return switch (this) {
      case SPINNER -> new String[] {"SpinnerOS", "YarnOS"};
      case WEAVER -> new String[] {"WeaverOS", "LoomOS"};
      case KNITTER -> new String[] {"KnitterOS", "KnitOS"};
      case DYER_FINISHER -> new String[] {"DyeOS", "FinishOS"};
      case VERTICAL_MILL -> new String[] {"FabricOS"};
      case GARMENT_MANUFACTURER -> new String[] {"GarmentOS"};
      case EXTERNAL_PARTNER -> new String[] {};
    };
  }

  /**
   * Get default OS code for this organization type.
   *
   * @return default OS code, or null for EXTERNAL_PARTNER
   */
  public String getDefaultOS() {
    String[] suggested = getSuggestedOS();
    return suggested.length > 0 ? suggested[0] : null;
  }

  /**
   * Check if this type represents an external partner organization.
   *
   * @return true if external partner
   */
  public boolean isExternalPartner() {
    return this == EXTERNAL_PARTNER;
  }

  /**
   * Check if this type represents a vertically integrated operation.
   *
   * @return true if vertical integration
   */
  public boolean isVerticallyIntegrated() {
    return this == VERTICAL_MILL;
  }
}
