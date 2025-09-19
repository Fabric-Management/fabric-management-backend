package com.fabricmanagement.contact.infrastructure.web.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user contact management.
 * 
 * NOTE: User contact management has been moved to identity-service.
 * This controller is kept for backward compatibility but redirects to identity-service.
 * 
 * @deprecated Use identity-service UserContactController instead
 */
@RestController
@RequestMapping("/api/user-contacts")
@RequiredArgsConstructor
@Slf4j
@Deprecated
public class UserContactController {
    
    /**
     * This controller has been deprecated.
     * User contact management is now handled by identity-service.
     * 
     * Please use:
     * - identity-service UserContactController for REST endpoints
     * - identity-service UserContactService for business logic
     */
    @GetMapping("/deprecated")
    public ResponseEntity<String> deprecated() {
        log.warn("UserContactController in contact-service is deprecated. Use identity-service instead.");
        return ResponseEntity.status(HttpStatus.GONE)
            .body("User contact management has been moved to identity-service. Please use identity-service endpoints.");
    }
}