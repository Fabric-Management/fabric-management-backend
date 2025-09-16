package com.fabricmanagement.company.domain.exception;

/**
 * Exception thrown when attempting to create a company that already exists.
 */
public class DuplicateCompanyException extends RuntimeException {

    public DuplicateCompanyException(String message) {
        super(message);
    }

    public DuplicateCompanyException(String message, Throwable cause) {
        super(message, cause);
    }

    public static DuplicateCompanyException withName(String companyName) {
        return new DuplicateCompanyException("Company with name '" + companyName + "' already exists");
    }

    public static DuplicateCompanyException withEmail(String email) {
        return new DuplicateCompanyException("Company with email '" + email + "' already exists");
    }
}
