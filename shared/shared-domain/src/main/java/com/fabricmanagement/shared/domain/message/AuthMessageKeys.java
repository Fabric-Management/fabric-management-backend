package com.fabricmanagement.shared.domain.message;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Authentication & Authorization Message Keys
 * 
 * All auth-related messages centralized here.
 * Actual messages in: resources/messages/auth_messages_{locale}.properties
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthMessageKeys {
    
    // Check Contact Responses
    public static final String EMAIL_NOT_REGISTERED = "auth.email.not_registered";
    public static final String EMAIL_FOUND_VERIFY = "auth.email.found_verify";
    public static final String EMAIL_FOUND_SET_PASSWORD = "auth.email.found_set_password";
    public static final String EMAIL_FOUND_LOGIN = "auth.email.found_login";
    public static final String CONTACT_NOT_FOUND = "auth.contact.not_found";
    public static final String USER_NOT_FOUND = "auth.user.not_found";
    
    // Login Messages
    public static final String LOGIN_SUCCESS = "auth.login.success";
    public static final String LOGIN_FAILED = "auth.login.failed";
    public static final String INVALID_CREDENTIALS = "auth.credentials.invalid";
    public static final String ACCOUNT_LOCKED = "auth.account.locked";
    public static final String ACCOUNT_NOT_ACTIVE = "auth.account.not_active";
    
    // Password Messages
    public static final String PASSWORD_CREATED = "auth.password.created";
    public static final String PASSWORD_ALREADY_SET = "auth.password.already_set";
    public static final String PASSWORD_NOT_SET = "auth.password.not_set";
    public static final String PASSWORD_RESET_SUCCESS = "auth.password.reset_success";
    
    // Verification Messages
    public static final String VERIFICATION_CODE_SENT = "auth.verification.code_sent";
    public static final String VERIFICATION_SUCCESS = "auth.verification.success";
    public static final String VERIFICATION_FAILED = "auth.verification.failed";
    public static final String VERIFICATION_CODE_INVALID = "auth.verification.code_invalid";
    public static final String VERIFICATION_CODE_EXPIRED = "auth.verification.code_expired";
    public static final String CONTACT_ALREADY_VERIFIED = "auth.contact.already_verified";
    
    // Next Step Actions (for UI routing)
    public static final String NEXT_STEP_REGISTER = "auth.next_step.register";
    public static final String NEXT_STEP_VERIFY = "auth.next_step.verify";
    public static final String NEXT_STEP_SET_PASSWORD = "auth.next_step.set_password";
    public static final String NEXT_STEP_LOGIN = "auth.next_step.login";
    public static final String NEXT_STEP_CONTACT_ADMIN = "auth.next_step.contact_admin";
}

