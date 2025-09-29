package com.fabricmanagement.company.application.port.in.query;

import com.fabricmanagement.company.application.dto.company.response.CompanyDetailResponse;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;
import com.fabricmanagement.company.domain.valueobject.CompanyId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Port interface for querying companies.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface CompanyQueryUseCase {
    
    /**
     * Retrieves a company by its ID.
     *
     * @param companyId the company ID
     * @return the company response
     */
    CompanyDetailResponse getCompanyById(CompanyId companyId);
    
    /**
     * Retrieves all companies with pagination.
     *
     * @param pageable pagination information
     * @return page of company responses
     */
    Page<CompanyResponse> getAllCompanies(Pageable pageable);
    
    /**
     * Searches companies by name.
     *
     * @param name the company name to search for
     * @return list of matching companies
     */
    List<CompanyResponse> searchCompaniesByName(String name);
    
    /**
     * Retrieves companies by status.
     *
     * @param status the company status
     * @return list of companies with the specified status
     */
    List<CompanyResponse> getCompaniesByStatus(String status);
}
