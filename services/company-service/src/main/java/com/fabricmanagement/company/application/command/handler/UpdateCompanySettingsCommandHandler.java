package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.UpdateCompanySettingsCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler for updating company settings
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateCompanySettingsCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public void handle(UpdateCompanySettingsCommand command) {
        log.info("Handling UpdateCompanySettingsCommand for company: {}", command.getCompanyId());
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(command.getCompanyId(), command.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(command.getCompanyId(), command.getTenantId()));
        
        // Update settings
        company.updateSettings(command.getSettings());
        
        companyRepository.save(company);
        
        log.info("Company settings updated successfully: {}", command.getCompanyId());
    }
}

