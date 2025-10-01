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
        
        // Create company aggregate
        Company company = Company.create(
            command.getTenantId(),
            new CompanyName(command.getName()),
            command.getLegalName(),
            CompanyType.valueOf(command.getType()),
            Industry.valueOf(command.getIndustry()),
            command.getDescription()
        );
        
        // Set additional fields
        if (command.getTaxId() != null) {
            company.updateCompany(command.getLegalName(), command.getDescription(), command.getWebsite());
        }
        
        if (command.getWebsite() != null || command.getLogoUrl() != null) {
            company.updateCompany(
                command.getLegalName() != null ? command.getLegalName() : company.getLegalName(),
                command.getDescription() != null ? command.getDescription() : company.getDescription(),
                command.getWebsite()
            );
        }
        
        if (command.getLogoUrl() != null) {
            company.updateLogo(command.getLogoUrl());
        }
        
        // Save company
        Company savedCompany = companyRepository.save(company);
        
        log.info("Company created successfully with id: {}", savedCompany.getId());
        return savedCompany.getId();
    }
}

