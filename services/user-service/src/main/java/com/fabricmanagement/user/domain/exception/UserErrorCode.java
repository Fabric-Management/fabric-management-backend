package com.fabricmanagement.user.domain.exception;

import com.fabricmanagement.common.core.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND("USER_001", "User not found"),
    USER_ALREADY_EXISTS("USER_002", "User already exists"),
    USER_CANNOT_BE_ACTIVATED("USER_003", "User can only be activated from PENDING status"),
    USER_CANNOT_BE_DEACTIVATED("USER_004", "Only active users can be deactivated"),
    INVALID_USER_STATUS("USER_005", "Invalid user status transition"),
    USERNAME_ALREADY_EXISTS("USER_006", "Username already exists"),
    TENANT_NOT_FOUND("USER_007", "Tenant not found"),
    UNAUTHORIZED_TENANT_ACCESS("USER_008", "Unauthorized tenant access");

    private final String code;
    private final String defaultMessage;
}