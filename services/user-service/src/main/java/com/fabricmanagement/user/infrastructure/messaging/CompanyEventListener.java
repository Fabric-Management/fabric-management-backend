package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.messaging.event.CompanyCreatedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.CompanyDeletedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.CompanyUpdatedEvent;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Company Event Listener
 * 
 * Listens to events from Company Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CompanyEventListener {
    
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    
    /**
     * Handles generic company events and routes to specific handlers
     */
    @KafkaListener(topics = "company-events", groupId = "user-service", 
                   containerFactory = "kafkaListenerContainerFactory")
    public void handleCompanyEvent(String event) {
        log.info("📬 Company event received: {}", event);
        
        try {
            // Parse event JSON
            @SuppressWarnings("unchecked")
            Map<String, Object> eventData = objectMapper.readValue(event, Map.class);
            String eventType = (String) eventData.get("eventType");
            
            if (eventType == null) {
                log.warn("⚠️ Event type is null, cannot route event");
                return;
            }
            
            // Route to specific handler based on event type
            switch (eventType) {
                case "CompanyCreated":
                case "COMPANY_CREATED":
                    handleCompanyCreatedFromMap(eventData);
                    break;
                case "CompanyUpdated":
                case "COMPANY_UPDATED":
                    handleCompanyUpdatedFromMap(eventData);
                    break;
                case "CompanyDeleted":
                case "COMPANY_DELETED":
                    handleCompanyDeletedFromMap(eventData);
                    break;
                default:
                    log.warn("⚠️ Unknown company event type: {}", eventType);
            }
            
            log.info("✅ Company event processed");
        } catch (Exception e) {
            log.error("❌ Error processing company event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to handle company created from map
     */
    private void handleCompanyCreatedFromMap(Map<String, Object> eventData) {
        try {
            CompanyCreatedEvent event = objectMapper.convertValue(
                eventData.get("data"), 
                CompanyCreatedEvent.class
            );
            handleCompanyCreated(event);
        } catch (Exception e) {
            log.error("❌ Error parsing company created event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to handle company updated from map
     */
    private void handleCompanyUpdatedFromMap(Map<String, Object> eventData) {
        try {
            CompanyUpdatedEvent event = objectMapper.convertValue(
                eventData.get("data"), 
                CompanyUpdatedEvent.class
            );
            handleCompanyUpdated(event);
        } catch (Exception e) {
            log.error("❌ Error parsing company updated event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Helper method to handle company deleted from map
     */
    private void handleCompanyDeletedFromMap(Map<String, Object> eventData) {
        try {
            CompanyDeletedEvent event = objectMapper.convertValue(
                eventData.get("data"), 
                CompanyDeletedEvent.class
            );
            handleCompanyDeleted(event);
        } catch (Exception e) {
            log.error("❌ Error parsing company deleted event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company created event
     * Logs company creation for audit trail
     */
    public void handleCompanyCreated(CompanyCreatedEvent event) {
        log.info("📬 Company created event received: companyId={}, name={}", 
            event.getCompanyId(), event.getName());
        
        try {
            // Log for audit trail
            log.info("📝 Audit: Company created - ID: {}, Name: {}, TenantID: {}", 
                event.getCompanyId(), 
                event.getName(),
                event.getTenantId());
            
            // Note: Company-user relationship will be handled by Company Service
            // User Service just needs to be aware of company creation
            
            log.info("✅ Company creation processed: {}", event.getCompanyId());
        } catch (Exception e) {
            log.error("❌ Error processing company created event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company updated event
     * Logs company updates for audit trail
     */
    public void handleCompanyUpdated(CompanyUpdatedEvent event) {
        log.info("📬 Company updated event received: companyId={}", event.getCompanyId());
        
        try {
            // Log for audit trail
            log.info("📝 Audit: Company updated - ID: {}", event.getCompanyId());
            
            // Note: User Service doesn't cache company data
            // If caching is implemented in the future, invalidate cache here
            
            log.info("✅ Company update processed: {}", event.getCompanyId());
        } catch (Exception e) {
            log.error("❌ Error processing company updated event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles company deleted event
     * Deactivates users belonging to the deleted company
     */
    @Transactional
    public void handleCompanyDeleted(CompanyDeletedEvent event) {
        log.info("📬 Company deleted event received: companyId={}", event.getCompanyId());
        
        try {
            UUID companyId = event.getCompanyId();
            UUID tenantId = event.getTenantId();
            
            // Log for audit trail
            log.info("📝 Audit: Company deleted - ID: {}, TenantID: {}", companyId, tenantId);
            
            // Find all users in this tenant
            // Note: In a real implementation with company-user relationship table,
            // we would query by companyId. For now, we use tenantId as proxy.
            List<User> users = userRepository.findByTenantId(tenantId);
            
            if (users.isEmpty()) {
                log.info("ℹ️ No users found for company {}", companyId);
                return;
            }
            
            // Deactivate all active users
            int deactivatedCount = 0;
            for (User user : users) {
                if (user.getStatus() == UserStatus.ACTIVE || 
                    user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                    
                    // Deactivate user (soft delete)
                    user.deactivate();
                    userRepository.save(user);
                    deactivatedCount++;
                    
                    log.debug("🔒 User {} deactivated due to company deletion", user.getId());
                }
            }
            
            log.info("✅ Company deletion processed: {} - Deactivated {} users", 
                companyId, deactivatedCount);
                
        } catch (Exception e) {
            log.error("❌ Error processing company deleted event: {}", e.getMessage(), e);
            // Don't throw to avoid message reprocessing
            // Consider implementing compensation logic or manual intervention
        }
    }
}

