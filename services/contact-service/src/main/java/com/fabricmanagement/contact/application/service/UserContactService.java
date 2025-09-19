package com.fabricmanagement.contact.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for managing user contacts.
 * 
 * NOTE: User contact management has been moved to identity-service.
 * This service is kept for backward compatibility but delegates to identity-service.
 * 
 * @deprecated Use identity-service UserContactService instead
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class UserContactService {
    
    /**
     * This service has been deprecated.
     * User contact management is now handled by identity-service.
     * 
     * Please use:
     * - identity-service UserContactService for user contact operations
     * - identity-service UserContactController for REST endpoints
     */
    public void deprecated() {
        log.warn("UserContactService in contact-service is deprecated. Use identity-service instead.");
    }
}