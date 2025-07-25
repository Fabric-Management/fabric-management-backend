package com.fabricmanagement.user_service.constants;

/**
 * Message keys for internationalization
 * These keys correspond to entries in messages.properties files
 */
public final class MessageKeys {

    private MessageKeys() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }

    /**
     * Success message keys
     */
    public static final class Success {
        public static final String USER_CREATED = "success.user.created";
        public static final String USER_UPDATED = "success.user.updated";
        public static final String USER_DELETED = "success.user.deleted";
        public static final String PASSWORD_CREATED = "success.password.created";
        public static final String PASSWORD_CHANGED = "success.password.changed";
        public static final String PASSWORD_RESET = "success.password.reset";
        public static final String EMAIL_VERIFIED = "success.email.verified";
        public static final String EMAIL_SENT = "success.email.sent";
        public static final String LOGIN_SUCCESSFUL = "success.login";
        public static final String LOGOUT_SUCCESSFUL = "success.logout";
        public static final String ROLES_UPDATED = "success.roles.updated";
        public static final String STATUS_UPDATED = "success.status.updated";
        public static final String BULK_OPERATION_COMPLETED = "success.bulk.completed";
    }

    /**
     * Error message keys
     */
    public static final class Error {
        // Not Found
        public static final String USER_NOT_FOUND = "error.user.notFound";
        public static final String EMAIL_NOT_FOUND = "error.email.notFound";
        public static final String USERNAME_NOT_FOUND = "error.username.notFound";

        // Duplicate
        public static final String USERNAME_EXISTS = "error.username.exists";
        public static final String EMAIL_EXISTS = "error.email.exists";

        // Validation
        public static final String INVALID_PASSWORD = "error.password.invalid";
        public static final String CURRENT_PASSWORD_WRONG = "error.password.currentWrong";
        public static final String WEAK_PASSWORD = "error.password.weak";
        public static final String INVALID_EMAIL = "error.email.invalid";
        public static final String INVALID_USERNAME = "error.username.invalid";

        // Authentication
        public static final String INVALID_CREDENTIALS = "error.auth.invalidCredentials";
        public static final String ACCOUNT_LOCKED = "error.auth.accountLocked";
        public static final String ACCOUNT_NOT_ACTIVE = "error.auth.accountNotActive";
        public static final String EMAIL_NOT_VERIFIED = "error.auth.emailNotVerified";
        public static final String PASSWORD_NOT_SET = "error.auth.passwordNotSet";

        // Business Logic
        public static final String USER_HAS_PASSWORD = "error.user.hasPassword";
        public static final String CANNOT_DELETE_SELF = "error.user.cannotDeleteSelf";
        public static final String CANNOT_CHANGE_OWN_ROLE = "error.user.cannotChangeOwnRole";

        // System
        public static final String INTERNAL_ERROR = "error.system.internal";
        public static final String SERVICE_UNAVAILABLE = "error.system.unavailable";
    }

    /**
     * Validation message keys
     */
    public static final class Validation {
        public static final String REQUIRED_FIELD = "validation.field.required";
        public static final String MIN_LENGTH = "validation.field.minLength";
        public static final String MAX_LENGTH = "validation.field.maxLength";
        public static final String PATTERN_MISMATCH = "validation.field.pattern";
        public static final String INVALID_FORMAT = "validation.field.format";
        public static final String FUTURE_DATE = "validation.date.future";
        public static final String PAST_DATE = "validation.date.past";
    }

    /**
     * Info message keys
     */
    public static final class Info {
        public static final String VERIFICATION_CODE_SENT = "info.verification.sent";
        public static final String CHECK_EMAIL = "info.check.email";
        public static final String ACCOUNT_WILL_LOCK = "info.account.willLock";
        public static final String FIRST_LOGIN = "info.firstLogin";
        public static final String NO_DATA = "info.noData";
        public static final String MEMBER_SINCE = "info.memberSince";
    }

    /**
     * Email template keys
     */
    public static final class Template {
        public static final String SUBJECT_WELCOME = "email.subject.welcome";
        public static final String SUBJECT_VERIFICATION = "email.subject.verification";
        public static final String SUBJECT_PASSWORD_RESET = "email.subject.passwordReset";
        public static final String GREETING = "email.greeting";
        public static final String FOOTER = "email.footer";
    }
}