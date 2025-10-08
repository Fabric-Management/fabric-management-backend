package com.fabricmanagement.shared.domain.policy;

/**
 * Company Type Enum
 * 
 * Defines the type of company and determines access boundaries in the policy system.
 * 
 * Usage:
 * - INTERNAL: Main manufacturing company (us) - Full access (read/write/delete)
 * - CUSTOMER: Customer company - Read-only access with limited scope
 * - SUPPLIER: Supplier company - Limited write access (purchase orders)
 * - SUBCONTRACTOR: Subcontractor - Limited write access (production orders)
 * 
 * Policy Impact:
 * - External types (CUSTOMER/SUPPLIER/SUBCONTRACTOR) have write restrictions
 * - Company type is the outer boundary of data access
 * - Used in PDP (Policy Decision Point) for guardrail checks
 * 
 * @see com.fabricmanagement.shared.domain.policy.UserContext
 * @see com.fabricmanagement.shared.domain.policy.DataScope
 */
public enum CompanyType {
    
    /**
     * Internal company (main manufacturer)
     * - Full access to system
     * - Can perform all operations (READ/WRITE/DELETE)
     * - Default type for primary organization
     */
    INTERNAL("Internal Company"),
    
    /**
     * Customer company (buyer)
     * - Read-only access
     * - Can view orders, invoices
     * - Cannot modify production data
     */
    CUSTOMER("Customer Company"),
    
    /**
     * Supplier company (raw material provider)
     * - Limited write access
     * - Can update purchase order status
     * - Can create delivery records
     */
    SUPPLIER("Supplier Company"),
    
    /**
     * Subcontractor (outsourced production)
     * - Limited write access
     * - Can update production order status
     * - Can record production data
     */
    SUBCONTRACTOR("Subcontractor");
    
    private final String displayLabel;
    
    CompanyType(String displayLabel) {
        this.displayLabel = displayLabel;
    }
    
    /**
     * Gets the display label for UI/API
     * 
     * @return human-readable label
     */
    public String getDisplayLabel() {
        return displayLabel;
    }
    
    /**
     * Checks if this company type is internal (full access)
     * 
     * @return true if INTERNAL type
     */
    public boolean isInternal() {
        return this == INTERNAL;
    }
    
    /**
     * Checks if this company type is external (restricted access)
     * 
     * @return true if CUSTOMER, SUPPLIER, or SUBCONTRACTOR
     */
    public boolean isExternal() {
        return !isInternal();
    }
    
    /**
     * Checks if this company type can write data
     * 
     * @return true if write operations are allowed by default
     */
    public boolean canWrite() {
        return switch (this) {
            case INTERNAL -> true;
            case SUPPLIER, SUBCONTRACTOR -> true;  // Limited write
            case CUSTOMER -> false;  // Read-only
        };
    }
    
    /**
     * Checks if this company type can delete data
     * 
     * @return true if delete operations are allowed
     */
    public boolean canDelete() {
        return this == INTERNAL;  // Only internal can delete
    }
}

