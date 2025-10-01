package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.DeleteCompanyCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler for deleting a company (soft delete)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteCompanyCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public void handle(DeleteCompanyCommand command) {
        log.info("Handling DeleteCompanyCommand for company: {}", command.getCompanyId());
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(command.getCompanyId(), command.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(command.getCompanyId(), command.getTenantId()));
        
        // Soft delete
        company.markAsDeleted();
        
        companyRepository.save(company);
        
        log.info("Company deleted successfully: {}", command.getCompanyId());
    }
}

