package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Company Repository
 * 
 * JPA repository for Company aggregate persistence
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    
    /**
     * Finds all non-deleted companies by tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.deleted = false")
    List<Company> findByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Finds a company by ID and tenant ID (for multi-tenancy isolation)
     */
    @Query("SELECT c FROM Company c WHERE c.id = :id AND c.tenantId = :tenantId AND c.deleted = false")
    Optional<Company> findByIdAndTenantId(@Param("id") UUID id, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds companies by status and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.status = :status AND c.tenantId = :tenantId AND c.deleted = false")
    List<Company> findByStatusAndTenantId(@Param("status") CompanyStatus status, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds companies by type and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.type = :type AND c.tenantId = :tenantId AND c.deleted = false")
    List<Company> findByTypeAndTenantId(@Param("type") CompanyType type, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds companies by industry and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.industry = :industry AND c.tenantId = :tenantId AND c.deleted = false")
    List<Company> findByIndustryAndTenantId(@Param("industry") Industry industry, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds active companies by tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.isActive = true AND c.tenantId = :tenantId AND c.deleted = false")
    List<Company> findActiveByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Checks if a company exists by name and tenant ID
     */
    @Query("SELECT COUNT(c) > 0 FROM Company c WHERE c.name.value = :name AND c.tenantId = :tenantId AND c.deleted = false")
    boolean existsByNameAndTenantId(@Param("name") String name, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds companies by name containing (search) and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE LOWER(c.name.value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) AND c.tenantId = :tenantId AND c.deleted = false")
    List<Company> searchByNameAndTenantId(@Param("searchTerm") String searchTerm, @Param("tenantId") UUID tenantId);
    
    /**
     * Counts active companies by tenant ID
     */
    @Query("SELECT COUNT(c) FROM Company c WHERE c.isActive = true AND c.tenantId = :tenantId AND c.deleted = false")
    long countActiveByTenantId(@Param("tenantId") UUID tenantId);
    
    /**
     * Finds company by tax ID and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.taxId = :taxId AND c.tenantId = :tenantId AND c.deleted = false")
    Optional<Company> findByTaxIdAndTenantId(@Param("taxId") String taxId, @Param("tenantId") UUID tenantId);
    
    /**
     * Finds company by registration number and tenant ID
     */
    @Query("SELECT c FROM Company c WHERE c.registrationNumber = :registrationNumber AND c.tenantId = :tenantId AND c.deleted = false")
    Optional<Company> findByRegistrationNumberAndTenantId(@Param("registrationNumber") String registrationNumber, @Param("tenantId") UUID tenantId);
    
    /**
     * Fuzzy search companies by name using PostgreSQL similarity
     * Uses pg_trgm extension for fuzzy matching
     * 
     * @param searchTerm The name to search for
     * @param tenantId Tenant context
     * @param similarityThreshold Minimum similarity (0.0 to 1.0), default 0.3
     * @return List of similar companies ordered by similarity score
     */
    @Query(value = 
        "SELECT c.* FROM companies c " +
        "WHERE c.tenant_id = :tenantId " +
        "AND c.deleted = false " +
        "AND (similarity(c.name, :searchTerm) > :threshold " +
        "     OR similarity(COALESCE(c.legal_name, ''), :searchTerm) > :threshold) " +
        "ORDER BY GREATEST(similarity(c.name, :searchTerm), similarity(COALESCE(c.legal_name, ''), :searchTerm)) DESC " +
        "LIMIT 10",
        nativeQuery = true)
    List<Company> findSimilarCompanies(
        @Param("tenantId") UUID tenantId,
        @Param("searchTerm") String searchTerm,
        @Param("threshold") double similarityThreshold);
    
    /**
     * Search companies for autocomplete (search-as-you-type)
     * Uses full-text search for fast results
     * 
     * @param searchTerm The partial name to search for
     * @param tenantId Tenant context
     * @param limit Maximum results to return
     * @return List of matching companies
     */
    @Query(value = 
        "SELECT c.* FROM companies c " +
        "WHERE c.tenant_id = :tenantId " +
        "AND c.deleted = false " +
        "AND c.name_search_vector @@ plainto_tsquery('simple', :searchTerm) " +
        "ORDER BY ts_rank(c.name_search_vector, plainto_tsquery('simple', :searchTerm)) DESC " +
        "LIMIT :limit",
        nativeQuery = true)
    List<Company> searchCompaniesForAutocomplete(
        @Param("tenantId") UUID tenantId,
        @Param("searchTerm") String searchTerm,
        @Param("limit") int limit);
    
    /**
     * Find companies that may be duplicates based on tax ID or registration number
     * Used before creating a new company
     */
    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId " +
           "AND c.deleted = false " +
           "AND (c.taxId = :taxId OR c.registrationNumber = :registrationNumber)")
    List<Company> findPotentialDuplicates(
        @Param("tenantId") UUID tenantId,
        @Param("taxId") String taxId,
        @Param("registrationNumber") String registrationNumber);
    
    // ===== Paginated Query Methods =====
    
    /**
     * Finds all non-deleted companies by tenant ID with pagination
     * 
     * @param tenantId Tenant context
     * @param pageable Pagination parameters (page, size, sort)
     * @return Page of companies
     */
    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.deleted = false")
    Page<Company> findByTenantIdAndDeletedFalse(@Param("tenantId") UUID tenantId, Pageable pageable);
    
    /**
     * Finds companies by status and tenant ID with pagination
     * 
     * @param status Company status filter
     * @param tenantId Tenant context
     * @param pageable Pagination parameters
     * @return Page of companies
     */
    @Query("SELECT c FROM Company c WHERE c.status = :status AND c.tenantId = :tenantId AND c.deleted = false")
    Page<Company> findByStatusAndTenantId(
        @Param("status") CompanyStatus status, 
        @Param("tenantId") UUID tenantId, 
        Pageable pageable);
    
    /**
     * Search companies by name with pagination
     * 
     * @param searchTerm Search keyword
     * @param tenantId Tenant context
     * @param pageable Pagination parameters
     * @return Page of matching companies
     */
    @Query("SELECT c FROM Company c WHERE LOWER(c.name.value) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "AND c.tenantId = :tenantId AND c.deleted = false")
    Page<Company> searchByNameAndTenantIdPaginated(
        @Param("searchTerm") String searchTerm, 
        @Param("tenantId") UUID tenantId, 
        Pageable pageable);
}

