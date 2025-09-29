package com.fabricmanagement.company.domain.specification;

import com.fabricmanagement.company.domain.model.Company;

/**
 * Composite specification that combines multiple company specifications.
 * This follows the Specification Pattern for complex business rules.
 */
public class CompanyValidationSpecification {
    
    private final ActiveCompanySpecification activeCompanySpec;
    private final ValidCompanyTypeSpecification validTypeSpec;
    private final CompanyRequiredFieldsSpecification requiredFieldsSpec;
    
    public CompanyValidationSpecification() {
        this.activeCompanySpec = new ActiveCompanySpecification();
        this.validTypeSpec = new ValidCompanyTypeSpecification();
        this.requiredFieldsSpec = new CompanyRequiredFieldsSpecification();
    }
    
    /**
     * Validates a company against all specifications.
     *
     * @param company the company to validate
     * @return true if all specifications are satisfied, false otherwise
     */
    public boolean isSatisfiedBy(Company company) {
        return requiredFieldsSpec.isSatisfiedBy(company) &&
               validTypeSpec.isSatisfiedBy(company) &&
               activeCompanySpec.isSatisfiedBy(company);
    }
    
    /**
     * Gets the first error message from unsatisfied specifications.
     *
     * @param company the company to validate
     * @return error message or null if all specifications are satisfied
     */
    public String getFirstErrorMessage(Company company) {
        if (!requiredFieldsSpec.isSatisfiedBy(company)) {
            return requiredFieldsSpec.getErrorMessage();
        }
        if (!validTypeSpec.isSatisfiedBy(company)) {
            return validTypeSpec.getErrorMessage();
        }
        if (!activeCompanySpec.isSatisfiedBy(company)) {
            return activeCompanySpec.getErrorMessage();
        }
        return null;
    }
    
    /**
     * Validates a company and throws exception if validation fails.
     *
     * @param company the company to validate
     * @throws IllegalArgumentException if validation fails
     */
    public void validate(Company company) {
        String errorMessage = getFirstErrorMessage(company);
        if (errorMessage != null) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
