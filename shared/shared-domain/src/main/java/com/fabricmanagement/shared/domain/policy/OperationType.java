package com.fabricmanagement.shared.domain.policy;

/**
 * Operation Type Enum
 * 
 * Defines types of operations that can be performed on resources.
 * Used in policy evaluation to determine if an operation is allowed.
 * 
 * Usage in Policy System:
 * - Combined with CompanyType for guardrail checks
 * - Combined with Role for default permissions
 * - Combined with DataScope for access control
 * 
 * Example Policy Tag: "WRITE:CONTACT/COMPANY"
 * - Operation: WRITE
 * - Resource: CONTACT
 * - Scope: COMPANY
 * 
 * @see com.fabricmanagement.shared.domain.policy.DataScope
 * @see com.fabricmanagement.shared.domain.policy.CompanyType
 */
public enum OperationType {
    
    /**
     * Read/View operation
     * - Viewing data
     * - Listing resources
     * - Searching and filtering
     * - Least restrictive operation
     */
    READ("Read", "Görüntüleme"),
    
    /**
     * Write operation
     * - Creating new resources
     * - Updating existing resources
     * - Moderate restriction level
     * - Often limited by CompanyType
     */
    WRITE("Write", "Yazma"),
    
    /**
     * Delete operation
     * - Soft or hard delete
     * - Most restrictive operation
     * - Usually INTERNAL-only
     * - Requires ADMIN or higher role
     */
    DELETE("Delete", "Silme"),
    
    /**
     * Approve operation
     * - Approval workflows
     * - Status changes (pending → approved)
     * - High restriction level
     * - Requires MANAGER or ADMIN role
     */
    APPROVE("Approve", "Onaylama"),
    
    /**
     * Export operation
     * - Data export (CSV, Excel, PDF)
     * - Report generation
     * - Moderate restriction (data sensitivity)
     * - Audit-logged operation
     */
    EXPORT("Export", "Dışa Aktarma"),
    
    /**
     * Manage operation
     * - Permission grant/revoke
     * - System configuration
     * - Highest restriction level
     * - ADMIN or SUPER_ADMIN only
     */
    MANAGE("Manage", "Yönetim");
    
    private final String displayLabel;
    private final String displayLabelTr;
    
    OperationType(String displayLabel, String displayLabelTr) {
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
     * Checks if this is a read-only operation
     * 
     * @return true if READ or EXPORT (non-mutating)
     */
    public boolean isReadOnly() {
        return this == READ || this == EXPORT;
    }
    
    /**
     * Checks if this operation mutates data
     * 
     * @return true if WRITE, DELETE, APPROVE, or MANAGE
     */
    public boolean isMutating() {
        return !isReadOnly();
    }
    
    /**
     * Gets restriction level (0 = least restrictive, 5 = most restrictive)
     * Used for policy evaluation priority
     * 
     * @return restriction level
     */
    public int getRestrictionLevel() {
        return switch (this) {
            case READ -> 0;
            case EXPORT -> 1;
            case WRITE -> 2;
            case APPROVE -> 3;
            case DELETE -> 4;
            case MANAGE -> 5;
        };
    }
    
    /**
     * Checks if this operation requires audit logging
     * 
     * @return true if operation should be audited
     */
    public boolean requiresAudit() {
        return switch (this) {
            case READ -> false;  // Too noisy to audit every read
            case WRITE, DELETE, APPROVE, EXPORT, MANAGE -> true;
        };
    }
}

