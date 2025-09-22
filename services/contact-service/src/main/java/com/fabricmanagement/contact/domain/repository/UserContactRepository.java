package com.fabricmanagement.contact.domain.repository;

import com.fabricmanagement.contact.domain.model.UserContact;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for UserContact domain entities.
 * This is a domain interface - implementations should handle persistence details.
 */
public interface UserContactRepository {

    /**
     * Saves a user contact.
     */
    UserContact save(UserContact userContact);

    /**
     * Finds a user contact by its ID.
     */
    Optional<UserContact> findById(UUID id);

    /**
     * Finds a user contact by user ID.
     */
    Optional<UserContact> findByUserId(UUID userId);

    /**
     * Finds all user contacts by tenant ID.
     */
    List<UserContact> findByTenantId(UUID tenantId);

    /**
     * Checks if a contact exists for the given user ID.
     */
    boolean existsByUserId(UUID userId);

    /**
     * Searches user contacts by query string.
     */
    List<UserContact> searchByQuery(String query, UUID tenantId);

    /**
     * Finds all active user contacts.
     */
    List<UserContact> findActiveContacts(UUID tenantId);

    /**
     * Finds user contacts by preferred contact method.
     */
    List<UserContact> findByPreferredContactMethod(String method, UUID tenantId);

    /**
     * Finds user contacts with public profiles.
     */
    List<UserContact> findPublicProfiles(UUID tenantId);

    /**
     * Finds user contacts that allow direct messages.
     */
    List<UserContact> findAllowingDirectMessages(UUID tenantId);

    /**
     * Deletes a user contact by ID.
     */
    void deleteById(UUID id);
}