package com.fabricmanagement.company.domain.exception;

import java.util.UUID;

/**
 * Exception thrown when company reaches maximum user limit
 */
public class MaxUsersLimitException extends RuntimeException {
    
    public MaxUsersLimitException(UUID companyId, int maxUsers) {
        super("Company " + companyId + " has reached maximum user limit: " + maxUsers);
    }
    
    public MaxUsersLimitException(String message) {
        super(message);
    }
}

