package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.Company;
import com.fabricmanagement.common.platform.company.domain.CompanyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Company entity.
 *
 * <p>All queries are tenant-scoped for multi-tenant isolation.</p>
 */
@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Company> findByTenantIdAndUid(UUID tenantId, String uid);

    Optional<Company> findByTaxId(String taxId);

    List<Company> findByTenantIdAndIsActiveTrue(UUID tenantId);

    List<Company> findByTenantIdAndCompanyType(UUID tenantId, CompanyType companyType);

    List<Company> findByTenantIdAndParentCompanyId(UUID tenantId, UUID parentCompanyId);

    boolean existsByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTaxId(String taxId);

    boolean existsByTenantIdAndTaxId(UUID tenantId, String taxId);

    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.companyName LIKE %:name%")
    List<Company> searchByName(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.companyType IN :types")
    List<Company> findByTenantIdAndCompanyTypeIn(@Param("tenantId") UUID tenantId, @Param("types") List<CompanyType> types);

    /**
     * Find all root tenant companies (where id = tenantId).
     * Used by platform admin to list all tenants in the system.
     * 
     * <p><b>Performance:</b> Filters at database level instead of loading all companies
     * and filtering in Java. This is critical for performance when there are many companies.</p>
     * 
     * <p><b>Note:</b> Only includes tenant-type company types (SPINNER, WEAVER, KNITTER, etc.)</p>
     * 
     * @return List of root tenant companies (active tenant-type companies)
     */
    @Query("SELECT c FROM Company c " +
           "WHERE c.id = c.tenantId " +
           "AND c.companyType IN ('SPINNER', 'WEAVER', 'KNITTER', 'DYER_FINISHER', 'VERTICAL_MILL', 'GARMENT_MANUFACTURER') " +
           "AND c.isActive = true")
    List<Company> findRootTenants();

    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    long countByTenantIdAndCompanyType(UUID tenantId, CompanyType companyType);
}

