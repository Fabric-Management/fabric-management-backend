package com.fabricmanagement.common.platform.communication.domain;

/**
 * Address type enumeration for different address categories.
 *
 * <p>Used to categorize addresses for both User and Company entities. Supports personal addresses
 * (HOME, BILLING, MAILING), business addresses (OFFICE, HEADQUARTERS), operational addresses
 * (WAREHOUSE, FACTORY, BRANCH), and field addresses (WORKSITE, REMOTE).
 *
 * <h2>Personal Address Types:</h2>
 *
 * <ul>
 *   <li><b>HOME:</b> Employee's personal residential address
 *   <li><b>BILLING:</b> Billing address for invoices or official document delivery
 *   <li><b>MAILING:</b> Alternative address for mail or communication
 *   <li><b>TEMPORARY:</b> Temporary accommodation or project-duration address
 *   <li><b>ALTERNATE:</b> Second residential address (e.g., out-of-town home)
 * </ul>
 *
 * <h2>Corporate/Operational Address Types:</h2>
 *
 * <ul>
 *   <li><b>OFFICE:</b> Office building where user works
 *   <li><b>WORK:</b> User's work/office address (legacy, use OFFICE for new addresses)
 *   <li><b>HEADQUARTERS:</b> Company's main headquarters location
 *   <li><b>BRANCH:</b> Company branch office location
 *   <li><b>WAREHOUSE:</b> Warehouse, production or storage facility
 *   <li><b>FACTORY:</b> Production facility address
 *   <li><b>SHIPPING:</b> Shipping/delivery address
 *   <li><b>BILLING:</b> Billing/invoice address (also used for companies)
 * </ul>
 *
 * <h2>Field/Work Travel Address Types:</h2>
 *
 * <ul>
 *   <li><b>WORKSITE:</b> Field work location address (e.g., construction site, project area)
 *   <li><b>REMOTE:</b> Remote work location (e.g., home-office outside city)
 * </ul>
 */
public enum AddressType {

  // ========== Personal Address Types ==========

  /**
   * Home address
   *
   * <p>Employee's personal residential address
   */
  HOME,

  /**
   * Billing address
   *
   * <p>Address for billing/invoicing or official document delivery
   *
   * <p>Used for: Invoice generation, tax documentation, official correspondence
   *
   * <p>Can be used for both personal and company addresses
   */
  BILLING,

  /**
   * Mailing address
   *
   * <p>Alternative address for mail or communication purposes
   *
   * <p>Used for: Personal mail delivery, alternative contact location
   */
  MAILING,

  /**
   * Temporary address
   *
   * <p>Temporary accommodation or project-duration address
   *
   * <p>Used for: Short-term assignments, project-based locations
   */
  TEMPORARY,

  /**
   * Alternate address
   *
   * <p>Second residential address (e.g., out-of-town home, vacation property)
   *
   * <p>Used for: Secondary residence, alternate living location
   */
  ALTERNATE,

  // ========== Corporate/Operational Address Types ==========

  /**
   * Office address
   *
   * <p>Office building where user works
   *
   * <p>Used for: Employee office location tracking, corporate office addresses
   */
  OFFICE,

  /**
   * Work address (Legacy)
   *
   * <p>User's work/office address (independent from company's address)
   *
   * <p>Used for: Employee office location tracking
   *
   * <p><b>Note:</b> For new addresses, prefer {@link #OFFICE} instead
   *
   * @deprecated Use {@link #OFFICE} for new addresses. Kept for backward compatibility.
   */
  @Deprecated
  WORK,

  /**
   * Company headquarters
   *
   * <p>Company's main headquarters location
   *
   * <p>Used for: Legal address, main office
   */
  HEADQUARTERS,

  /**
   * Branch office
   *
   * <p>Company's branch office location
   *
   * <p>Used for: Regional offices, subsidiaries
   */
  BRANCH,

  /**
   * Warehouse/storage facility
   *
   * <p>Company's warehouse or storage location
   *
   * <p>Used for: Inventory management, logistics
   */
  WAREHOUSE,

  /**
   * Factory address
   *
   * <p>Production facility address
   *
   * <p>Used for: Manufacturing locations, production sites
   */
  FACTORY,

  /**
   * Shipping address
   *
   * <p>Address for shipping/delivery purposes
   *
   * <p>Used for: Order delivery, package shipping
   */
  SHIPPING,

  // ========== Field/Work Travel Address Types ==========

  /**
   * Worksite address
   *
   * <p>Field work location address (e.g., construction site, project area)
   *
   * <p>Used for: On-site work locations, field assignments
   */
  WORKSITE,

  /**
   * Remote location
   *
   * <p>Remote work location (e.g., home-office outside city)
   *
   * <p>Used for: Remote work tracking, distributed team locations
   */
  REMOTE;

  // ========== Helper Methods ==========

  /**
   * Check if this is a personal address type.
   *
   * @return true if personal address type
   */
  public boolean isPersonal() {
    return switch (this) {
      case HOME, BILLING, MAILING, TEMPORARY, ALTERNATE -> true;
      default -> false;
    };
  }

  /**
   * Check if this is a corporate/operational address type.
   *
   * @return true if corporate address type
   */
  public boolean isCorporate() {
    return switch (this) {
      case OFFICE, WORK, HEADQUARTERS, BRANCH, WAREHOUSE, FACTORY, SHIPPING -> true;
      case BILLING -> true; // BILLING can be both personal and corporate
      default -> false;
    };
  }

  /**
   * Check if this is a field/work travel address type.
   *
   * @return true if field address type
   */
  public boolean isField() {
    return switch (this) {
      case WORKSITE, REMOTE -> true;
      default -> false;
    };
  }

  /**
   * Get address type category for grouping.
   *
   * @return address type category
   */
  public AddressTypeCategory getCategory() {
    if (isField()) {
      return AddressTypeCategory.FIELD;
    }
    if (isCorporate() && !isPersonal()) {
      return AddressTypeCategory.CORPORATE;
    }
    if (isPersonal() && !isCorporate()) {
      return AddressTypeCategory.PERSONAL;
    }
    // BILLING can be both - default to PERSONAL for user addresses
    // For company addresses, use CORPORATE explicitly
    return AddressTypeCategory.PERSONAL;
  }

  /**
   * Check if this address type is valid for user addresses.
   *
   * @return true if valid for user addresses
   */
  public boolean isValidForUser() {
    return isPersonal() || this == OFFICE || this == WORK || isField();
  }

  /**
   * Check if this address type is valid for company addresses.
   *
   * @return true if valid for company addresses
   */
  public boolean isValidForCompany() {
    return isCorporate() || isField();
  }
}
