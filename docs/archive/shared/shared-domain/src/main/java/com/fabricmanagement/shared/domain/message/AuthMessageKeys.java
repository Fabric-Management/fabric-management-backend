package com.fabricmanagement.shared.domain.message;

import lombok.experimental.UtilityClass;

/**
 * Auth Message Keys
 * 
 * Authentication-specific message keys for i18n support
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ MESSAGE KEYS
 * ✅ i18n SUPPORT
 */
@UtilityClass
public class AuthMessageKeys {
    
    // =========================================================================
    // AUTHENTICATION MESSAGES
    // =========================================================================
    
    public static final String AUTH_LOGIN_SUCCESS = "auth.login_success";
    public static final String AUTH_LOGIN_FAILED = "auth.login_failed";
    public static final String AUTH_LOGOUT_SUCCESS = "auth.logout_success";
    public static final String AUTH_INVALID_CREDENTIALS = "auth.invalid_credentials";
    public static final String AUTH_ACCOUNT_LOCKED = "auth.account_locked";
    public static final String AUTH_ACCOUNT_DISABLED = "auth.account_disabled";
    public static final String AUTH_TOKEN_EXPIRED = "auth.token_expired";
    public static final String AUTH_TOKEN_INVALID = "auth.token_invalid";
    public static final String AUTH_ACCESS_DENIED = "auth.access_denied";
    public static final String AUTH_INSUFFICIENT_PERMISSIONS = "auth.insufficient_permissions";
    
    // =========================================================================
    // REGISTRATION MESSAGES
    // =========================================================================
    
    public static final String AUTH_REGISTRATION_SUCCESS = "auth.registration_success";
    public static final String AUTH_REGISTRATION_FAILED = "auth.registration_failed";
    public static final String AUTH_REGISTRATION_EMAIL_SENT = "auth.registration_email_sent";
    public static final String AUTH_REGISTRATION_EMAIL_VERIFIED = "auth.registration_email_verified";
    public static final String AUTH_REGISTRATION_EMAIL_VERIFICATION_FAILED = "auth.registration_email_verification_failed";
    public static final String AUTH_REGISTRATION_EMAIL_VERIFICATION_EXPIRED = "auth.registration_email_verification_expired";
    
    // =========================================================================
    // PASSWORD MESSAGES
    // =========================================================================
    
    public static final String AUTH_PASSWORD_CHANGED = "auth.password_changed";
    public static final String AUTH_PASSWORD_RESET_REQUESTED = "auth.password_reset_requested";
    public static final String AUTH_PASSWORD_RESET_SUCCESS = "auth.password_reset_success";
    public static final String AUTH_PASSWORD_RESET_FAILED = "auth.password_reset_failed";
    public static final String AUTH_PASSWORD_RESET_EXPIRED = "auth.password_reset_expired";
    public static final String AUTH_PASSWORD_WEAK = "auth.password_weak";
    public static final String AUTH_PASSWORD_MISMATCH = "auth.password_mismatch";
    public static final String AUTH_PASSWORD_SAME_AS_CURRENT = "auth.password_same_as_current";
    
    // =========================================================================
    // TOKEN MESSAGES
    // =========================================================================
    
    public static final String AUTH_TOKEN_GENERATED = "auth.token_generated";
    public static final String AUTH_TOKEN_REFRESHED = "auth.token_refreshed";
    public static final String AUTH_TOKEN_REVOKED = "auth.token_revoked";
    public static final String AUTH_TOKEN_BLACKLISTED = "auth.token_blacklisted";
    public static final String AUTH_TOKEN_VALIDATION_FAILED = "auth.token_validation_failed";
    public static final String AUTH_TOKEN_MALFORMED = "auth.token_malformed";
    public static final String AUTH_TOKEN_SIGNATURE_INVALID = "auth.token_signature_invalid";
    
    // =========================================================================
    // CONTACT MESSAGES
    // =========================================================================
    
    public static final String AUTH_CONTACT_NOT_FOUND = "auth.contact_not_found";
    public static final String AUTH_CONTACT_ALREADY_EXISTS = "auth.contact_already_exists";
    public static final String AUTH_CONTACT_NOT_VERIFIED = "auth.contact_not_verified";
    public static final String AUTH_CONTACT_VERIFICATION_REQUIRED = "auth.contact_verification_required";
    public static final String AUTH_CONTACT_VERIFICATION_SENT = "auth.contact_verification_sent";
    public static final String AUTH_CONTACT_VERIFICATION_SUCCESS = "auth.contact_verification_success";
    public static final String AUTH_CONTACT_VERIFICATION_FAILED = "auth.contact_verification_failed";
    public static final String AUTH_CONTACT_VERIFICATION_EXPIRED = "auth.contact_verification_expired";
    
    // =========================================================================
    // USER MESSAGES
    // =========================================================================
    
    public static final String AUTH_USER_NOT_FOUND = "auth.user_not_found";
    public static final String AUTH_USER_ALREADY_EXISTS = "auth.user_already_exists";
    public static final String AUTH_USER_INACTIVE = "auth.user_inactive";
    public static final String AUTH_USER_LOCKED = "auth.user_locked";
    public static final String AUTH_USER_SUSPENDED = "auth.user_suspended";
    public static final String AUTH_USER_DELETED = "auth.user_deleted";
    public static final String AUTH_USER_PROFILE_UPDATED = "auth.user_profile_updated";
    public static final String AUTH_USER_STATUS_CHANGED = "auth.user_status_changed";
    
