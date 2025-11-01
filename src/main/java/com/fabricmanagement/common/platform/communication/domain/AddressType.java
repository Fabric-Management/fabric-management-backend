package com.fabricmanagement.common.platform.communication.domain;

/**
 * Address type enumeration for different address categories.
 *
 * <p>Used to categorize addresses for both User and Company entities.
 * Supports personal addresses (HOME), business addresses (WORK, HEADQUARTERS),
 * and operational addresses (WAREHOUSE, BRANCH, SHIPPING).</p>
 *
 * <h2>Usage:</h2>
 * <ul>
 *   <li><b>HOME:</b> User's personal home address</li>
 *   <li><b>WORK:</b> User's work/office address (independent from company address)</li>
 *   <li><b>HEADQUARTERS:</b> Company's main headquarters</li>
 *   <li><b>BRANCH:</b> Company branch office</li>
 *   <li><b>WAREHOUSE:</b> Company warehouse/storage facility</li>
 *   <li><b>SHIPPING:</b> Shipping/delivery address</li>
 *   <li><b>BILLING:</b> Billing/invoice address</li>
 * </ul>
 */
public enum AddressType {

    /**
     * Home address
     * <p>User's personal residential address</p>
     */
    HOME,

    /**
     * Work address
     * <p>User's work/office address (independent from company's address)</p>
     * <p>Used for: Employee office location tracking</p>
     */
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
     * Shipping address
     * <p>Address for shipping/delivery purposes</p>
     * <p>Used for: Order delivery, package shipping</p>
     */
    SHIPPING,

    /**
     * Billing address
     * <p>Address for billing/invoicing purposes</p>
     * <p>Used for: Invoice generation, tax documentation</p>
     */
    BILLING
}

