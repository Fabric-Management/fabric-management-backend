package com.fabricmanagement.contact.infrastructure.persistence.repository;

import com.fabricmanagement.contact.infrastructure.persistence.entity.ContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.UserContactEntity;
import com.fabricmanagement.contact.infrastructure.persistence.entity.CompanyContactEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * JPA repository for contact entities.
 */
@Repository
public interface ContactJpaRepository extends JpaRepository<ContactEntity, UUID> {

    /**
     * Finds a user contact by user ID.
     */
    @Query("SELECT c FROM UserContactEntity c WHERE c.userId = :userId AND c.deleted = false")
    Optional<UserContactEntity> findByUserId(@Param("userId") UUID userId);

    /**
     * Checks if a contact exists for the given user ID.
     */
    @Query("SELECT COUNT(c) > 0 FROM UserContactEntity c WHERE c.userId = :userId AND c.deleted = false")
    boolean existsByUserId(@Param("userId") UUID userId);

    /**
     * Finds contacts by tenant ID and entity type.
     */
    @Query("SELECT c FROM UserContactEntity c WHERE c.tenantId = :tenantId AND TYPE(c) = UserContactEntity AND c.deleted = false")
    Page<UserContactEntity> findByTenantIdAndEntityType(@Param("tenantId") UUID tenantId, @Param("entityType") String entityType, Pageable pageable);

    /**
     * Searches user contacts by query string.
     */
    @Query("SELECT c FROM UserContactEntity c WHERE " +
           "(LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.displayName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.jobTitle) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.department) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND c.deleted = false")
    Page<UserContactEntity> searchUserContacts(@Param("query") String query, Pageable pageable);

    /**
     * Finds all contacts by tenant ID.
     */
    @Query("SELECT c FROM ContactEntity c WHERE c.tenantId = :tenantId AND c.deleted = false")
    Page<ContactEntity> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    /**
     * Finds contacts by status.
     */
    @Query("SELECT c FROM ContactEntity c WHERE c.status = :status AND c.deleted = false")
    Page<ContactEntity> findByStatus(@Param("status") String status, Pageable pageable);

    /**
     * Finds a company contact by company ID.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.companyId = :companyId AND c.deleted = false")
    Optional<CompanyContactEntity> findByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Checks if a contact exists for the given company ID.
     */
    @Query("SELECT COUNT(c) > 0 FROM CompanyContactEntity c WHERE c.companyId = :companyId AND c.deleted = false")
    boolean existsByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Finds company contacts by industry.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE c.industry = :industry AND c.deleted = false")
    Page<CompanyContactEntity> findByIndustry(@Param("industry") String industry, Pageable pageable);

    /**
     * Searches company contacts by query string.
     */
    @Query("SELECT c FROM CompanyContactEntity c WHERE " +
           "(LOWER(c.companyName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.industry) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(c.mainContactPerson) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND c.deleted = false")
    Page<CompanyContactEntity> searchCompanyContacts(@Param("query") String query, Pageable pageable);
}