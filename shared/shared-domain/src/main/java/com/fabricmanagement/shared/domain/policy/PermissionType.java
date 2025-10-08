package com.fabricmanagement.shared.domain.policy;

/**
 * Permission Type Enum
 * 
 * Defines whether a user permission grant allows or denies access.
 * Used in UserPermission entity for Advanced Settings (user-specific grants).
 * 
 * Key Rule: DENY always wins over ALLOW (fail-safe security model)
 * 
 * Usage Flow:
 * 1. Check user-specific DENY grants → if found, immediately DENY
 * 2. Check role default permissions → if ALLOW, continue
 * 3. Check user-specific ALLOW grants → if found, ALLOW
 * 4. If no match, fall back to role default or DENY
 * 
 * Example:
 * - User has role OPERATOR → default [READ:CONTACT/SELF]
 * - Admin adds user grant: [ALLOW, WRITE:CONTACT/COMPANY, 7 days]
 * - User can now write company contacts for 7 days
 * - After 7 days, grant expires → back to role default (SELF only)
 * 
 * Example (DENY override):
 * - User has role ADMIN → default [WRITE:USER/COMPANY]
 * - Admin adds user grant: [DENY, DELETE:USER/COMPANY, permanent]
 * - User can no longer delete users (even though ADMIN role normally allows)
 * 
 * @see com.fabricmanagement.shared.domain.policy.OperationType
 * @see com.fabricmanagement.shared.domain.policy.DataScope
 */
public enum PermissionType {
    
    /**
     * Allow permission
     * - Grants access to an operation
     * - Can be overridden by DENY
     * - Used to extend role permissions
     * - Example: OPERATOR gets temporary WRITE:USER/COMPANY access
     */
    ALLOW("Allow", "İzin Ver"),
    
    /**
     * Deny permission
     * - Denies access to an operation
     * - Always takes precedence over ALLOW
     * - Used to restrict role permissions
     * - Example: ADMIN loses DELETE:USER/COMPANY access (safety)
     */
    DENY("Deny", "İzin Verme");
    
    private final String displayLabel;
    private final String displayLabelTr;
    
    PermissionType(String displayLabel, String displayLabelTr) {
        this.displayLabel = displayLabel;
        this.displayLabelTr = displayLabelTr;
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
     * Checks if this permission type is ALLOW
     * 
     * @return true if ALLOW
     */
    public boolean isAllow() {
        return this == ALLOW;
    }
    
    /**
     * Checks if this permission type is DENY
     * 
     * @return true if DENY
     */
    public boolean isDeny() {
        return this == DENY;
    }
    
    /**
     * Gets priority for conflict resolution
     * Higher priority wins in case of conflict
     * 
     * @return priority (DENY = 1, ALLOW = 0)
     */
    public int getPriority() {
        return this == DENY ? 1 : 0;  // DENY has higher priority
    }
    
    /**
     * Resolves conflict between two permission types
     * 
     * @param other other permission type
     * @return winning permission type (DENY always wins)
     */
    public PermissionType resolveConflict(PermissionType other) {
        // DENY always wins
        if (this == DENY || other == DENY) {
            return DENY;
        }
        return ALLOW;
    }
}

