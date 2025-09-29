package com.fabricmanagement.user.domain.specification;

import com.fabricmanagement.user.domain.model.User;

import java.util.regex.Pattern;

/**
 * Specification to check if a user has valid email format.
 * This follows the Specification Pattern for business rules.
 */
public class ValidEmailSpecification {
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$"
    );
    
    /**
     * Checks if a user has a valid email format.
     *
     * @param user the user to check
     * @return true if email format is valid, false otherwise
     */
    public boolean isSatisfiedBy(User user) {
        if (user == null || user.getEmail() == null) {
            return false;
        }
        
        return EMAIL_PATTERN.matcher(user.getEmail()).matches();
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return "User must have a valid email format";
    }
}
