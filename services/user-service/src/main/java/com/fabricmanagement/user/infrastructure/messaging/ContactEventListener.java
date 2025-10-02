package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.domain.aggregate.User;
import com.fabricmanagement.user.domain.valueobject.UserStatus;
import com.fabricmanagement.user.infrastructure.messaging.event.ContactVerifiedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.ContactCreatedEvent;
import com.fabricmanagement.user.infrastructure.messaging.event.ContactDeletedEvent;
import com.fabricmanagement.user.infrastructure.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Contact Event Listener
 * 
 * Listens to events from Contact Service
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ContactEventListener {
    
    private final UserRepository userRepository;
    
    /**
     * Handles contact verified event
     * Automatically activates users who are pending verification
     */
    @KafkaListener(topics = "contact.verified", groupId = "user-service")
    @Transactional
    public void handleContactVerified(ContactVerifiedEvent event) {
        log.info("📬 Contact verified event received: ownerId={}, contactValue={}", 
            event.getOwnerId(), event.getContactValue());
        
        try {
            // Parse UUID safely
            UUID userId;
            try {
                userId = UUID.fromString(event.getOwnerId());
            } catch (IllegalArgumentException e) {
                log.error("❌ Invalid UUID format for ownerId: {}", event.getOwnerId());
                return;
            }
            
            // Find user by ID
            User user = userRepository.findById(userId).orElse(null);
            
            if (user == null) {
                log.warn("⚠️ User not found for contact verification: {}", userId);
                return;
            }
            
            // Check if user is pending verification
            if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                // Activate user
                user.verifyContactAndActivate(event.getContactValue());
                userRepository.save(user);
                
                log.info("✅ User {} automatically activated after contact verification", userId);
            } else {
                log.debug("ℹ️ User {} status is {}, no activation needed", 
                    userId, user.getStatus());
            }
            
            log.info("✅ Contact verification processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact verified event: {}", e.getMessage(), e);
            // Don't throw exception to avoid message reprocessing
            // Consider implementing dead letter queue for failed events
        }
    }
    
    /**
     * Handles contact created event
     * Logs contact creation for audit trail
     */
    @KafkaListener(topics = "contact.created", groupId = "user-service")
    public void handleContactCreated(ContactCreatedEvent event) {
        log.info("📬 Contact created event received: ownerId={}, contactType={}", 
            event.getOwnerId(), event.getContactType());
        
        try {
            // Log for audit trail
            log.info("📝 Audit: Contact created - Owner: {}, Type: {}, Value: {}", 
                event.getOwnerId(), 
                event.getContactType(), 
                event.getContactValue());
            
            log.info("✅ Contact creation processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact created event: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles contact deleted event
     * Logs contact deletion for audit trail
     */
    @KafkaListener(topics = "contact.deleted", groupId = "user-service")
    public void handleContactDeleted(ContactDeletedEvent event) {
        log.info("📬 Contact deleted event received: ownerId={}", event.getOwnerId());
        
        try {
            // Log for audit trail
            log.info("📝 Audit: Contact deleted - Owner: {}, Contact ID: {}", 
                event.getOwnerId(), 
                event.getContactId());
            
            // Note: If this was user's primary contact, they may need to be deactivated
            // This would require additional business logic based on requirements
            
            log.info("✅ Contact deletion processed for owner: {}", event.getOwnerId());
        } catch (Exception e) {
            log.error("❌ Error processing contact deleted event: {}", e.getMessage(), e);
        }
    }
}
