package com.fabricmanagement.company.domain.exception;

import java.util.UUID;

/**
 * Exception thrown when a user tries to access a company they don't have access to
 */
public class UnauthorizedCompanyAccessException extends RuntimeException {
    
    public UnauthorizedCompanyAccessException(UUID companyId, UUID tenantId) {
        super("Unauthorized access to company " + companyId + " for tenant " + tenantId);
    }
    
    public UnauthorizedCompanyAccessException(String message) {
        super(message);
    }
}

