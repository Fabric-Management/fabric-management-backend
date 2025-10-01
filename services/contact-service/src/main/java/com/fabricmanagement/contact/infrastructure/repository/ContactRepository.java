package com.fabricmanagement.contact.infrastructure.repository;

import com.fabricmanagement.contact.domain.aggregate.Contact;
import com.fabricmanagement.contact.domain.valueobject.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
     * Finds contacts by owner ID
     */
    List<Contact> findByOwnerId(String ownerId);
    
    /**
     * Finds contacts by owner ID and type
     */
    List<Contact> findByOwnerIdAndOwnerType(String ownerId, Contact.OwnerType ownerType);
    
    /**
     * Finds contact by value
     */
    Optional<Contact> findByContactValue(String contactValue);
    
    /**
     * Checks if contact value exists
     */
    boolean existsByContactValue(String contactValue);
    
    /**
     * Finds verified contacts for an owner
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true")
    List<Contact> findVerifiedContactsByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds primary contact for an owner
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.isPrimary = true")
    Optional<Contact> findPrimaryContactByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds contacts by type for an owner
     */
    @Query("SELECT c FROM Contact c WHERE c.ownerId = :ownerId AND c.contactType = :type")
    List<Contact> findByOwnerAndType(@Param("ownerId") String ownerId, @Param("type") ContactType type);
    
    /**
     * Counts verified contacts for an owner
     */
    @Query("SELECT COUNT(c) FROM Contact c WHERE c.ownerId = :ownerId AND c.isVerified = true")
    long countVerifiedContactsByOwner(@Param("ownerId") String ownerId);
    
    /**
     * Finds all primary contacts
     */
    @Query("SELECT c FROM Contact c WHERE c.isPrimary = true")
    List<Contact> findAllPrimaryContacts();
    
    /**
     * Updates all contacts to non-primary for an owner
     */
    @Query("UPDATE Contact c SET c.isPrimary = false WHERE c.ownerId = :ownerId")
    void removePrimaryStatusForOwner(@Param("ownerId") String ownerId);
}

