package com.fabricmanagement.company.application.service;

import com.fabricmanagement.company.application.dto.AddContactToCompanyRequest;
import com.fabricmanagement.company.infrastructure.client.ContactServiceClient;
import com.fabricmanagement.company.infrastructure.client.dto.ContactDto;
import com.fabricmanagement.company.infrastructure.client.dto.CreateContactDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Company Contact Service
 * 
 * Manages company contacts via Contact Service integration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompanyContactService {
    
    private final ContactServiceClient contactServiceClient;
    
    /**
     * Adds a contact to a company
     */
    @Transactional
    public UUID addContactToCompany(UUID companyId, AddContactToCompanyRequest request, 
                                    UUID tenantId, String addedBy) {
        log.info("Adding contact to company: {} by user: {}", companyId, addedBy);
        
        CreateContactDto contactRequest = CreateContactDto.builder()
            .ownerId(companyId.toString())
            .ownerType("COMPANY")
            .contactValue(request.getContactValue())
            .contactType(request.getContactType())
            .isPrimary(request.getIsPrimary())
            .autoVerified(false)
            .build();
        
        ContactDto contact = contactServiceClient.createContact(contactRequest);
        log.info("Contact created successfully for company {}: {}", companyId, contact.getId());
        
        return contact.getId();
    }
    
    /**
     * Removes a contact from a company
     */
    @Transactional
    public void removeContactFromCompany(UUID companyId, UUID contactId, 
                                        UUID tenantId, String removedBy) {
        log.info("Removing contact {} from company: {} by user: {}", contactId, companyId, removedBy);
        contactServiceClient.deleteContact(contactId);
    }
    
    /**
     * Sets a contact as primary for the company
     */
    @Transactional
    public void setPrimaryContact(UUID companyId, UUID contactId, 
                                 UUID tenantId, String updatedBy) {
        log.info("Setting contact {} as primary for company: {} by user: {}", contactId, companyId, updatedBy);
        contactServiceClient.makePrimary(contactId);
    }
    
    /**
     * Creates a new contact for a company
     */
    public ContactDto createCompanyContact(UUID companyId, String contactValue, String contactType, boolean isPrimary) {
        log.info("Creating contact for company: {}", companyId);
        
        CreateContactDto request = CreateContactDto.builder()
            .ownerId(companyId.toString())
            .ownerType("COMPANY")
            .contactValue(contactValue)
            .contactType(contactType)
            .isPrimary(isPrimary)
            .autoVerified(false)
            .build();
        
        ContactDto contact = contactServiceClient.createContact(request);
        log.info("Contact created successfully for company {}: {}", companyId, contact.getId());
        
        return contact;
    }
    
    /**
     * Gets all contacts for a company
     */
    public List<ContactDto> getCompanyContacts(UUID companyId) {
        log.debug("Getting contacts for company: {}", companyId);
        return contactServiceClient.getContactsByOwner(companyId.toString());
    }
    
    /**
     * Gets verified contacts for a company
     */
    public List<ContactDto> getVerifiedCompanyContacts(UUID companyId) {
        log.debug("Getting verified contacts for company: {}", companyId);
        return contactServiceClient.getVerifiedContacts(companyId.toString());
    }
    
    /**
     * Gets primary contact for a company
     */
    public ContactDto getPrimaryCompanyContact(UUID companyId) {
        log.debug("Getting primary contact for company: {}", companyId);
        
        try {
            return contactServiceClient.getPrimaryContact(companyId.toString());
        } catch (Exception e) {
            log.warn("No primary contact found for company: {}", companyId);
            return null;
        }
    }
    
    /**
     * Makes a contact primary for a company
     */
    public ContactDto makeContactPrimary(UUID contactId) {
        log.info("Making contact primary: {}", contactId);
        return contactServiceClient.makePrimary(contactId);
    }
    
    /**
     * Deletes a company contact
     */
    public void deleteCompanyContact(UUID contactId) {
        log.info("Deleting company contact: {}", contactId);
        contactServiceClient.deleteContact(contactId);
    }
    
    /**
     * Sends verification code to a contact
     */
    public void sendVerificationCode(UUID contactId) {
        log.info("Sending verification code for contact: {}", contactId);
        contactServiceClient.sendVerificationCode(contactId);
    }
    
    /**
     * Verifies a company contact
     */
    public ContactDto verifyContact(UUID contactId, String code) {
        log.info("Verifying contact: {}", contactId);
        return contactServiceClient.verifyContact(contactId, code);
    }
    
    /**
     * Gets a specific contact
     */
    public ContactDto getContact(UUID contactId) {
        log.debug("Getting contact: {}", contactId);
        return contactServiceClient.getContact(contactId);
    }
}

