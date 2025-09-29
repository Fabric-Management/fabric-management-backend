package com.fabricmanagement.contact.application.port.in.command;

import com.fabricmanagement.contact.application.dto.contact.request.CreateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.request.UpdateContactRequest;
import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;

import java.util.UUID;

/**
 * Port interface for contact management operations.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface ContactManagementUseCase {
    
    /**
     * Creates a new contact.
     *
     * @param request the contact creation request
     * @return the created contact response
     */
    ContactResponse createContact(CreateContactRequest request);
    
    /**
     * Updates an existing contact.
     *
     * @param contactId the contact ID
     * @param request the contact update request
     * @return the updated contact response
     */
    ContactResponse updateContact(UUID contactId, UpdateContactRequest request);
    
    /**
     * Deletes a contact.
     *
     * @param contactId the contact ID
     */
    void deleteContact(UUID contactId);
    
    /**
     * Activates a contact.
     *
     * @param contactId the contact ID
     */
    void activateContact(UUID contactId);
    
    /**
     * Deactivates a contact.
     *
     * @param contactId the contact ID
     */
    void deactivateContact(UUID contactId);
}

