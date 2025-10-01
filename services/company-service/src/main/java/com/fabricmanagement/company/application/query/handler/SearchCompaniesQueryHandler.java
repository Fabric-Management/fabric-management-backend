package com.fabricmanagement.company.application.query.handler;

import com.fabricmanagement.company.application.dto.CompanyResponse;
import com.fabricmanagement.company.application.query.SearchCompaniesQuery;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query Handler for searching companies
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchCompaniesQueryHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional(readOnly = true)
    public List<CompanyResponse> handle(SearchCompaniesQuery query) {
        log.debug("Handling SearchCompaniesQuery for tenant: {} with term: {}", 
            query.getTenantId(), query.getSearchTerm());
        
        return companyRepository.searchByNameAndTenantId(query.getSearchTerm(), query.getTenantId())
            .stream()
            .map(CompanyResponse::fromEntity)
            .collect(Collectors.toList());
    }
}

