package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Service Communication Constants
 * 
 * Centralized constants for inter-service communication
 * NO HARDCODED STRINGS!
 */
public final class ServiceConstants {
    
    private ServiceConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    // Service Availability Messages
    public static final String MSG_SERVICE_UNAVAILABLE = "Service temporarily unavailable";
    public static final String MSG_CONTACT_SERVICE_UNAVAILABLE = "Contact Service temporarily unavailable";
    public static final String MSG_USER_SERVICE_UNAVAILABLE = "User Service temporarily unavailable";
    public static final String MSG_COMPANY_SERVICE_UNAVAILABLE = "Company Service temporarily unavailable";
    
    // Fallback Messages
    public static final String MSG_FALLBACK_EMPTY_RESULT = "Returning empty result due to service unavailability";
    public static final String MSG_FALLBACK_NULL_RESULT = "Returning null due to service unavailability";
    
    // Success Messages
    public static final String MSG_CONTACT_CREATED = "Contact created successfully";
    public static final String MSG_CONTACT_UPDATED = "Contact updated successfully";
    public static final String MSG_CONTACT_DELETED = "Contact deleted successfully";
    public static final String MSG_CONTACT_VERIFIED = "Contact verified successfully";
    public static final String MSG_CONTACT_SET_PRIMARY = "Contact set as primary successfully";
    public static final String MSG_VERIFICATION_CODE_SENT = "Verification code sent successfully";
    public static final String MSG_BATCH_SUCCESS = "Batch operation completed successfully";
    public static final String MSG_BATCH_CONTACTS_RETRIEVED = "Batch contacts retrieved successfully";
    
    // Owner Types (matches database enum)
    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_COMPANY = "COMPANY";
    
    // Contact Medium Types
    public static final String CONTACT_MEDIUM_EMAIL = "EMAIL";
    public static final String CONTACT_MEDIUM_PHONE = "PHONE";
    public static final String CONTACT_MEDIUM_ADDRESS = "ADDRESS";
}

