package com.fabricmanagement.contact.domain.valueobject;

/**
 * Contact Type Enumeration
 * 
 * Represents the type of contact (user or company)
 */
public enum ContactType {
    /**
     * Contact belongs to a user
     */
    USER,
    
    /**
     * Contact belongs to a company
     */
    COMPANY,
    
    /**
     * Contact is shared between user and company
     */
    SHARED
}
