package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.Contact;
import com.fabricmanagement.common.platform.communication.domain.ContactType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Contact entity.
 */
@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

    /**
     * Find contact by value and type within tenant.
     */
    @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId " +
           "AND c.contactValue = :contactValue AND c.contactType = :contactType")
    Optional<Contact> findByTenantIdAndContactValueAndContactType(
            @Param("tenantId") UUID tenantId,
            @Param("contactValue") String contactValue,
            @Param("contactType") ContactType contactType);

    /**
     * Find all contacts by type within tenant.
     */
    @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId AND c.contactType = :contactType")
    List<Contact> findByTenantIdAndContactType(
            @Param("tenantId") UUID tenantId,
            @Param("contactType") ContactType contactType);

    /**
     * Find all extensions for a parent phone contact.
     */
    @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId " +
           "AND c.contactType = 'PHONE_EXTENSION' AND c.parentContactId = :parentContactId")
    List<Contact> findExtensionsByParentContactId(
            @Param("tenantId") UUID tenantId,
            @Param("parentContactId") UUID parentContactId);

    /**
     * Find primary contacts by type within tenant.
     */
    @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId " +
           "AND c.contactType = :contactType AND c.isPrimary = true")
    List<Contact> findPrimaryByTenantIdAndContactType(
            @Param("tenantId") UUID tenantId,
            @Param("contactType") ContactType contactType);

    /**
     * Find verified contacts by type within tenant.
     */
    @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId " +
           "AND c.contactType = :contactType AND c.isVerified = true")
    List<Contact> findVerifiedByTenantIdAndContactType(
            @Param("tenantId") UUID tenantId,
            @Param("contactType") ContactType contactType);
}

