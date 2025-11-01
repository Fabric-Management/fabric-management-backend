package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.CompanyAddress;
import com.fabricmanagement.common.platform.communication.domain.CompanyAddressId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CompanyAddress junction entity.
 */
@Repository
public interface CompanyAddressRepository extends JpaRepository<CompanyAddress, CompanyAddressId> {

    /**
     * Find all addresses for a company within tenant.
     */
    @Query("SELECT ca FROM CompanyAddress ca WHERE ca.tenantId = :tenantId AND ca.companyId = :companyId")
    List<CompanyAddress> findByTenantIdAndCompanyId(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId);

    /**
     * Find specific company-address assignment.
     */
    @Query("SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.addressId = :addressId")
    Optional<CompanyAddress> findByCompanyIdAndAddressId(
            @Param("companyId") UUID companyId,
            @Param("addressId") UUID addressId);

    /**
     * Find primary address for company.
     */
    @Query("SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.isPrimary = true")
    Optional<CompanyAddress> findPrimaryByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Find headquarters address for company.
     */
    @Query("SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.isHeadquarters = true")
    Optional<CompanyAddress> findHeadquartersByCompanyId(@Param("companyId") UUID companyId);
}

