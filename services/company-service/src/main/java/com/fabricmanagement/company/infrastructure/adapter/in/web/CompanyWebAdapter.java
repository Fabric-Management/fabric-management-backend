package com.fabricmanagement.company.infrastructure.adapter.in.web;

import com.fabricmanagement.company.application.port.in.command.CreateCompanyUseCase;
import com.fabricmanagement.company.application.port.in.command.DeleteCompanyUseCase;
import com.fabricmanagement.company.application.port.in.command.UpdateCompanyUseCase;
import com.fabricmanagement.company.application.port.in.query.CompanyQueryUseCase;
import com.fabricmanagement.company.application.dto.company.request.CreateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.request.UpdateCompanyRequest;
import com.fabricmanagement.company.application.dto.company.response.CompanyDetailResponse;
import com.fabricmanagement.company.application.dto.company.response.CompanyResponse;
import com.fabricmanagement.company.domain.valueobject.CompanyId;
import com.fabricmanagement.company.infrastructure.web.controller.CompanyController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter implementation for web layer operations.
 * This follows the Adapter Pattern to adapt web controllers to application ports.
 */
@Component
public class CompanyWebAdapter {
    
    private final CreateCompanyUseCase createCompanyUseCase;
    private final UpdateCompanyUseCase updateCompanyUseCase;
    private final DeleteCompanyUseCase deleteCompanyUseCase;
    private final CompanyQueryUseCase companyQueryUseCase;
    
    public CompanyWebAdapter(CreateCompanyUseCase createCompanyUseCase,
                            UpdateCompanyUseCase updateCompanyUseCase,
                            DeleteCompanyUseCase deleteCompanyUseCase,
                            CompanyQueryUseCase companyQueryUseCase) {
        this.createCompanyUseCase = createCompanyUseCase;
        this.updateCompanyUseCase = updateCompanyUseCase;
        this.deleteCompanyUseCase = deleteCompanyUseCase;
        this.companyQueryUseCase = companyQueryUseCase;
    }
    
    /**
     * Creates a new company through web interface.
     */
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        return createCompanyUseCase.createCompany(request);
    }
    
    /**
     * Updates an existing company through web interface.
     */
    public CompanyResponse updateCompany(String companyId, UpdateCompanyRequest request) {
        CompanyId id = new CompanyId(companyId);
        return updateCompanyUseCase.updateCompany(id, request);
    }
    
    /**
     * Deletes a company through web interface.
     */
    public void deleteCompany(String companyId) {
        CompanyId id = new CompanyId(companyId);
        deleteCompanyUseCase.deleteCompany(id);
    }
    
    /**
     * Retrieves a company by ID through web interface.
     */
    public CompanyDetailResponse getCompanyById(String companyId) {
        CompanyId id = new CompanyId(companyId);
        return companyQueryUseCase.getCompanyById(id);
    }
    
    /**
     * Retrieves all companies with pagination through web interface.
     */
    public Page<CompanyResponse> getAllCompanies(Pageable pageable) {
        return companyQueryUseCase.getAllCompanies(pageable);
    }
    
    /**
     * Searches companies by name through web interface.
     */
    public List<CompanyResponse> searchCompaniesByName(String name) {
        return companyQueryUseCase.searchCompaniesByName(name);
    }
    
    /**
     * Retrieves companies by status through web interface.
     */
    public List<CompanyResponse> getCompaniesByStatus(String status) {
        return companyQueryUseCase.getCompaniesByStatus(status);
    }
}
