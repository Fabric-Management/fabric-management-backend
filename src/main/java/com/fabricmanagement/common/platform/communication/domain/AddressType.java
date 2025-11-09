package com.fabricmanagement.common.platform.communication.domain;

/**
 * Address type enumeration for different address categories.
 *
 * <p>Used to categorize addresses for both User and Company entities.
 * Supports personal addresses (HOME, BILLING, MAILING), business addresses (OFFICE, HEADQUARTERS),
 * operational addresses (WAREHOUSE, FACTORY, BRANCH), and field addresses (WORKSITE, REMOTE).</p>
 *
 * <h2>Personal Address Types:</h2>
 * <ul>
 *   <li><b>HOME:</b> Employee's personal residential address</li>
 *   <li><b>BILLING:</b> Billing address for invoices or official document delivery</li>
 *   <li><b>MAILING:</b> Alternative address for mail or communication</li>
 *   <li><b>TEMPORARY:</b> Temporary accommodation or project-duration address</li>
 *   <li><b>ALTERNATE:</b> Second residential address (e.g., out-of-town home)</li>
 * </ul>
 *
 * <h2>Corporate/Operational Address Types:</h2>
 * <ul>
 *   <li><b>OFFICE:</b> Office building where user works</li>
 *   <li><b>WORK:</b> User's work/office address (legacy, use OFFICE for new addresses)</li>
 *   <li><b>HEADQUARTERS:</b> Company's main headquarters location</li>
 *   <li><b>BRANCH:</b> Company branch office location</li>
 *   <li><b>WAREHOUSE:</b> Warehouse, production or storage facility</li>
 *   <li><b>FACTORY:</b> Production facility address</li>
 *   <li><b>SHIPPING:</b> Shipping/delivery address</li>
 *   <li><b>BILLING:</b> Billing/invoice address (also used for companies)</li>
 * </ul>
 *
 * <h2>Field/Work Travel Address Types:</h2>
 * <ul>
 *   <li><b>WORKSITE:</b> Field work location address (e.g., construction site, project area)</li>
 *   <li><b>REMOTE:</b> Remote work location (e.g., home-office outside city)</li>
 * </ul>
 */
public enum AddressType {

    // ========== Personal Address Types ==========

    /**
     * Home address
     * <p>Employee's personal residential address</p>
     */
    HOME,

    /**
     * Billing address
     * <p>Address for billing/invoicing or official document delivery</p>
     * <p>Used for: Invoice generation, tax documentation, official correspondence</p>
     * <p>Can be used for both personal and company addresses</p>
     */
    BILLING,

    /**
     * Mailing address
     * <p>Alternative address for mail or communication purposes</p>
     * <p>Used for: Personal mail delivery, alternative contact location</p>
     */
    MAILING,

    /**
     * Temporary address
     * <p>Temporary accommodation or project-duration address</p>
     * <p>Used for: Short-term assignments, project-based locations</p>
     */
    TEMPORARY,

    /**
     * Alternate address
     * <p>Second residential address (e.g., out-of-town home, vacation property)</p>
     * <p>Used for: Secondary residence, alternate living location</p>
     */
    ALTERNATE,

    // ========== Corporate/Operational Address Types ==========

    /**
     * Office address
     * <p>Office building where user works</p>
     * <p>Used for: Employee office location tracking, corporate office addresses</p>
     */
    OFFICE,

    /**
     * Work address (Legacy)
     * <p>User's work/office address (independent from company's address)</p>
     * <p>Used for: Employee office location tracking</p>
     * <p><b>Note:</b> For new addresses, prefer {@link #OFFICE} instead</p>
     * @deprecated Use {@link #OFFICE} for new addresses. Kept for backward compatibility.
     */
    @Deprecated
    WORK,

    /**
     * Company headquarters
     * <p>Company's main headquarters location</p>
     * <p>Used for: Legal address, main office</p>
     */
    HEADQUARTERS,

    /**
     * Branch office
     * <p>Company's branch office location</p>
     * <p>Used for: Regional offices, subsidiaries</p>
     */
    BRANCH,

    /**
     * Warehouse/storage facility
     * <p>Company's warehouse or storage location</p>
     * <p>Used for: Inventory management, logistics</p>
     */
    WAREHOUSE,

    /**
     * Factory address
     * <p>Production facility address</p>
     * <p>Used for: Manufacturing locations, production sites</p>
     */
    FACTORY,

    /**
     * Shipping address
     * <p>Address for shipping/delivery purposes</p>
     * <p>Used for: Order delivery, package shipping</p>
     */
    SHIPPING,

    // ========== Field/Work Travel Address Types ==========

    /**
     * Worksite address
     * <p>Field work location address (e.g., construction site, project area)</p>
     * <p>Used for: On-site work locations, field assignments</p>
     */
    WORKSITE,

    /**
     * Remote location
     * <p>Remote work location (e.g., home-office outside city)</p>
     * <p>Used for: Remote work tracking, distributed team locations</p>
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

