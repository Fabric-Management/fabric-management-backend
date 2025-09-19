package com.fabricmanagement.contact.infrastructure.persistence.entity;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * UserContactEntity for managing user contact information.
 * 
 * NOTE: User contact management has been moved to identity-service.
 * This entity is kept for backward compatibility but is deprecated.
 * 
 * @deprecated Use identity-service UserContact entity instead
 */
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class UserContactEntity {
    
    /**
     * This entity has been deprecated.
     * User contact management is now handled by identity-service.
     * 
     * Please use:
     * - identity-service UserContact entity
     * - identity-service UserContactService for business logic
     */
    public void deprecated() {
        log.warn("UserContactEntity in contact-service is deprecated. Use identity-service instead.");
    }
}