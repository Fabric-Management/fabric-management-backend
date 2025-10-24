package com.fabricmanagement.company.domain.valueobject;

/**
 * Company Status Enumeration
 * 
 * Represents the possible states of a company
 */
public enum CompanyStatus {
    /**
     * Company is active and operational
     */
    ACTIVE,
    
    /**
     * Company is inactive (temporarily disabled)
     */
    INACTIVE,
    
    /**
     * Company is suspended (due to violations or issues)
     */
    SUSPENDED,
    
    /**
     * Company is pending activation
     */
    PENDING,
    
    /**
     * Company is deleted (soft delete)
     */
    DELETED
}
