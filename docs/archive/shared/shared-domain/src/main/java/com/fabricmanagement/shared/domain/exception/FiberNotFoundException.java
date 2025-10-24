package com.fabricmanagement.shared.domain.exception;

public class FiberNotFoundException extends RuntimeException {
    public FiberNotFoundException(String message) {
        super(message);
    }
}

