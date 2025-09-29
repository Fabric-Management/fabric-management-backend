package com.fabricmanagement.user.domain.specification;

import com.fabricmanagement.user.domain.model.User;

/**
 * Composite specification that combines multiple user specifications.
 * This follows the Specification Pattern for complex business rules.
 */
public class UserValidationSpecification {
    
    private final UserRequiredFieldsSpecification requiredFieldsSpec;
    private final ValidUsernameSpecification validUsernameSpec;
    private final ValidEmailSpecification validEmailSpec;
    private final ActiveUserSpecification activeUserSpec;
    
    public UserValidationSpecification() {
        this.requiredFieldsSpec = new UserRequiredFieldsSpecification();
        this.validUsernameSpec = new ValidUsernameSpecification();
        this.validEmailSpec = new ValidEmailSpecification();
        this.activeUserSpec = new ActiveUserSpecification();
    }
    
    /**
     * Validates a user against all specifications.
     *
     * @param user the user to validate
     * @return true if all specifications are satisfied, false otherwise
     */
    public boolean isSatisfiedBy(User user) {
        return requiredFieldsSpec.isSatisfiedBy(user) &&
               validUsernameSpec.isSatisfiedBy(user) &&
               validEmailSpec.isSatisfiedBy(user) &&
               activeUserSpec.isSatisfiedBy(user);
    }
    
    /**
     * Gets the first error message from unsatisfied specifications.
     *
     * @param user the user to validate
     * @return error message or null if all specifications are satisfied
     */
    public String getFirstErrorMessage(User user) {
        if (!requiredFieldsSpec.isSatisfiedBy(user)) {
            return requiredFieldsSpec.getErrorMessage();
        }
        if (!validUsernameSpec.isSatisfiedBy(user)) {
            return validUsernameSpec.getErrorMessage();
        }
        if (!validEmailSpec.isSatisfiedBy(user)) {
            return validEmailSpec.getErrorMessage();
        }
        if (!activeUserSpec.isSatisfiedBy(user)) {
            return activeUserSpec.getErrorMessage();
        }
        return null;
    }
    
    /**
     * Validates a user and throws exception if validation fails.
     *
     * @param user the user to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(User user) {
        String errorMessage = getFirstErrorMessage(user);
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
