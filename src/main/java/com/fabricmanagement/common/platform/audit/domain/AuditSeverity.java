package com.fabricmanagement.common.platform.audit.domain;

/**
 * Audit log severity levels.
 */
public enum AuditSeverity {

    /**
     * Informational - Normal operations
     */
    INFO,

    /**
     * Warning - Suspicious but allowed
     */
    WARNING,

    /**
     * Error - Failed operations
     */
    ERROR,

    /**
     * Critical - Security incidents
     */
    CRITICAL
}

