package com.fabricmanagement.identity.infrastructure.web.controller;

import com.fabricmanagement.identity.application.service.UserContactService;
import com.fabricmanagement.identity.domain.model.UserContact;
import com.fabricmanagement.identity.domain.valueobject.ContactStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for user contact management.
 */
@RestController
@RequestMapping("/api/user-contacts")
@RequiredArgsConstructor
@Slf4j
public class UserContactController {
    
    private final UserContactService userContactService;
    
    /**
     * Creates a new user contact.
     */
    @PostMapping
    public ResponseEntity<UserContact> createUserContact(@RequestBody UserContact userContact) {
        log.info("Creating user contact for user: {}", userContact.getUserId());
        
        UserContact createdContact = userContactService.createUserContact(userContact);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdContact);
    }
    
    /**
     * Updates an existing user contact.
     */
    @PutMapping("/{contactId}")
    public ResponseEntity<UserContact> updateUserContact(
            @PathVariable Long contactId,
            @RequestBody UserContact updatedContact) {
        log.info("Updating user contact: {}", contactId);
        
        UserContact contact = userContactService.updateUserContact(UUID.fromString(contactId.toString()), updatedContact);
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Gets user contact by ID.
     */
    @GetMapping("/{contactId}")
    public ResponseEntity<UserContact> getUserContactById(@PathVariable Long contactId) {
        log.info("Fetching user contact by ID: {}", contactId);
        
        return userContactService.getUserContactById(UUID.fromString(contactId.toString()))
            .map(contact -> ResponseEntity.ok(contact))
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets user contacts by user ID.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserContact>> getUserContactsByUserId(@PathVariable UUID userId) {
        log.info("Fetching user contacts for user: {}", userId);
        
        List<UserContact> contacts = userContactService.getUserContactsByUserId(userId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets user contacts by tenant ID.
     */
    @GetMapping("/tenant/{tenantId}")
    public ResponseEntity<List<UserContact>> getUserContactsByTenantId(@PathVariable UUID tenantId) {
        log.info("Fetching user contacts for tenant: {}", tenantId);
        
        List<UserContact> contacts = userContactService.getUserContactsByTenantId(tenantId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Deletes a user contact.
     */
    @DeleteMapping("/{contactId}")
    public ResponseEntity<Void> deleteUserContact(@PathVariable Long contactId) {
        log.info("Deleting user contact: {}", contactId);
        
        userContactService.deleteUserContact(UUID.fromString(contactId.toString()));
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Activates a user contact.
     */
    @PostMapping("/{contactId}/activate")
    public ResponseEntity<UserContact> activateUserContact(@PathVariable Long contactId) {
        log.info("Activating user contact: {}", contactId);
        
        UserContact contact = userContactService.activateUserContact(UUID.fromString(contactId.toString()));
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Deactivates a user contact.
     */
    @PostMapping("/{contactId}/deactivate")
    public ResponseEntity<UserContact> deactivateUserContact(@PathVariable Long contactId) {
        log.info("Deactivating user contact: {}", contactId);
        
        UserContact contact = userContactService.deactivateUserContact(UUID.fromString(contactId.toString()));
        return ResponseEntity.ok(contact);
    }
}
