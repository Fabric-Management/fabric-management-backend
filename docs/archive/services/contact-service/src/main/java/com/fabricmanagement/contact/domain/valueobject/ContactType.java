package com.fabricmanagement.contact.domain.valueobject;

/**
 * Contact Type Enumeration
 * 
 * Defines different types of contact information
 */
public enum ContactType {
    /**
     * Email address
     */
    EMAIL,
    
    /**
     * Phone number (mobile, landline)
     */
    PHONE,
    
    /**
     * Phone extension (internal)
     * Links to company's main phone via parent_contact_id
     * Example: ext 101, 102, etc.
     */
    PHONE_EXTENSION,
    
    /**
     * Physical address
     * Actual data stored in addresses table
     */
    ADDRESS,
    
    /**
     * Fax number
     */
    FAX,
    
    /**
     * Website URL
     */
    WEBSITE,
    
    /**
     * Social media handle
     */
    SOCIAL_MEDIA
}
