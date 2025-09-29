package com.fabricmanagement.company.application.port.in.command;

import com.fabricmanagement.company.application.dto.company.request.CreateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;

/**
 * Port interface for creating a new company.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface CreateCompanyUseCase {
    
    /**
     * Creates a new company with the provided information.
     *
     * @param request the company creation request
     * @return the created company response
     */
    CompanyResponse createCompany(CreateCompanyRequest request);
}
