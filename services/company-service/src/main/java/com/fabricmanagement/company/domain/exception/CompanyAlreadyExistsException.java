package com.fabricmanagement.company.domain.exception;

import lombok.Getter;

/**
 * Exception thrown when trying to create a company that already exists
 * 
 * Provides detailed information about what caused the duplicate detection
 */
@Getter
public class CompanyAlreadyExistsException extends RuntimeException {
    
    private final String matchType;
    private final String matchedValue;
    
    /**
     * Legacy constructor for simple name-based duplicates
     */
    public CompanyAlreadyExistsException(String companyName) {
        super("Company already exists with name: " + companyName);
        this.matchType = "NAME";
        this.matchedValue = companyName;
    }
    
    /**
     * Detailed constructor with match type information
     * 
     * @param matchType Type of match (TAX_ID, REGISTRATION_NUMBER, NAME_EXACT, etc.)
     * @param matchedValue The value that matched
     * @param message User-friendly error message
     */
    public CompanyAlreadyExistsException(String matchType, String matchedValue, String message) {
        super(message);
        this.matchType = matchType;
        this.matchedValue = matchedValue;
    }
    
    public CompanyAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
        this.matchType = "UNKNOWN";
        this.matchedValue = null;
    }
}

