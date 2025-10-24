package com.fabricmanagement.shared.domain.valueobject;

/**
 * Operation Type
 * 
 * Defines the type of operation being performed
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ POLICY FRAMEWORK
 * ✅ ENUM TYPE SAFETY
 */
public enum OperationType {
    
    // =========================================================================
    // CRUD OPERATIONS
    // =========================================================================
    CREATE("Create a new resource"),
    READ("Read/view a resource"),
    UPDATE("Update/modify a resource"),
    DELETE("Delete a resource"),
    
    // =========================================================================
    // BUSINESS OPERATIONS
    // =========================================================================
    APPROVE("Approve a resource"),
    REJECT("Reject a resource"),
    PUBLISH("Publish a resource"),
    ARCHIVE("Archive a resource"),
    RESTORE("Restore a resource"),
    
    // =========================================================================
    // SYSTEM OPERATIONS
    // =========================================================================
    LOGIN("User login"),
    LOGOUT("User logout"),
    REGISTER("User registration"),
    RESET_PASSWORD("Password reset"),
    CHANGE_PASSWORD("Password change"),
    
    // =========================================================================
    // ADMIN OPERATIONS
    // =========================================================================
    ASSIGN_ROLE("Assign role to user"),
    REMOVE_ROLE("Remove role from user"),
    GRANT_PERMISSION("Grant permission"),
    REVOKE_PERMISSION("Revoke permission"),
    
    // =========================================================================
    // AUDIT OPERATIONS
    // =========================================================================
    AUDIT("Audit operation"),
    EXPORT("Export data"),
    IMPORT("Import data"),
    
    // =========================================================================
    // COMMUNICATION OPERATIONS
    // =========================================================================
    SEND_NOTIFICATION("Send notification"),
    SEND_EMAIL("Send email"),
    SEND_SMS("Send SMS");
    
    private final String description;
    
    OperationType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
    
    /**
     * Check if this is a CRUD operation
     */
    public boolean isCrudOperation() {
        return this == CREATE || this == READ || this == UPDATE || this == DELETE;
    }
    
    /**
     * Check if this is a destructive operation
     */
    public boolean isDestructiveOperation() {
        return this == DELETE || this == ARCHIVE || this == REJECT;
    }
    
    /**
     * Check if this is a system operation
     */
    public boolean isSystemOperation() {
        return this == LOGIN || this == LOGOUT || this == REGISTER || 
               this == RESET_PASSWORD || this == CHANGE_PASSWORD;
    }
    
    /**
     * Check if this is an admin operation
     */
    public boolean isAdminOperation() {
        return this == ASSIGN_ROLE || this == REMOVE_ROLE || 
               this == GRANT_PERMISSION || this == REVOKE_PERMISSION;
    }
}
