package com.fabricmanagement.company.infrastructure.repository;

import com.fabricmanagement.company.domain.aggregate.Company;
import com.fabricmanagement.company.domain.valueobject.CompanyStatus;
import com.fabricmanagement.company.domain.valueobject.CompanyType;
import com.fabricmanagement.company.domain.valueobject.Industry;
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
}

