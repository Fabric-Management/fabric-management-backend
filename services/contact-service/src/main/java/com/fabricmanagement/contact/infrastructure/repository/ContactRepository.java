package com.fabricmanagement.contact.infrastructure.repository;

import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Contact Repository
 * 
 * Handles database operations for contacts
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {
    
    /**
     * Finds contacts by owner ID (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.deleted = false")
    List<Contact> findByOwnerId(@Param("ownerId") UUID ownerId);

    /**
     * Finds contacts by owner ID and type (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.ownerType = :ownerType AND c.deleted = false")
    List<Contact> findByOwnerIdAndOwnerType(@Param("ownerId") UUID ownerId, @Param("ownerType") Contact.OwnerType ownerType);

    /**
     * Finds contact by value (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.contactValue = :contactValue AND c.deleted = false")
    Optional<Contact> findByContactValue(@Param("contactValue") String contactValue);

    /**
     * Checks if contact value exists (excluding deleted)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Contact c WHERE c.contactValue = :contactValue AND c.deleted = false")
    boolean existsByContactValue(@Param("contactValue") String contactValue);

    /**
     * Finds verified contacts for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true AND c.deleted = false")
    List<Contact> findVerifiedContactsByOwner(@Param("ownerId") UUID ownerId);

    /**
     * Finds primary contact for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isPrimary = true AND c.deleted = false")
    Optional<Contact> findPrimaryContactByOwner(@Param("ownerId") UUID ownerId);

    /**
     * Finds contacts by owner ID and contact type (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.contactType = :contactType AND c.deleted = false")
    List<Contact> findByOwnerIdAndContactType(@Param("ownerId") UUID ownerId, @Param("contactType") String contactType);

    /**
     * Finds contacts by type for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.contactType = :type AND c.deleted = false")
    List<Contact> findByOwnerAndType(@Param("ownerId") UUID ownerId, @Param("type") ContactType type);

    /**
     * Counts verified contacts for an owner (excluding deleted)
     */
    @Query("SELECT COUNT(c) FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true AND c.deleted = false")
    long countVerifiedContactsByOwner(@Param("ownerId") UUID ownerId);

    /**
     * Finds all primary contacts (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.isPrimary = true AND c.deleted = false")
    List<Contact> findAllPrimaryContacts();
    
    /**
     * Updates all contacts to non-primary for an owner
     */
    @Modifying
    @Transactional
    @Query("UPDATE Contact c SET c.isPrimary = false WHERE c.ownerId = :ownerId")
    void removePrimaryStatusForOwner(@Param("ownerId") UUID ownerId);
    
    /**
     * Finds contacts by multiple owner IDs (batch query)
     * 
     * NEW: Added for batch fetching to prevent N+1 query problem
     * Performance: 100 owners = 1 query instead of 100 queries
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId IN :ownerIds AND c.deleted = false")
    List<Contact> findByOwnerIdIn(@Param("ownerIds") List<UUID> ownerIds);
}

