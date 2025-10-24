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

    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.companyName LIKE %:name%")
    List<Company> searchByName(@Param("tenantId") UUID tenantId, @Param("name") String name);

    @Query("SELECT c FROM Company c WHERE c.tenantId = :tenantId AND c.companyType IN :types")
    List<Company> findByTenantIdAndCompanyTypeIn(@Param("tenantId") UUID tenantId, @Param("types") List<CompanyType> types);

    long countByTenantIdAndIsActiveTrue(UUID tenantId);

    long countByTenantIdAndCompanyType(UUID tenantId, CompanyType companyType);
}

