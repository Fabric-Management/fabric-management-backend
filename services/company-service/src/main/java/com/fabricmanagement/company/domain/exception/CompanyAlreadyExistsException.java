package com.fabricmanagement.company.domain.exception;

/**
 * Exception thrown when trying to create a company that already exists
 */
public class CompanyAlreadyExistsException extends RuntimeException {
    
    public CompanyAlreadyExistsException(String companyName) {
        super("Company already exists with name: " + companyName);
    }
    
    public CompanyAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}

