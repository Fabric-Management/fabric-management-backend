package com.fabricmanagement.user.infrastructure.messaging;

import com.fabricmanagement.user.application.service.UserApplicationService;
import com.fabricmanagement.user.domain.model.User;
import com.fabricmanagement.user.domain.model.UserStatus;
import com.fabricmanagement.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Event listener for handling events from Identity Service.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityEventConsumer {

    private final UserRepository userRepository;
    private final UserApplicationService userApplicationService;

    @KafkaListener(topics = "user.created", groupId = "user-service")
    @Transactional
    public void handleUserCreated(@Payload Map<String, Object> eventData,
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 Acknowledgment acknowledgment) {
        try {
            log.info("Received user created event from topic: {}", topic);
            
            String userId = (String) eventData.get("userId");
            String tenantId = (String) eventData.get("tenantId");
            String username = (String) eventData.get("username");
            
            log.info("Creating user profile for user: {} in tenant: {}", userId, tenantId);
            
            // Create user profile in User Service
            User user = User.builder()
                .id(UUID.fromString(userId))
                .tenantId(UUID.fromString(tenantId))
                .username(username)
                .status(UserStatus.ACTIVE)
                .build();
            
            userRepository.save(user);
            
            log.info("User profile created successfully for user: {}", userId);
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing user created event: {}", e.getMessage(), e);
            // Don't acknowledge on error - message will be retried
        }
    }

    @KafkaListener(topics = "user.profile.updated", groupId = "user-service")
    @Transactional
    public void handleUserProfileUpdated(@Payload Map<String, Object> eventData,
                                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                        Acknowledgment acknowledgment) {
        try {
            log.info("Received user profile updated event from topic: {}", topic);
            
            String userId = (String) eventData.get("userId");
            
            log.info("Processing profile update for user: {}", userId);
            
            // Find user and update last modified timestamp
            UUID userUuid = UUID.fromString(userId);
            userRepository.findById(userUuid).ifPresent(user -> {
                user.setUpdatedAt(java.time.LocalDateTime.now());
                userRepository.save(user);
                log.info("User profile updated timestamp refreshed for user: {}", userId);
            });
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing user profile updated event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.suspended", groupId = "user-service")
    @Transactional
    public void handleUserSuspended(@Payload Map<String, Object> eventData,
                                   @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                   Acknowledgment acknowledgment) {
        try {
            log.info("Received user suspended event from topic: {}", topic);
            
            String userId = (String) eventData.get("userId");
            String reason = (String) eventData.get("reason");
            
            log.info("Suspending user profile for user: {} with reason: {}", userId, reason);
            
            // Find user and suspend
            UUID userUuid = UUID.fromString(userId);
            userRepository.findById(userUuid).ifPresent(user -> {
                user.setStatus(UserStatus.SUSPENDED);
                user.setUpdatedAt(java.time.LocalDateTime.now());
                userRepository.save(user);
                log.info("User profile suspended for user: {}", userId);
            });
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing user suspended event: {}", e.getMessage(), e);
        }
    }

    @KafkaListener(topics = "user.reactivated", groupId = "user-service")
    @Transactional
    public void handleUserReactivated(@Payload Map<String, Object> eventData,
                                     @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                     Acknowledgment acknowledgment) {
        try {
            log.info("Received user reactivated event from topic: {}", topic);
            
            String userId = (String) eventData.get("userId");
            
            log.info("Reactivating user profile for user: {}", userId);
            
            // Find user and reactivate
            UUID userUuid = UUID.fromString(userId);
            userRepository.findById(userUuid).ifPresent(user -> {
                user.setStatus(UserStatus.ACTIVE);
                user.setUpdatedAt(java.time.LocalDateTime.now());
                userRepository.save(user);
                log.info("User profile reactivated for user: {}", userId);
            });
            
            // Acknowledge the message
            acknowledgment.acknowledge();
            
        } catch (Exception e) {
            log.error("Error processing user reactivated event: {}", e.getMessage(), e);
        }
    }
}
