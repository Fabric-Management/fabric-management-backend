package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.UpdateCompanyCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.CompanyNotFoundException;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler for updating company information
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateCompanyCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public void handle(UpdateCompanyCommand command) {
        log.info("Handling UpdateCompanyCommand for company: {}", command.getCompanyId());
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(command.getCompanyId(), command.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(command.getCompanyId(), command.getTenantId()));
        
        // Update company information
        company.updateCompany(
            command.getLegalName() != null ? command.getLegalName() : company.getLegalName(),
            command.getDescription() != null ? command.getDescription() : company.getDescription(),
            command.getWebsite() != null ? command.getWebsite() : company.getWebsite()
        );
        
        if (command.getLogoUrl() != null) {
            company.updateLogo(command.getLogoUrl());
        }
        
        companyRepository.save(company);
        
        log.info("Company updated successfully: {}", command.getCompanyId());
    }
}

