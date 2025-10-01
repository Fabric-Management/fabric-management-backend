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
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isDeleted = false")
    List<Contact> findByOwnerId(@Param("ownerId") String ownerId);
    
    /**
     * Finds contacts by owner ID and type (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.ownerType = :ownerType AND c.isDeleted = false")
    List<Contact> findByOwnerIdAndOwnerType(@Param("ownerId") String ownerId, @Param("ownerType") Contact.OwnerType ownerType);
    
    /**
     * Finds contact by value (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.contactValue = :contactValue AND c.isDeleted = false")
    Optional<Contact> findByContactValue(@Param("contactValue") String contactValue);
    
    /**
     * Checks if contact value exists (excluding deleted)
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Contact c WHERE c.contactValue = :contactValue AND c.isDeleted = false")
    boolean existsByContactValue(@Param("contactValue") String contactValue);
    
    /**
     * Finds verified contacts for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true AND c.isDeleted = false")
    List<Contact> findVerifiedContactsByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds primary contact for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isPrimary = true AND c.isDeleted = false")
    Optional<Contact> findPrimaryContactByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds contacts by type for an owner (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.contactType = :type AND c.isDeleted = false")
    List<Contact> findByOwnerAndType(@Param("ownerId") String ownerId, @Param("type") ContactType type);
    
    /**
     * Counts verified contacts for an owner (excluding deleted)
     */
    @Query("SELECT COUNT(c) FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true AND c.isDeleted = false")
    long countVerifiedContactsByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds all primary contacts (excluding deleted)
     */
    @Query("SELECT c FROM Contact c WHERE c.isPrimary = true AND c.isDeleted = false")
    List<Contact> findAllPrimaryContacts();
    
    /**
     * Updates all contacts to non-primary for an owner
     */
    @Modifying
    @Transactional
    @Query("UPDATE Contact c SET c.isPrimary = false WHERE c.ownerId = :ownerId")
    void removePrimaryStatusForOwner(@Param("ownerId") String ownerId);
}

