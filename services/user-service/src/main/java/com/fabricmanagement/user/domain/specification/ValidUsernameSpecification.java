package com.fabricmanagement.user.domain.specification;

import com.fabricmanagement.user.domain.model.User;

import java.util.regex.Pattern;

/**
 * Specification to check if a user has a valid username.
 * This follows the Specification Pattern for business rules.
 */
public class ValidUsernameSpecification {
    
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{3,20}$");
    
    /**
     * Checks if a user has a valid username format.
     *
     * @param user the user to check
     * @return true if username format is valid, false otherwise
     */
    public boolean isSatisfiedBy(User user) {
        if (user == null || user.getUsername() == null) {
            return false;
        }
        
        String username = user.getUsername().trim();
        return username.length() >= 3 && 
               username.length() <= 20 && 
               USERNAME_PATTERN.matcher(username).matches();
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return "Username must be 3-20 characters long and contain only letters, numbers, and underscores";
    }
}
