package com.fabricmanagement.shared.domain.message;

import lombok.experimental.UtilityClass;

/**
 * Message Keys
 * 
 * Centralized message keys for i18n support
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ MESSAGE KEYS
 * ✅ i18n SUPPORT
 */
@UtilityClass
public class MessageKeys {
    
    // =========================================================================
    // GENERAL MESSAGES
    // =========================================================================
    
    public static final String SUCCESS = "success";
    public static final String ERROR = "error";
    public static final String WARNING = "warning";
    public static final String INFO = "info";
    
    // =========================================================================
    // VALIDATION MESSAGES
    // =========================================================================
    
    public static final String VALIDATION_REQUIRED = "validation.required";
    public static final String VALIDATION_INVALID_FORMAT = "validation.invalid_format";
    public static final String VALIDATION_TOO_SHORT = "validation.too_short";
    public static final String VALIDATION_TOO_LONG = "validation.too_long";
    public static final String VALIDATION_INVALID_EMAIL = "validation.invalid_email";
    public static final String VALIDATION_INVALID_PHONE = "validation.invalid_phone";
    public static final String VALIDATION_INVALID_UUID = "validation.invalid_uuid";
    public static final String VALIDATION_PASSWORD_WEAK = "validation.password_weak";
    public static final String VALIDATION_PASSWORD_MISMATCH = "validation.password_mismatch";
    
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
    // USER MESSAGES
    // =========================================================================
    
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DELETED = "user.deleted";
    public static final String USER_NOT_FOUND = "user.not_found";
    public static final String USER_ALREADY_EXISTS = "user.already_exists";
    public static final String USER_PROFILE_UPDATED = "user.profile_updated";
    public static final String USER_PASSWORD_CHANGED = "user.password_changed";
    public static final String USER_STATUS_CHANGED = "user.status_changed";
    
    // =========================================================================
    // COMPANY MESSAGES
    // =========================================================================
    
    public static final String COMPANY_CREATED = "company.created";
    public static final String COMPANY_UPDATED = "company.updated";
    public static final String COMPANY_DELETED = "company.deleted";
    public static final String COMPANY_NOT_FOUND = "company.not_found";
    public static final String COMPANY_ALREADY_EXISTS = "company.already_exists";
    public static final String COMPANY_STATUS_CHANGED = "company.status_changed";
    
    // =========================================================================
    // CONTACT MESSAGES
    // =========================================================================
    
    public static final String CONTACT_CREATED = "contact.created";
    public static final String CONTACT_UPDATED = "contact.updated";
    public static final String CONTACT_DELETED = "contact.deleted";
    public static final String CONTACT_NOT_FOUND = "contact.not_found";
    public static final String CONTACT_ALREADY_EXISTS = "contact.already_exists";
    public static final String CONTACT_VERIFICATION_SENT = "contact.verification_sent";
    public static final String CONTACT_VERIFICATION_SUCCESS = "contact.verification_success";
    public static final String CONTACT_VERIFICATION_FAILED = "contact.verification_failed";
    public static final String CONTACT_VERIFICATION_EXPIRED = "contact.verification_expired";
    
    // =========================================================================
    // FIBER MESSAGES
    // =========================================================================
    
    public static final String FIBER_CREATED = "fiber.created";
    public static final String FIBER_UPDATED = "fiber.updated";
    public static final String FIBER_DELETED = "fiber.deleted";
    public static final String FIBER_NOT_FOUND = "fiber.not_found";
    public static final String FIBER_ALREADY_EXISTS = "fiber.already_exists";
    public static final String FIBER_STATUS_CHANGED = "fiber.status_changed";
    
    // =========================================================================
    // NOTIFICATION MESSAGES
    // =========================================================================
    
    public static final String NOTIFICATION_SENT = "notification.sent";
    public static final String NOTIFICATION_FAILED = "notification.failed";
    public static final String NOTIFICATION_DELIVERED = "notification.delivered";
    public static final String NOTIFICATION_READ = "notification.read";
    public static final String NOTIFICATION_DELETED = "notification.deleted";
    
    // =========================================================================
    // SYSTEM MESSAGES
    // =========================================================================
    
    public static final String SYSTEM_ERROR = "system.error";
    public static final String SYSTEM_UNAVAILABLE = "system.unavailable";
    public static final String SYSTEM_MAINTENANCE = "system.maintenance";
    public static final String SYSTEM_RATE_LIMIT = "system.rate_limit";
    public static final String SYSTEM_TIMEOUT = "system.timeout";
    
    // =========================================================================
    // BUSINESS MESSAGES
    // =========================================================================
    
    public static final String BUSINESS_RULE_VIOLATION = "business.rule_violation";
    public static final String BUSINESS_INVALID_OPERATION = "business.invalid_operation";
    public static final String BUSINESS_RESOURCE_CONFLICT = "business.resource_conflict";
    public static final String BUSINESS_QUOTA_EXCEEDED = "business.quota_exceeded";
    
    // =========================================================================
    // AUDIT MESSAGES
    // =========================================================================
    
    public static final String AUDIT_LOGIN_SUCCESS = "audit.login_success";
    public static final String AUDIT_LOGIN_FAILED = "audit.login_failed";
    public static final String AUDIT_LOGOUT = "audit.logout";
    public static final String AUDIT_PASSWORD_CHANGED = "audit.password_changed";
    public static final String AUDIT_ACCOUNT_LOCKED = "audit.account_locked";
    public static final String AUDIT_ACCOUNT_UNLOCKED = "audit.account_unlocked";
    public static final String AUDIT_PERMISSION_GRANTED = "audit.permission_granted";
    public static final String AUDIT_PERMISSION_REVOKED = "audit.permission_revoked";
    public static final String AUDIT_ROLE_ASSIGNED = "audit.role_assigned";
    public static final String AUDIT_ROLE_REMOVED = "audit.role_removed";
    
    // =========================================================================
    // ERROR MESSAGES
    // =========================================================================
    
    public static final String ERROR_INTERNAL_SERVER = "error.internal_server";
    public static final String ERROR_BAD_REQUEST = "error.bad_request";
    public static final String ERROR_UNAUTHORIZED = "error.unauthorized";
    public static final String ERROR_FORBIDDEN = "error.forbidden";
    public static final String ERROR_NOT_FOUND = "error.not_found";
    public static final String ERROR_METHOD_NOT_ALLOWED = "error.method_not_allowed";
    public static final String ERROR_CONFLICT = "error.conflict";
    public static final String ERROR_UNPROCESSABLE_ENTITY = "error.unprocessable_entity";
    public static final String ERROR_TOO_MANY_REQUESTS = "error.too_many_requests";
    public static final String ERROR_SERVICE_UNAVAILABLE = "error.service_unavailable";
    
    // =========================================================================
    // SUCCESS MESSAGES
    // =========================================================================
    
    public static final String SUCCESS_OPERATION_COMPLETED = "success.operation_completed";
    public static final String SUCCESS_DATA_RETRIEVED = "success.data_retrieved";
    public static final String SUCCESS_DATA_SAVED = "success.data_saved";
    public static final String SUCCESS_DATA_UPDATED = "success.data_updated";
    public static final String SUCCESS_DATA_DELETED = "success.data_deleted";
    public static final String SUCCESS_EMAIL_SENT = "success.email_sent";
    public static final String SUCCESS_SMS_SENT = "success.sms_sent";
    public static final String SUCCESS_FILE_UPLOADED = "success.file_uploaded";
    public static final String SUCCESS_FILE_DOWNLOADED = "success.file_downloaded";
}