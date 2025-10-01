package com.fabricmanagement.company.application.command.handler;

import com.fabricmanagement.company.application.command.UpdateSubscriptionCommand;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Command Handler for updating company subscription
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UpdateSubscriptionCommandHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional
    public void handle(UpdateSubscriptionCommand command) {
        log.info("Handling UpdateSubscriptionCommand for company: {}", command.getCompanyId());
        
        // Find company with tenant validation
        Company company = companyRepository.findByIdAndTenantId(command.getCompanyId(), command.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(command.getCompanyId(), command.getTenantId()));
        
        // Update subscription
        company.updateSubscription(command.getPlan(), command.getMaxUsers(), command.getEndDate());
        
        companyRepository.save(company);
        
        log.info("Company subscription updated successfully: {}", command.getCompanyId());
    }
}

