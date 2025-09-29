package com.fabricmanagement.company.application.port.in.command;

import com.fabricmanagement.company.domain.valueobject.CompanyId;

/**
 * Port interface for deleting a company.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface DeleteCompanyUseCase {
    
    /**
     * Deletes a company by its ID.
     *
     * @param companyId the company ID to delete
     */
    void deleteCompany(CompanyId companyId);
}
