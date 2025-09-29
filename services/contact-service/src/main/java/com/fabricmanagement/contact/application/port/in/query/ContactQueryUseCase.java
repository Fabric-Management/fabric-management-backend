package com.fabricmanagement.contact.application.port.in.query;

import com.fabricmanagement.contact.application.dto.contact.response.ContactResponse;
import com.fabricmanagement.contact.application.dto.common.PageResponse;

import java.util.List;
import java.util.UUID;

/**
 * Port interface for contact query operations.
 * This follows Hexagonal Architecture principles as an inbound port.
 */
public interface ContactQueryUseCase {
    
    /**
     * Finds a contact by ID.
     *
     * @param contactId the contact ID
     * @return the contact response
     */
    ContactResponse findById(UUID contactId);
    
    /**
     * Finds contacts by name.
     *
     * @param name the contact name
     * @return list of matching contacts
     */
    List<ContactResponse> findByName(String name);
    
    /**
     * Finds contacts by email.
     *
     * @param email the email address
     * @return list of matching contacts
     */
    List<ContactResponse> findByEmail(String email);
    
    /**
     * Finds contacts by phone.
     *
     * @param phone the phone number
     * @return list of matching contacts
     */
    List<ContactResponse> findByPhone(String phone);
    
    /**
     * Finds all contacts with pagination.
     *
     * @param page the page number
     * @param size the page size
     * @return page response with contacts
     */
    PageResponse<ContactResponse> findAll(int page, int size);
    
    /**
     * Searches contacts by query string.
     *
     * @param query the search query
     * @param page the page number
     * @param size the page size
     * @return page response with matching contacts
     */
    PageResponse<ContactResponse> searchContacts(String query, int page, int size);
}

