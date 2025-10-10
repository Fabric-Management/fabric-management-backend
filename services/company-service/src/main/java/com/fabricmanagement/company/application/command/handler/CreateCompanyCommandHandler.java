package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.CreateCompanyCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.CompanyAlreadyExistsException;
import com.fabricmanagement.company.domain.valueobject.CompanyName;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Command Handler for creating a new company
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateCompanyCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public UUID handle(CreateCompanyCommand command) {
        log.info("Handling CreateCompanyCommand for tenant: {}", command.getTenantId());
        
        // Check if company already exists with the same name
        if (companyRepository.existsByNameAndTenantId(command.getName(), command.getTenantId())) {
            throw new CompanyAlreadyExistsException(command.getName());
        }
        
        // ✅ CORRECT: Use factory method (includes validation + domain event)
        Company company = Company.create(
            command.getTenantId(),
            new CompanyName(command.getName()),
            command.getLegalName(),
            CompanyType.valueOf(command.getType()),
            Industry.valueOf(command.getIndustry()),
            command.getDescription()
        );
        
        // Set registration details (tax ID, registration number)
        if (command.getTaxId() != null || command.getRegistrationNumber() != null) {
            company.setRegistrationDetails(command.getTaxId(), command.getRegistrationNumber());
        }
        
        // Set website and logo
        if (command.getWebsite() != null) {
            company.updateCompany(company.getLegalName(), company.getDescription(), command.getWebsite());
        }
        
        if (command.getLogoUrl() != null) {
            company.updateLogo(command.getLogoUrl());
        }
        
        // Configure policy fields (businessType, parentCompany, relationship)
        if (command.getBusinessType() != null) {
            com.fabricmanagement.shared.domain.policy.CompanyType businessType = 
                com.fabricmanagement.shared.domain.policy.CompanyType.valueOf(command.getBusinessType());
            company.configurePolicyFields(
                businessType,
                command.getParentCompanyId(),
                command.getRelationshipType()
            );
        }
        
        // Save company
        Company savedCompany = companyRepository.save(company);
        
        log.info("Company created successfully with id: {}", savedCompany.getId());
        return savedCompany.getId();
    }
}

