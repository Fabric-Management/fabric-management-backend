package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.infrastructure.messaging.event.ContactVerifiedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.ContactCreatedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.ContactDeletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Contact Event Listener
 * 
 * Listens to events from Contact Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactEventListener {
    
    /**
     * Handles contact verified event
     */
    @KafkaListener(topics = "contact.verified", groupId = "user-service")
    public void handleContactVerified(ContactVerifiedEvent event) {
        log.info("📬 Contact verified event received: ownerId={}, contactValue={}", 
            event.getOwnerId(), event.getContactValue());
        
        try {
            // TODO: Update user status if needed
            // If user has pending verification, activate them
            log.info("✅ Contact verification processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact verified event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles contact created event
     */
    @KafkaListener(topics = "contact.created", groupId = "user-service")
    public void handleContactCreated(ContactCreatedEvent event) {
        log.info("📬 Contact created event received: ownerId={}, contactType={}", 
            event.getOwnerId(), event.getContactType());
        
        try {
            // TODO: Log or track contact creation
            log.info("✅ Contact creation processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact created event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles contact deleted event
     */
    @KafkaListener(topics = "contact.deleted", groupId = "user-service")
    public void handleContactDeleted(ContactDeletedEvent event) {
        log.info("📬 Contact deleted event received: ownerId={}", event.getOwnerId());
        
        try {
            // TODO: Handle contact deletion if needed
            log.info("✅ Contact deletion processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact deleted event: {}", e.getMessage(), e);
        }
    }
}
