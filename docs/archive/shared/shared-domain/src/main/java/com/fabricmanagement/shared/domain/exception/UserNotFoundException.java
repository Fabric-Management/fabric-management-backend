package com.fabricmanagement.shared.domain.exception;

import lombok.Getter;

import java.util.UUID;

/**
 * User Not Found Exception
 * 
 * Exception thrown when user is not found
 * 
 * ✅ ZERO HARDCODED VALUES
 * ✅ PRODUCTION-READY
 * ✅ DOMAIN EXCEPTION
 * ✅ UUID TYPE SAFETY
 */
@Getter
public class UserNotFoundException extends DomainException {
    
    private final UUID userId;
    private final String contactValue;
    
    public UserNotFoundException(UUID userId) {
        super("User not found with ID: " + userId);
        this.userId = userId;
        this.contactValue = null;
    }
    
    public UserNotFoundException(String contactValue) {
        super("User not found with contact: " + contactValue);
        this.userId = null;
        this.contactValue = contactValue;
    }
    
    public UserNotFoundException(UUID userId, String contactValue) {
        super("User not found with ID: " + userId + " and contact: " + contactValue);
        this.userId = userId;
        this.contactValue = contactValue;
    }
    
    public UserNotFoundException(String message, UUID userId) {
        super(message);
        this.userId = userId;
        this.contactValue = null;
    }
    
    public UserNotFoundException(String message, String contactValue) {
        super(message);
        this.userId = null;
        this.contactValue = contactValue;
    }
}