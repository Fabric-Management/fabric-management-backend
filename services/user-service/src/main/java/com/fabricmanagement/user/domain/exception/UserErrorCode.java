package com.fabricmanagement.user.domain.exception;

/**
 * Error codes for user-related operations.
 */
public enum UserErrorCode {
    
    USER_NOT_FOUND("USER_001", "User not found"),
    USER_ALREADY_EXISTS("USER_002", "User already exists"),
    INVALID_USER_STATE("USER_003", "Invalid user state"),
    USER_PROFILE_INCOMPLETE("USER_004", "User profile is incomplete"),
    DUPLICATE_IDENTITY_ID("USER_005", "Duplicate identity ID"),
    INVALID_TENANT("USER_006", "Invalid tenant"),
    USER_ALREADY_ACTIVE("USER_007", "User is already active"),
    USER_ALREADY_INACTIVE("USER_008", "User is already inactive"),
    USER_ALREADY_SUSPENDED("USER_009", "User is already suspended");
    
    private final String code;
    private final String message;
    
    UserErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public String getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
    
    @Override
    public String toString() {
        return code + ": " + message;
    }
}
