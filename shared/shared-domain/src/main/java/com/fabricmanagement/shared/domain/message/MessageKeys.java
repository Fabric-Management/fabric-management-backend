package com.fabricmanagement.shared.domain.message;

/**
 * Message Keys - Centralized Message Management
 * 
 * All user-facing messages MUST use keys from this interface.
 * Actual messages are in properties files (EN/TR).
 * 
 * Pattern: {category}.{action}.{status}
 * Example: auth.email.not_found
 */
public interface MessageKeys {
    
    // Base interface - extended by specific message key classes
    String getKey();
}

