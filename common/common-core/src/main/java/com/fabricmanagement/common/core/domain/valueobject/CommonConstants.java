package com.fabricmanagement.common.core.domain.valueobject;

/**
 * Common constants used across the application.
 */
public final class CommonConstants {

    // HTTP Headers
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    public static final String HEADER_API_VERSION = "X-API-Version";

    // Default Values
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_DIRECTION = "ASC";
    public static final String DEFAULT_SORT_FIELD = "id";

    // Validation
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_DESCRIPTION_LENGTH = 500;
    public static final int MAX_EMAIL_LENGTH = 255;

    // Date Formats
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";
    public static final String ISO_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    // System Users
    public static final String SYSTEM_USER = "system";
    public static final String ANONYMOUS_USER = "anonymous";

    // Cache Names
    public static final String CACHE_USERS = "users";
    public static final String CACHE_ROLES = "roles";
    public static final String CACHE_PERMISSIONS = "permissions";

    // Error Codes
    public static final String ERROR_VALIDATION = "VALIDATION_ERROR";
    public static final String ERROR_NOT_FOUND = "NOT_FOUND";
    public static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    public static final String ERROR_BUSINESS_RULE = "BUSINESS_RULE_VIOLATION";
    public static final String ERROR_INTERNAL = "INTERNAL_SERVER_ERROR";

    private CommonConstants() {
        // Utility class
    }
}
