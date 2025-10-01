package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.infrastructure.messaging.event.CompanyCreatedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.CompanyDeletedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.CompanyUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Company Event Listener
 * 
 * Listens to events from Company Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventListener {
    
    /**
     * Handles company created event
     */
    @KafkaListener(topics = "company-events", groupId = "user-service", 
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleCompanyEvent(String event) {
        log.info("📬 Company event received: {}", event);
        
        try {
            // TODO: Parse event and determine type
            // For now, just log it
            log.info("✅ Company event processed");
        } catch (Exception e) {
            log.error("❌ Error processing company event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company created event
     */
    public void handleCompanyCreated(CompanyCreatedEvent event) {
        log.info("📬 Company created event received: companyId={}, name={}", 
            event.getCompanyId(), event.getName());
        
        try {
            // TODO: Update user-company relationships if needed
            // Maybe send notification to company admin
            log.info("✅ Company creation processed: {}", event.getCompanyId());
        } catch (Exception e) {
            log.error("❌ Error processing company created event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company updated event
     */
    public void handleCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("📬 Company updated event received: companyId={}", event.getCompanyId());
        
        try {
            // TODO: Update cached company data if needed
            log.info("✅ Company update processed: {}", event.getCompanyId());
        } catch (Exception e) {
            log.error("❌ Error processing company updated event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company deleted event
     */
    public void handleCompanyDeleted(CompanyDeletedEvent event) {
        log.info("📬 Company deleted event received: companyId={}", event.getCompanyId());
        
        try {
            // TODO: Handle user cleanup when company is deleted
            // Maybe deactivate users or move them to another company
            log.info("✅ Company deletion processed: {}", event.getCompanyId());
        } catch (Exception e) {
            log.error("❌ Error processing company deleted event: {}", e.getMessage(), e);
        }
    }
}

