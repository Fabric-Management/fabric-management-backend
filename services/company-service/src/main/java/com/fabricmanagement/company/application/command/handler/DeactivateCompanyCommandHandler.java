package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.DeactivateCompanyCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler for deactivating a company
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeactivateCompanyCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public void handle(DeactivateCompanyCommand command) {
        log.info("Handling DeactivateCompanyCommand for company: {}", command.getCompanyId());
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(command.getCompanyId(), command.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(command.getCompanyId(), command.getTenantId()));
        
        // Deactivate company
        company.deactivate();
        
        companyRepository.save(company);
        
        log.info("Company deactivated successfully: {}", command.getCompanyId());
    }
}

