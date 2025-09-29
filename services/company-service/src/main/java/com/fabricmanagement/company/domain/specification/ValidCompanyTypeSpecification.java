package com.fabricmanagement.company.domain.specification;

import com.fabricmanagement.company.domain.model.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyType;

/**
 * Specification to check if a company has a valid type.
 * This follows the Specification Pattern for business rules.
 */
public class ValidCompanyTypeSpecification {
    
    /**
     * Checks if a company has a valid type.
     *
     * @param company the company to check
     * @return true if company type is valid, false otherwise
     */
    public boolean isSatisfiedBy(Company company) {
        if (company == null || company.getCompanyType() == null) {
            return false;
        }
        
        CompanyType type = company.getCompanyType();
        return CompanyType.CORPORATION.equals(type) ||
               CompanyType.LLC.equals(type) ||
               CompanyType.PARTNERSHIP.equals(type) ||
               CompanyType.SOLE_PROPRIETORSHIP.equals(type);
    }
    
    /**
     * Gets the error message when specification is not satisfied.
     *
     * @return error message
     */
    public String getErrorMessage() {
        return "Company must have a valid type (CORPORATION, LLC, PARTNERSHIP, SOLE_PROPRIETORSHIP)";
    }
}
