package com.fabricmanagement.company.api;

import com.fabricmanagement.company.application.service.CompanyContactService;
import com.fabricmanagement.company.infrastructure.client.dto.ContactDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

/**
 * Company Contact REST Controller
 * 
 * Provides API endpoints for managing company contacts
 */
@RestController
@RequestMapping("/api/companies/{companyId}/contacts")
@RequiredArgsConstructor
@Slf4j
public class CompanyContactController {
    
    private final CompanyContactService companyContactService;
    
    /**
     * Creates a new contact for a company
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactDto> createCompanyContact(
            @PathVariable UUID companyId,
            @Valid @RequestBody CreateCompanyContactRequest request) {
        
        log.info("Creating contact for company: {}", companyId);
        
        ContactDto contact = companyContactService.createCompanyContact(
            companyId,
            request.getContactValue(),
            request.getContactType(),
            request.isPrimary()
        );
        
        return ResponseEntity.status(HttpStatus.CREATED).body(contact);
    }
    
    /**
     * Gets all contacts for a company
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ContactDto>> getCompanyContacts(@PathVariable UUID companyId) {
        log.debug("Getting contacts for company: {}", companyId);
        List<ContactDto> contacts = companyContactService.getCompanyContacts(companyId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets verified contacts for a company
     */
    @GetMapping("/verified")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ContactDto>> getVerifiedContacts(@PathVariable UUID companyId) {
        log.debug("Getting verified contacts for company: {}", companyId);
        List<ContactDto> contacts = companyContactService.getVerifiedCompanyContacts(companyId);
        return ResponseEntity.ok(contacts);
    }
    
    /**
     * Gets primary contact for a company
     */
    @GetMapping("/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactDto> getPrimaryContact(@PathVariable UUID companyId) {
        log.debug("Getting primary contact for company: {}", companyId);
        ContactDto contact = companyContactService.getPrimaryCompanyContact(companyId);
        
        if (contact == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Gets a specific contact
     */
    @GetMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactDto> getContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.debug("Getting contact {} for company: {}", contactId, companyId);
        ContactDto contact = companyContactService.getContact(contactId);
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Makes a contact primary
     */
    @PutMapping("/{contactId}/primary")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactDto> makePrimary(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.info("Making contact {} primary for company: {}", contactId, companyId);
        ContactDto contact = companyContactService.makeContactPrimary(contactId);
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Sends verification code to a contact
     */
    @PostMapping("/{contactId}/send-verification")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> sendVerificationCode(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.info("Sending verification code for contact {} of company: {}", contactId, companyId);
        companyContactService.sendVerificationCode(contactId);
        return ResponseEntity.ok().build();
    }
    
    /**
     * Verifies a contact
     */
    @PutMapping("/{contactId}/verify")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ContactDto> verifyContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId,
            @Valid @RequestBody VerifyContactRequest request) {
        
        log.info("Verifying contact {} for company: {}", contactId, companyId);
        ContactDto contact = companyContactService.verifyContact(contactId, request.getCode());
        return ResponseEntity.ok(contact);
    }
    
    /**
     * Deletes a contact
     */
    @DeleteMapping("/{contactId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteContact(
            @PathVariable UUID companyId,
            @PathVariable UUID contactId) {
        
        log.info("Deleting contact {} for company: {}", contactId, companyId);
        companyContactService.deleteCompanyContact(contactId);
        return ResponseEntity.noContent().build();
    }
    
    // DTOs
    
    /**
     * Create Company Contact Request DTO
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CreateCompanyContactRequest {
        @NotBlank(message = "Contact value is required")
        private String contactValue;
        
        @NotBlank(message = "Contact type is required")
        private String contactType; // EMAIL, PHONE, ADDRESS, etc.
        
        private boolean isPrimary = false;
    }
    
    /**
     * Verify Contact Request DTO
     */
    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class VerifyContactRequest {
        @NotBlank(message = "Verification code is required")
        private String code;
    }
}

