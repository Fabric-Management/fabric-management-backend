package com.fabricmanagement.company.application.mapper;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import org.springframework.stereotype.Component;

/**
 * Mapper for Company domain events
 * 
 * Handles Entity → Event transformations
 * Follows SRP: Only event mapping, no business logic
 */
@Component
public class CompanyEventMapper {

    /**
     * Creates CompanyCreatedEvent from Company entity
     */
    public CompanyCreatedEvent toCreatedEvent(Company company) {
        return new CompanyCreatedEvent(
                company.getId(),
                company.getTenantId(),
                company.getName().getValue(),
                company.getType().toString(),
                company.getIndustry().toString()
        );
    }

    /**
     * Creates CompanyUpdatedEvent from Company entity
     */
    public CompanyUpdatedEvent toUpdatedEvent(Company company) {
        return new CompanyUpdatedEvent(
                company.getId(),
                company.getTenantId(),
                company.getName().getValue(),
                company.getType().toString()
        );
    }

    /**
     * Creates CompanyDeletedEvent from Company entity
     */
    public CompanyDeletedEvent toDeletedEvent(Company company) {
        return new CompanyDeletedEvent(
                company.getId(),
                company.getTenantId(),
                company.getName().getValue(),
                company.getType().toString()
        );
    }
}

