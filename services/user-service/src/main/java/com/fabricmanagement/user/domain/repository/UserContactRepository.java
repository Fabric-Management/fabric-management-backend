package com.fabricmanagement.user.domain.repository;

import com.fabricmanagement.user.domain.model.UserContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Domain repository interface for UserContact entity.
 * Defines contract for user contact data access operations.
 */
public interface UserContactRepository {

    /**
     * Saves a user contact entity.
     */
    UserContact save(UserContact userContact);

    /**
     * Finds a user contact by ID.
     */
    Optional<UserContact> findById(UUID id);

    /**
     * Finds all contacts for a user.
     */
    List<UserContact> findByUserId(UUID userId);

    /**
     * Finds contacts by user ID and type.
     */
    List<UserContact> findByUserIdAndType(UUID userId, UserContact.ContactType type);

    /**
     * Finds primary contact for a user.
     */
    Optional<UserContact> findPrimaryContactByUserId(UUID userId);

    /**
     * Finds verified contacts for a user.
     */
    List<UserContact> findVerifiedContactsByUserId(UUID userId);

    /**
     * Finds contact by value.
     */
    Optional<UserContact> findByValue(String value);

    /**
     * Checks if contact exists by value.
     */
    boolean existsByValue(String value);

    /**
     * Checks if contact exists by value and user ID.
     */
    boolean existsByValueAndUserId(String value, UUID userId);

    /**
     * Deletes a user contact by ID.
     */
    void deleteById(UUID id);

    /**
     * Deletes all contacts for a user.
     */
    void deleteByUserId(UUID userId);

    /**
     * Finds contacts with pagination.
     */
    Page<UserContact> findAll(Pageable pageable);
}
