package com.fabricmanagement.company.application.query.handler;

import com.fabricmanagement.company.application.dto.CompanyResponse;
import com.fabricmanagement.company.application.query.ListCompaniesQuery;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query Handler for listing companies
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ListCompaniesQueryHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional(readOnly = true)
    @Cacheable(value = "companiesList", key = "#query.tenantId")
    public List<CompanyResponse> handle(ListCompaniesQuery query) {
        log.debug("Handling ListCompaniesQuery for tenant: {}", query.getTenantId());
        
        return companyRepository.findByTenantId(query.getTenantId())
            .stream()
            .map(CompanyResponse::fromEntity)
            .collect(Collectors.toList());
    }
}

