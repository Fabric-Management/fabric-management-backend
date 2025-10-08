package com.fabricmanagement.shared.domain.policy;

/**
 * User Context Enum
 * 
 * Defines the context/origin of a user in relation to the system.
 * Works together with CompanyType to determine access boundaries.
 * 
 * Usage:
 * - INTERNAL: Employee of our company - Full system access based on role
 * - CUSTOMER: Employee of customer company - Limited to order/invoice viewing
 * - SUPPLIER: Employee of supplier company - Limited to purchase order updates
 * - SUBCONTRACTOR: Employee of subcontractor - Limited to production order updates
 * 
 * Key Difference from CompanyType:
 * - CompanyType: Describes the company
 * - UserContext: Describes the user's relationship to the system
 * 
 * Example:
 * - User: "John Doe"
 * - UserContext: CUSTOMER
 * - CompanyType: CUSTOMER
 * - Department: null (external users don't have departments)
 * 
 * @see com.fabricmanagement.shared.domain.policy.CompanyType
 * @see com.fabricmanagement.shared.domain.policy.DataScope
 */
public enum UserContext {
    
    /**
     * Internal user (our company's employee)
     * - Has department assignment
     * - Full feature access based on role
     * - Can be: ADMIN, MANAGER, OPERATOR, etc.
     */
    INTERNAL("Internal Employee"),
    
    /**
     * Customer user (buyer company's employee)
     * - No department (external)
     * - Read-only access to own orders/invoices
     * - Typical roles: CUSTOMER_VIEWER, CUSTOMER_ADMIN
     */
    CUSTOMER("Customer User"),
    
    /**
     * Supplier user (raw material provider's employee)
     * - No department (external)
     * - Can update purchase order status, create deliveries
     * - Typical roles: SUPPLIER_MANAGER, SUPPLIER_OPERATOR
     */
    SUPPLIER("Supplier User"),
    
    /**
     * Subcontractor user (outsourced production worker)
     * - No department (external)
     * - Can update production orders, record production data
     * - Typical roles: SUBCONTRACTOR_MANAGER, SUBCONTRACTOR_OPERATOR
     */
    SUBCONTRACTOR("Subcontractor User");
    
    private final String displayLabel;
    
    UserContext(String displayLabel) {
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
     * Checks if this user is internal (employee of our company)
     * 
     * @return true if INTERNAL context
     */
    public boolean isInternal() {
        return this == INTERNAL;
    }
    
    /**
     * Checks if this user is external (from another company)
     * 
     * @return true if CUSTOMER, SUPPLIER, or SUBCONTRACTOR
     */
    public boolean isExternal() {
        return !isInternal();
    }
    
    /**
     * Checks if this user should have a department assignment
     * 
     * @return true if user should belong to a department
     */
    public boolean requiresDepartment() {
        return this == INTERNAL;  // Only internal users have departments
    }
    
    /**
     * Gets corresponding company type for this user context
     * Useful for validation: user context should match company type
     * 
     * @return corresponding CompanyType
     */
    public CompanyType getCorrespondingCompanyType() {
        return switch (this) {
            case INTERNAL -> CompanyType.INTERNAL;
            case CUSTOMER -> CompanyType.CUSTOMER;
            case SUPPLIER -> CompanyType.SUPPLIER;
            case SUBCONTRACTOR -> CompanyType.SUBCONTRACTOR;
        };
    }
}

