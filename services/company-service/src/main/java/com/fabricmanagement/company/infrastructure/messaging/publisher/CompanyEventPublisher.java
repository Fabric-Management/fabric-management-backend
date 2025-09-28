package com.fabricmanagement.company.infrastructure.messaging.publisher;

import com.fabricmanagement.company.domain.event.CompanyCreatedEvent;
import com.fabricmanagement.company.domain.event.CompanyDeletedEvent;
import com.fabricmanagement.company.domain.event.CompanyUpdatedEvent;
import com.fabricmanagement.company.domain.model.Company;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Event publisher for company domain events.
 * Note: Kafka dependencies are not available, using simple logging for now.
 * TODO: Implement proper event publishing when Kafka is available.
 */
@Component
@Slf4j
public class CompanyEventPublisher {

    private static final String COMPANY_CREATED_TOPIC = "company.created";
    private static final String COMPANY_UPDATED_TOPIC = "company.updated";
    private static final String COMPANY_DELETED_TOPIC = "company.deleted";

    /**
     * Publishes a company created event.
     */
    public void publishCompanyCreatedEvent(Company company) {
        try {
            CompanyCreatedEvent event = CompanyCreatedEvent.builder()
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .companyName(company.getCompanyName())
                .industry(company.getIndustry())
                .companyType(company.getCompanyType())
                .status(company.getStatus())
                .createdAt(company.getCreatedAt())
                .build();

            // TODO: Replace with actual Kafka publishing when dependency is available
            log.info("Publishing company created event to topic '{}': {}", COMPANY_CREATED_TOPIC, event);
            // kafkaTemplate.send(COMPANY_CREATED_TOPIC, company.getId().toString(), event);

        } catch (Exception e) {
            log.error("Failed to publish company created event for company ID: {}", company.getId(), e);
            // Don't throw exception - event publishing failures shouldn't fail the business operation
        }
    }

    /**
     * Publishes a company updated event.
     */
    public void publishCompanyUpdatedEvent(Company company) {
        try {
            CompanyUpdatedEvent event = CompanyUpdatedEvent.builder()
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .companyName(company.getCompanyName())
                .industry(company.getIndustry())
                .companyType(company.getCompanyType())
                .status(company.getStatus())
                .updatedAt(company.getUpdatedAt())
                .build();

            // TODO: Replace with actual Kafka publishing when dependency is available
            log.info("Publishing company updated event to topic '{}': {}", COMPANY_UPDATED_TOPIC, event);
            // kafkaTemplate.send(COMPANY_UPDATED_TOPIC, company.getId().toString(), event);

        } catch (Exception e) {
            log.error("Failed to publish company updated event for company ID: {}", company.getId(), e);
            // Don't throw exception - event publishing failures shouldn't fail the business operation
        }
    }

    /**
     * Publishes a company deleted event.
     */
    public void publishCompanyDeletedEvent(Company company) {
        try {
            CompanyDeletedEvent event = CompanyDeletedEvent.builder()
                .companyId(company.getId())
                .tenantId(company.getTenantId())
                .companyName(company.getCompanyName())
                .deletedAt(company.getUpdatedAt())
                .build();

            // TODO: Replace with actual Kafka publishing when dependency is available
            log.info("Publishing company deleted event to topic '{}': {}", COMPANY_DELETED_TOPIC, event);
            // kafkaTemplate.send(COMPANY_DELETED_TOPIC, company.getId().toString(), event);

        } catch (Exception e) {
            log.error("Failed to publish company deleted event for company ID: {}", company.getId(), e);
            // Don't throw exception - event publishing failures shouldn't fail the business operation
        }
    }
}