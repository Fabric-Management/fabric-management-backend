package com.fabricmanagement.user.domain.specification;

import com.fabricmanagement.common.core.domain.specification.Specification;
import com.fabricmanagement.user.domain.model.User;

/**
 * Specification to check if a user has required fields.
 * This follows the Specification Pattern for business rules.
 */
public class UserRequiredFieldsSpecification implements Specification<User> {
    
    /**
     * Checks if a user has all required fields.
     *
     * @param user the user to check
     * @return true if all required fields are present, false otherwise
     */
    @Override
    public boolean isSatisfiedBy(User user) {
        if (user == null) {
            return false;
        }
        
        return user.getUsername() != null && !user.getUsername().trim().isEmpty() &&
               user.getEmail() != null && !user.getEmail().trim().isEmpty() &&
               user.getFirstName() != null && !user.getFirstName().trim().isEmpty() &&
               user.getLastName() != null && !user.getLastName().trim().isEmpty();
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    @Override
    public String getErrorMessage() {
        return "User must have username, email, first name, and last name";
    }
}
