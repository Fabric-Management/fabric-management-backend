package com.fabricmanagement.shared.domain.policy;

/**
 * Company Type Enum
 * 
 * Defines the business relationship type of a company to the system.
 * 
 * Note: This is different from Company.type (legal entity type like CORPORATION, LLC).
 *       CompanyType is about business relationship to the system.
 */
public enum CompanyType {
    
    /**
     * Internal company (manufacturer/main company)
     * Our company - full system access
     */
    INTERNAL,
    
    /**
     * External customer company
     * Limited read access to their own data
     */
    CUSTOMER,
    
    /**
     * External supplier company
     * Limited write access for purchase orders
     */
    SUPPLIER,
    
    /**
     * External subcontractor company
     * Limited write access for production orders
     */
    SUBCONTRACTOR;
    
    public boolean isInternal() {
        return this == INTERNAL;
    }
    
    public boolean isExternal() {
        return !isInternal();
    }
}
