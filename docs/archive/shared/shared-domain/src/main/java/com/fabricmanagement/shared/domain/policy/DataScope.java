package com.fabricmanagement.shared.domain.policy;

/**
 * Data Scope Enum
 * 
 * Defines the scope of data access for authorization.
 */
public enum DataScope {
    
    SELF,           // Own data only
    COMPANY,        // Company-wide data
    CROSS_COMPANY,  // Multi-company data (trusted partners)
    GLOBAL;         // System-wide data (Super Admin only)
    
    public boolean isSelfOnly() {
        return this == SELF;
    }
    
    public boolean allowsCompanyAccess() {
        return this == COMPANY || this == CROSS_COMPANY || this == GLOBAL;
    }
    
    public boolean isGlobal() {
        return this == GLOBAL;
    }
}
