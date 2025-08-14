package com.fabricmanagement.common.core.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String[] args;

    public BusinessException(ErrorCode errorCode, String... args) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
        this.args = args;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause, String... args) {
        super(errorCode.getDefaultMessage(), cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}