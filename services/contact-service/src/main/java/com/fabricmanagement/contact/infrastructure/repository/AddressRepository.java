package com.fabricmanagement.contact.infrastructure.repository;

import com.fabricmanagement.contact.domain.entity.Address;
import com.fabricmanagement.contact.domain.aggregate.Contact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Address Repository
 * 
 * Handles persistence for Address entities
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {

    /**
     * Finds address by contact ID
     * 
     * @param contactId Contact ID
     * @return Address if found
     */
    @Query("SELECT a FROM Address a WHERE a.contactId = :contactId AND a.deleted = false")
    Optional<Address> findByContactId(@Param("contactId") UUID contactId);

    /**
     * Finds all addresses for an owner (USER or COMPANY)
     * 
     * @param ownerId Owner ID (User ID or Company ID)
     * @param ownerType Owner type (USER or COMPANY)
     * @return List of addresses
     */
    @Query("SELECT a FROM Address a WHERE a.ownerId = :ownerId AND a.ownerType = :ownerType AND a.deleted = false")
    List<Address> findByOwner(@Param("ownerId") UUID ownerId, @Param("ownerType") Contact.OwnerType ownerType);

    /**
     * Finds primary address for an owner
     * 
     * @param ownerId Owner ID
     * @param ownerType Owner type
     * @return Primary address if found
     */
    @Query("SELECT a FROM Address a WHERE a.ownerId = :ownerId AND a.ownerType = :ownerType AND a.isPrimary = true AND a.deleted = false")
    Optional<Address> findPrimaryByOwner(@Param("ownerId") UUID ownerId, @Param("ownerType") Contact.OwnerType ownerType);

    /**
     * Finds addresses by country
     * Useful for company duplicate checks (same legal name + country)
     * 
     * @param country Country name
     * @return List of addresses in that country
     */
    @Query("SELECT a FROM Address a WHERE LOWER(a.country) = LOWER(:country) AND a.deleted = false")
    List<Address> findByCountry(@Param("country") String country);

    /**
     * Checks if owner has any address
     * 
     * @param ownerId Owner ID
     * @param ownerType Owner type
     * @return true if address exists
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM Address a WHERE a.ownerId = :ownerId AND a.ownerType = :ownerType AND a.deleted = false")
    boolean existsByOwner(@Param("ownerId") UUID ownerId, @Param("ownerType") Contact.OwnerType ownerType);

    /**
     * Removes primary flag from all addresses of an owner
     * Used before setting new primary
     * 
     * @param ownerId Owner ID
     * @param ownerType Owner type
     */
    @Query("UPDATE Address a SET a.isPrimary = false WHERE a.ownerId = :ownerId AND a.ownerType = :ownerType")
    void removePrimaryStatusForOwner(@Param("ownerId") UUID ownerId, @Param("ownerType") Contact.OwnerType ownerType);
}

