package com.fabricmanagement.company.application.port.in.command;

import com.fabricmanagement.company.application.dto.company.request.UpdateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;
import com.fabricmanagement.company.domain.valueobject.CompanyId;

/**
 * Port interface for updating an existing company.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface UpdateCompanyUseCase {
    
    /**
     * Updates an existing company with the provided information.
     *
     * @param companyId the company ID to update
     * @param request the company update request
     * @return the updated company response
     */
    CompanyResponse updateCompany(CompanyId companyId, UpdateCompanyRequest request);
}
