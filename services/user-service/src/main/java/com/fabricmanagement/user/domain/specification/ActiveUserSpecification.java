package com.fabricmanagement.user.domain.specification;

import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;

/**
 * Specification to check if a user is active.
 * This follows the Specification Pattern for business rules.
 */
public class ActiveUserSpecification {
    
    /**
     * Checks if a user is active.
     *
     * @param user the user to check
     * @return true if user is active, false otherwise
     */
    public boolean isSatisfiedBy(User user) {
        return user != null && UserStatus.ACTIVE.equals(user.getStatus());
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return "User must be active";
    }
}
