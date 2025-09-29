package com.fabricmanagement.company.domain.specification;

import com.fabricmanagement.common.core.domain.specification.Specification;
import com.fabricmanagement.company.domain.model.Company;

/**
 * Specification to check if a company has required fields.
 * This follows the Specification Pattern for business rules.
 */
public class CompanyRequiredFieldsSpecification implements Specification<Company> {
    
    /**
     * Checks if a company has all required fields.
     *
     * @param company the company to check
     * @return true if all required fields are present, false otherwise
     */
    @Override
    public boolean isSatisfiedBy(Company company) {
        if (company == null) {
            return false;
        }
        
        return company.getCompanyName() != null && !company.getCompanyName().trim().isEmpty() &&
               company.getLegalName() != null && !company.getLegalName().trim().isEmpty() &&
               company.getTaxNumber() != null && !company.getTaxNumber().trim().isEmpty();
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    @Override
    public String getErrorMessage() {
        return "Company must have company name, legal name, and tax number";
    }
}
