package com.fabricmanagement.company.application.query.handler;

import com.fabricmanagement.company.application.dto.CompanyResponse;
import com.fabricmanagement.company.application.query.GetCompaniesByStatusQuery;
import com.fabricmanagement.company.infrastructure.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Query Handler for getting companies by status
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetCompaniesByStatusQueryHandler {
    
    private final CompanyRepository companyRepository;
    
    @Transactional(readOnly = true)
    public List<CompanyResponse> handle(GetCompaniesByStatusQuery query) {
        log.debug("Handling GetCompaniesByStatusQuery for tenant: {} with status: {}", 
            query.getTenantId(), query.getStatus());
        
        return companyRepository.findByStatusAndTenantId(query.getStatus(), query.getTenantId())
            .stream()
            .map(CompanyResponse::fromEntity)
            .collect(Collectors.toList());
    }
}

