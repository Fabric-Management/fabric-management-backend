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
    
    // Success Messages - Contact
    public static final String MSG_CONTACT_CREATED = "Contact created successfully";
    public static final String MSG_CONTACT_UPDATED = "Contact updated successfully";
    public static final String MSG_CONTACT_DELETED = "Contact deleted successfully";
    public static final String MSG_CONTACT_VERIFIED = "Contact verified successfully";
    public static final String MSG_CONTACT_SET_PRIMARY = "Contact set as primary successfully";
    public static final String MSG_VERIFICATION_CODE_SENT = "Verification code sent successfully";
    public static final String MSG_BATCH_SUCCESS = "Batch operation completed successfully";
    public static final String MSG_BATCH_CONTACTS_RETRIEVED = "Batch contacts retrieved successfully";
    
    // Success Messages - Company
    public static final String MSG_COMPANY_CREATED = "Company created successfully";
    public static final String MSG_COMPANY_UPDATED = "Company updated successfully";
    public static final String MSG_COMPANY_DELETED = "Company deleted successfully";
    
    // Success Messages - User
    public static final String MSG_USER_CREATED = "User created successfully";
    public static final String MSG_USER_UPDATED = "User updated successfully";
    public static final String MSG_USER_DELETED = "User deleted successfully";
    public static final String MSG_TENANT_REGISTERED = "Tenant registered successfully";
    public static final String MSG_PASSWORD_CREATED = "Password created successfully";
    
    // Success Messages - Company Settings & Subscription
    public static final String MSG_COMPANY_SETTINGS_UPDATED = "Company settings updated successfully";
    public static final String MSG_COMPANY_SUBSCRIPTION_UPDATED = "Company subscription updated successfully";
    public static final String MSG_COMPANY_ACTIVATED = "Company activated successfully";
    
    // Kafka Topics - Event-Driven Architecture
    public static final String TOPIC_TENANT_EVENTS = "tenant-events";
    public static final String TOPIC_COMPANY_EVENTS = "company-events";
    public static final String TOPIC_USER_EVENTS = "user-events";
    public static final String TOPIC_CONTACT_EVENTS = "contact-events";
    
    // Kafka Consumer Groups
    public static final String GROUP_CONTACT_SERVICE_TENANT = "contact-service-tenant-group";
    public static final String GROUP_COMPANY_SERVICE_TENANT = "company-service-tenant-group";
    public static final String GROUP_USER_SERVICE_COMPANY = "user-service-company-group";
    public static final String MSG_COMPANY_DEACTIVATED = "Company deactivated successfully";
    
    // Success Messages - Permissions
    public static final String MSG_PERMISSION_CREATED = "Permission created successfully";
    public static final String MSG_PERMISSION_DELETED = "Permission deleted successfully";
    
    // Error Messages - Not Found
    public static final String MSG_CONTACT_NOT_FOUND = "Contact not found";
    public static final String MSG_COMPANY_NOT_FOUND = "Company not found";
    public static final String MSG_USER_NOT_FOUND = "User not found";
    
    // Error Messages - Registration & Auth
    public static final String MSG_EMAIL_ALREADY_REGISTERED = "Email already registered";
    public static final String MSG_FAILED_TO_CREATE_COMPANY = "Failed to create company";
    public static final String MSG_FAILED_TO_CREATE_CONTACT = "Failed to create email contact";
    public static final String MSG_INVALID_CREDENTIALS = "Invalid credentials";
    public static final String MSG_PASSWORD_NOT_SET = "Password not set. Please setup your password first.";
    public static final String MSG_POLICY_VALIDATION_FAILED = "Policy validation failed";
    
    // Error Messages - Company Duplicates
    public static final String MSG_COMPANY_TAX_ID_ALREADY_REGISTERED = "A company with this tax ID is already registered";
    public static final String MSG_COMPANY_REGISTRATION_NUMBER_ALREADY_REGISTERED = "A company with this registration number is already registered";
    public static final String MSG_COMPANY_NAME_ALREADY_REGISTERED = "A company with this name is already registered";
    public static final String MSG_COMPANY_DUPLICATE_DETECTED = "A company matching your information is already registered";
    public static final String MSG_USE_FORGOT_PASSWORD = "If this is your company, please use the forgot password option to recover your account";
    
    // Error Messages - Email Validation
    public static final String MSG_CORPORATE_EMAIL_REQUIRED = "Please use your corporate email address for company registration";
    public static final String MSG_PERSONAL_EMAIL_NOT_ALLOWED = "Personal email addresses are not allowed for company registration";
    public static final String MSG_EMAIL_DOMAIN_MISMATCH = "Email domain should match your company website domain";
    public static final String MSG_EMAIL_DOMAIN_ALREADY_REGISTERED = "A company with this email domain is already registered";
    
    // Error Codes
    public static final String ERROR_CODE_CONTACT_NOT_FOUND = "CONTACT_NOT_FOUND";
    public static final String ERROR_CODE_COMPANY_NOT_FOUND = "COMPANY_NOT_FOUND";
    public static final String ERROR_CODE_USER_NOT_FOUND = "USER_NOT_FOUND";
    
    // Owner Types (matches database enum)
    public static final String OWNER_TYPE_USER = "USER";
    public static final String OWNER_TYPE_COMPANY = "COMPANY";
    
    // Contact Medium Types
    public static final String CONTACT_MEDIUM_EMAIL = "EMAIL";
    public static final String CONTACT_MEDIUM_PHONE = "PHONE";
    public static final String CONTACT_MEDIUM_ADDRESS = "ADDRESS";
    
    // Audit Trail Constants
    public static final String AUDIT_SYSTEM_USER = "SYSTEM";           // System-initiated actions (onboarding, migrations)
    public static final String AUDIT_ANONYMOUS_USER = "ANONYMOUS";     // Public actions before authentication
    public static final String AUDIT_SCHEDULER_USER = "SCHEDULER";     // Scheduled job actions
    public static final String AUDIT_MIGRATION_USER = "MIGRATION";     // Database migration actions
}

