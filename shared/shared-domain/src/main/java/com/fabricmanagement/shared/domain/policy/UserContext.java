package com.fabricmanagement.shared.domain.policy;

/**
 * User Context Enum
 * 
 * Defines user's relationship to the system based on their company type.
 */
public enum UserContext {
    
    /**
     * Internal employee (our company)
     */
    INTERNAL,
    
    /**
     * Customer company employee
     */
    CUSTOMER,
    
    /**
     * Supplier company employee
     */
    SUPPLIER,
    
    /**
     * Subcontractor company employee
     */
    SUBCONTRACTOR;
    
    public boolean isInternal() {
        return this == INTERNAL;
    }
    
    public boolean isExternal() {
        return !isInternal();
    }
}
