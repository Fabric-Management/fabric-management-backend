package com.fabricmanagement.company.domain.exception;

import java.util.UUID;

/**
 * Exception thrown when a company is not found
 */
public class CompanyNotFoundException extends RuntimeException {
    
    public CompanyNotFoundException(UUID companyId) {
        super("Company not found with id: " + companyId);
    }
    
    public CompanyNotFoundException(String message) {
        super(message);
    }
}

