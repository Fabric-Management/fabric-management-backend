package com.fabricmanagement.shared.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Base Kafka Error Handling Configuration - SHARED
 * 
 * Standard error handling for ALL microservices using Kafka.
 * Provides:
 * - Dead Letter Queue (DLQ) pattern
 * - Exponential backoff retry
 * - Business exception handling
 * 
 * DLQ Pattern:
 * - Failed messages → {original-topic}.DLT
 * - 3 retries with exponential backoff (1s, 2s, 4s)
 * - Business exceptions NOT retried
 * 
 * Configuration:
 * - Auto-configured when Kafka is on classpath
 * - Override in service-specific config if needed
 * 
 * @author Fabric Management Team
 * @since 3.0 (Clean Architecture Refactor)
 */
@Configuration
@Slf4j
@ConditionalOnClass(KafkaTemplate.class)
public class BaseKafkaErrorConfig {
    
    /**
     * Default error handler for Kafka listeners
     * 
     * Pattern:
     * 1. Retry 3 times with exponential backoff
     * 2. If still failing → Send to DLT (Dead Letter Topic)
     * 3. Business exceptions → No retry
     * 
     * DLT naming: {original-topic}.DLT
     * Example: user.created → user.created.DLT
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        
        // Dead Letter Queue: Failed messages → {topic}.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("📮 DLQ: Message sent to {}.DLT - Error: {}", 
                    record.topic(), ex.getMessage());
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            }
        );
        
        // Exponential backoff: 1s → 2s → 4s (3 retries total)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);   // 1 second
        backOff.setMultiplier(2.0);          // 2x each retry
        backOff.setMaxInterval(10000L);      // Max 10 seconds
        
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        
        // Business exceptions should NOT be retried
        errorHandler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            IllegalStateException.class,
            NullPointerException.class
        );
        
        log.info("✅ Kafka error handler configured: 3 retries, exponential backoff, DLT enabled");
        
        return errorHandler;
    }
}

