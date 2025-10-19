package com.fabricmanagement.shared.infrastructure.constants;

/**
 * Kafka Topic Constants
 * 
 * Centralized topic definitions for event-driven communication.
 * 
 * MANIFESTO Compliance:
 * ✅ ZERO HARDCODED - All topics in one place
 * ✅ DRY - Single source of truth
 * ✅ Production-Ready - Easy to change for different environments
 * 
 * @since 3.4.0
 */
public final class KafkaTopics {
    
    private KafkaTopics() {
        // Prevent instantiation
    }
    
    // ========== Tenant Events ==========
    public static final String TENANT_EVENTS = "tenant-events";
    
    // ========== Company Events ==========
    public static final String COMPANY_EVENTS = "company-events";
    
    // ========== User Events ==========
    public static final String USER_CREATED = "user.created";
    public static final String USER_UPDATED = "user.updated";
    public static final String USER_DELETED = "user.deleted";
    
    // ========== Contact Events ==========
    public static final String CONTACT_EVENTS = "contact-events";
    
    // ========== Notification Events ==========
    public static final String NOTIFICATION_EVENTS = "notification-events";
}

