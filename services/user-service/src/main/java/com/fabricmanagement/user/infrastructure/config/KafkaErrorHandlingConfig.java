package com.fabricmanagement.user.infrastructure.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Kafka Error Handling Configuration
 * 
 * Simple DLQ pattern: 3 retries → send to {topic}.DLT
 */
@Configuration
@Slf4j
public class KafkaErrorHandlingConfig {
    
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        
        // DLQ: Failed messages → {original-topic}.DLT
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> {
                log.error("📮 DLQ: {} - Error: {}", record.topic(), ex.getMessage());
                return new TopicPartition(record.topic() + ".DLT", record.partition());
            }
        );
        
        // 3 retries with exponential backoff (1s, 2s, 4s)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        
        // Don't retry business exceptions
        handler.addNotRetryableExceptions(
            IllegalArgumentException.class,
            IllegalStateException.class
        );
        
        return handler;
    }
}

