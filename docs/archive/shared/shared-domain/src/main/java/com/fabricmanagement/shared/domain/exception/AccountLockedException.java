package com.fabricmanagement.shared.domain.exception;

/**
 * Exception thrown when account is locked due to too many failed attempts
 */
public class AccountLockedException extends DomainException {
    
    private static final String ERROR_CODE = "ACCOUNT_LOCKED";

    private final int remainingMinutes;

    public AccountLockedException(String contactValue, int remainingMinutes) {
        super(String.format("Account locked due to too many failed login attempts. Please try again in %d minutes.", remainingMinutes), 
              ERROR_CODE, contactValue, remainingMinutes);
        this.remainingMinutes = remainingMinutes;
    }

    public int getRemainingMinutes() {
        return remainingMinutes;
    }
}
