package com.fabricmanagement.platform.communication.infra.repository;

import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for Contact entity. */
@Repository
public interface ContactRepository extends JpaRepository<Contact, UUID> {

  /** Find contact by value and type within tenant. */
  @Query(
      "SELECT c FROM Contact c WHERE c.tenantId = :tenantId "
          + "AND c.contactValue = :contactValue AND c.contactType = :contactType")
  Optional<Contact> findByTenantIdAndContactValueAndContactType(
      @Param("tenantId") UUID tenantId,
      @Param("contactValue") String contactValue,
      @Param("contactType") ContactType contactType);

  @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId AND c.contactValue = :contactValue")
  Optional<Contact> findByTenantIdAndContactValue(
      @Param("tenantId") UUID tenantId, @Param("contactValue") String contactValue);

  /** Find all contacts by type within tenant. */
  @Query("SELECT c FROM Contact c WHERE c.tenantId = :tenantId AND c.contactType = :contactType")
  List<Contact> findByTenantIdAndContactType(
      @Param("tenantId") UUID tenantId, @Param("contactType") ContactType contactType);

  /** Find all extensions for a parent phone contact. */
  @Query(
      "SELECT c FROM Contact c WHERE c.tenantId = :tenantId "
          + "AND c.contactType = 'PHONE_EXTENSION' AND c.parentContactId = :parentContactId")
  List<Contact> findExtensionsByParentContactId(
      @Param("tenantId") UUID tenantId, @Param("parentContactId") UUID parentContactId);

  /** Find verified contacts by type within tenant. */
  @Query(
      "SELECT c FROM Contact c WHERE c.tenantId = :tenantId "
          + "AND c.contactType = :contactType AND c.isVerified = true")
  List<Contact> findVerifiedByTenantIdAndContactType(
      @Param("tenantId") UUID tenantId, @Param("contactType") ContactType contactType);

  /**
   * Case-insensitive partial search by contact value within tenant (max 20 results to keep it
   * lightweight).
   */
  @Query(
      "SELECT c FROM Contact c WHERE c.tenantId = :tenantId "
          + "AND LOWER(c.contactValue) LIKE LOWER(CONCAT('%', :query, '%')) "
          + "ORDER BY c.contactValue ASC")
  List<Contact> searchByTenantIdAndContactValue(
      @Param("tenantId") UUID tenantId,
      @Param("query") String query,
      org.springframework.data.domain.Pageable pageable);

  /**
   * Check if any contact exists with the given email domain. Used for providing context-aware error
   * messages during login.
   *
   * @param domain Email domain (e.g., "gmail.com", "company.com")
   * @return true if any contact with this domain exists
   */
  @Query(
      "SELECT COUNT(c) > 0 FROM Contact c "
          + "WHERE c.contactType = 'EMAIL' "
          + "AND c.contactValue LIKE CONCAT('%@', :domain)")
  boolean existsByEmailDomain(@Param("domain") String domain);
}
