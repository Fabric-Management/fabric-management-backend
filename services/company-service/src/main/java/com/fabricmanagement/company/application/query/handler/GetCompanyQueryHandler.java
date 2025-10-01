package com.fabricmanagement.company.application.query.handler;

import com.fabricmanagement.company.application.dto.CompanyResponse;
import com.fabricmanagement.company.application.query.GetCompanyQuery;
import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.exception.UnauthorizedCompanyAccessException;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Query Handler for getting a single company
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetCompanyQueryHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional(readOnly = true)
    @Cacheable(value = "companies", key = "#query.companyId")
    public CompanyResponse handle(GetCompanyQuery query) {
        log.debug("Handling GetCompanyQuery for company: {}", query.getCompanyId());
        
        Company company = companyRepository.findByIdAndTenantId(query.getCompanyId(), query.getTenantId())
            .orElseThrow(() -> new UnauthorizedCompanyAccessException(query.getCompanyId(), query.getTenantId()));
        
        return CompanyResponse.fromEntity(company);
    }
}

