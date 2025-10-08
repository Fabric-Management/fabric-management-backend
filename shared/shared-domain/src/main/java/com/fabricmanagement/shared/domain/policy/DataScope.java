package com.fabricmanagement.shared.domain.policy;

/**
 * Data Scope Enum
 * 
 * Defines the scope of data access for authorization decisions.
 * Critical component of the policy system - determines WHICH data user can access.
 * 
 * Scope Hierarchy (from narrow to broad):
 * SELF → COMPANY → CROSS_COMPANY → GLOBAL
 * 
 * Usage in Policy System:
 * - Combined with OperationType for access control
 * - Validated at both Gateway (PDP) and Service level (defense in depth)
 * - Determines data filtering in repository queries
 * 
 * Example:
 * - User with SELF scope can only access their own contacts
 * - User with COMPANY scope can access all company contacts
 * - User with GLOBAL scope can access all system data (Super Admin)
 * 
 * Policy Tag Example: "WRITE:CONTACT/COMPANY"
 * - Operation: WRITE
 * - Resource: CONTACT
 * - Scope: COMPANY (can write company-wide contacts)
 * 
 * @see com.fabricmanagement.shared.domain.policy.OperationType
 * @see com.fabricmanagement.shared.domain.policy.CompanyType
 */
public enum DataScope {
    
    /**
     * Self scope - Own data only
     * 
     * Access Rules:
     * - User can only access their own data
     * - Validation: resource.ownerId == user.id
     * 
     * Examples:
     * - User can view/edit own profile
     * - User can view/edit own contacts
     * - User can view own orders
     * 
     * Typical Roles: USER, OPERATOR
     */
    SELF("Self", "Kendim", 0),
    
    /**
     * Company scope - Company-wide data
     * 
     * Access Rules:
     * - User can access data within their company
     * - Validation: resource.companyId == user.companyId
     * 
     * Examples:
     * - Manager can view all company users
     * - Admin can view all company orders
     * - Manager can view department production data
     * 
     * Typical Roles: MANAGER, ADMIN
     */
    COMPANY("Company", "Şirket", 1),
    
    /**
     * Cross-company scope - Multi-company data
     * 
     * Access Rules:
     * - User can access data across multiple companies
     * - Requires company relationship (trust)
     * - Validation: relationship exists AND is ACTIVE
     * 
     * Examples:
     * - Internal manager viewing customer orders
     * - Internal admin viewing supplier delivery status
     * - Production coordinator viewing subcontractor status
     * 
     * Typical Roles: INTERNAL MANAGER, INTERNAL ADMIN
     * Required: CompanyRelationship must exist
     */
    CROSS_COMPANY("Cross-Company", "Şirketler Arası", 2),
    
    /**
     * Global scope - System-wide data
     * 
     * Access Rules:
     * - User can access all data in the system
     * - No company/tenant restrictions
     * - Highest level of access
     * 
     * Examples:
     * - Super Admin can view all companies
     * - Super Admin can view all users
     * - Platform-level operations
     * 
     * Typical Roles: SUPER_ADMIN only
     */
    GLOBAL("Global", "Genel", 3);
    
    private final String displayLabel;
    private final String displayLabelTr;
    private final int level;  // Hierarchy level (0 = narrowest, 3 = broadest)
    
    DataScope(String displayLabel, String displayLabelTr, int level) {
        this.displayLabel = displayLabel;
        this.displayLabelTr = displayLabelTr;
        this.level = level;
    }
    
    /**
     * Gets the display label in English
     * 
     * @return English display label
     */
    public String getDisplayLabel() {
        return displayLabel;
    }
    
    /**
     * Gets the display label in Turkish
     * 
     * @return Turkish display label
     */
    public String getDisplayLabelTr() {
        return displayLabelTr;
    }
    
    /**
     * Gets the display label for current locale
     * 
     * @param locale "en" or "tr"
     * @return localized display label
     */
    public String getDisplayLabel(String locale) {
        return "tr".equalsIgnoreCase(locale) ? displayLabelTr : displayLabel;
    }
    
    /**
     * Gets the hierarchy level
     * 
     * @return level (0 = SELF, 3 = GLOBAL)
     */
    public int getLevel() {
        return level;
    }
    
    /**
     * Checks if this scope is broader than another scope
     * 
     * @param other scope to compare
     * @return true if this scope includes the other scope
     */
    public boolean includes(DataScope other) {
        return this.level >= other.level;
    }
    
    /**
     * Checks if this scope requires company relationship validation
     * 
     * @return true if CROSS_COMPANY scope
     */
    public boolean requiresRelationshipCheck() {
        return this == CROSS_COMPANY;
    }
    
    /**
     * Checks if this is the most restrictive scope
     * 
     * @return true if SELF scope
     */
    public boolean isMostRestrictive() {
        return this == SELF;
    }
    
    /**
     * Checks if this is the least restrictive scope
     * 
     * @return true if GLOBAL scope
     */
    public boolean isLeastRestrictive() {
        return this == GLOBAL;
    }
}