    // =========================================================================
    // ROLE MESSAGES
    // =========================================================================
    
    public static final String AUTH_ROLE_ASSIGNED = "auth.role_assigned";
    public static final String AUTH_ROLE_REMOVED = "auth.role_removed";
    public static final String AUTH_ROLE_NOT_FOUND = "auth.role_not_found";
    public static final String AUTH_ROLE_ALREADY_ASSIGNED = "auth.role_already_assigned";
    public static final String AUTH_ROLE_NOT_ASSIGNED = "auth.role_not_assigned";
    public static final String AUTH_ROLE_INSUFFICIENT_PERMISSIONS = "auth.role_insufficient_permissions";
    
    // =========================================================================
    // PERMISSION MESSAGES
    // =========================================================================
    
    public static final String AUTH_PERMISSION_GRANTED = "auth.permission_granted";
    public static final String AUTH_PERMISSION_REVOKED = "auth.permission_revoked";
    public static final String AUTH_PERMISSION_NOT_FOUND = "auth.permission_not_found";
    public static final String AUTH_PERMISSION_ALREADY_GRANTED = "auth.permission_already_granted";
    public static final String AUTH_PERMISSION_NOT_GRANTED = "auth.permission_not_granted";
    public static final String AUTH_PERMISSION_DENIED = "auth.permission_denied";
    
    // =========================================================================
    // TENANT MESSAGES
    // =========================================================================
    
    public static final String AUTH_TENANT_NOT_FOUND = "auth.tenant_not_found";
    public static final String AUTH_TENANT_ALREADY_EXISTS = "auth.tenant_already_exists";
    public static final String AUTH_TENANT_INACTIVE = "auth.tenant_inactive";
    public static final String AUTH_TENANT_SUSPENDED = "auth.tenant_suspended";
    public static final String AUTH_TENANT_ACCESS_DENIED = "auth.tenant_access_denied";
    public static final String AUTH_TENANT_QUOTA_EXCEEDED = "auth.tenant_quota_exceeded";
    
    // =========================================================================
    // SECURITY MESSAGES
    // =========================================================================
    
    public static final String AUTH_SECURITY_VIOLATION = "auth.security_violation";
    public static final String AUTH_SECURITY_BREACH = "auth.security_breach";
    public static final String AUTH_SECURITY_SUSPICIOUS_ACTIVITY = "auth.security_suspicious_activity";
    public static final String AUTH_SECURITY_RATE_LIMIT_EXCEEDED = "auth.security_rate_limit_exceeded";
    public static final String AUTH_SECURITY_BRUTE_FORCE_DETECTED = "auth.security_brute_force_detected";
    public static final String AUTH_SECURITY_ACCOUNT_LOCKED = "auth.security_account_locked";
    public static final String AUTH_SECURITY_ACCOUNT_UNLOCKED = "auth.security_account_unlocked";
    
    // =========================================================================
    // VALIDATION MESSAGES
    // =========================================================================
    
    public static final String AUTH_VALIDATION_CONTACT_REQUIRED = "auth.validation.contact_required";
    public static final String AUTH_VALIDATION_PASSWORD_REQUIRED = "auth.validation.password_required";
    public static final String AUTH_VALIDATION_TENANT_REQUIRED = "auth.validation.tenant_required";
    public static final String AUTH_VALIDATION_EMAIL_INVALID = "auth.validation.email_invalid";
    public static final String AUTH_VALIDATION_PHONE_INVALID = "auth.validation.phone_invalid";
    public static final String AUTH_VALIDATION_PASSWORD_WEAK = "auth.validation.password_weak";
    public static final String AUTH_VALIDATION_PASSWORD_MISMATCH = "auth.validation.password_mismatch";
    public static final String AUTH_VALIDATION_TOKEN_INVALID = "auth.validation.token_invalid";
    public static final String AUTH_VALIDATION_ROLE_INVALID = "auth.validation.role_invalid";
    public static final String AUTH_VALIDATION_PERMISSION_INVALID = "auth.validation.permission_invalid";
    
    // =========================================================================
    // AUDIT MESSAGES
    // =========================================================================
    
    public static final String AUTH_AUDIT_LOGIN_SUCCESS = "auth.audit.login_success";
    public static final String AUTH_AUDIT_LOGIN_FAILED = "auth.audit.login_failed";
    public static final String AUTH_AUDIT_LOGOUT = "auth.audit.logout";
    public static final String AUTH_AUDIT_PASSWORD_CHANGED = "auth.audit.password_changed";
    public static final String AUTH_AUDIT_ACCOUNT_LOCKED = "auth.audit.account_locked";
    public static final String AUTH_AUDIT_ACCOUNT_UNLOCKED = "auth.audit.account_unlocked";
    public static final String AUTH_AUDIT_PERMISSION_GRANTED = "auth.audit.permission_granted";
    public static final String AUTH_AUDIT_PERMISSION_REVOKED = "auth.audit.permission_revoked";
    public static final String AUTH_AUDIT_ROLE_ASSIGNED = "auth.audit.role_assigned";
    public static final String AUTH_AUDIT_ROLE_REMOVED = "auth.audit.role_removed";
    public static final String AUTH_AUDIT_TOKEN_REVOKED = "auth.audit.token_revoked";
    public static final String AUTH_AUDIT_SECURITY_VIOLATION = "auth.audit.security_violation";
}