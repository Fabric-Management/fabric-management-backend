package com.fabricmanagement.shared.domain.policy;

/**
 * Operation Type Enum
 * 
 * Defines the type of operation being performed.
 */
public enum OperationType {
    
    READ,      // View data
    WRITE,     // Create/Update data
    DELETE,    // Delete data
    APPROVE,   // Approve workflow items
    EXPORT,    // Export data
    MANAGE;    // Administrative operations
    
    public boolean isReadOnly() {
        return this == READ;
    }
    
    public boolean isModifying() {
        return this == WRITE || this == DELETE;
    }
}
