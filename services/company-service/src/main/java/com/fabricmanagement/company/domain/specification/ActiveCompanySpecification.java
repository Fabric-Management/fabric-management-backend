package com.fabricmanagement.company.domain.specification;

import com.fabricmanagement.company.domain.model.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;

/**
 * Specification to check if a company is active.
 * This follows the Specification Pattern for business rules.
 */
public class ActiveCompanySpecification {
    
    /**
     * Checks if a company is active.
     *
     * @param company the company to check
     * @return true if company is active, false otherwise
     */
    public boolean isSatisfiedBy(Company company) {
        return company != null && CompanyStatus.ACTIVE.equals(company.getStatus());
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return "Company must be active";
    }
}
