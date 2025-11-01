package com.fabricmanagement.common.platform.communication.infra.repository;

import com.fabricmanagement.common.platform.communication.domain.CompanyContact;
import com.fabricmanagement.common.platform.communication.domain.CompanyContactId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for CompanyContact junction entity.
 */
@Repository
public interface CompanyContactRepository extends JpaRepository<CompanyContact, CompanyContactId> {

    /**
     * Find all contacts for a company within tenant.
     */
    @Query("SELECT cc FROM CompanyContact cc WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
    List<CompanyContact> findByTenantIdAndCompanyId(
            @Param("tenantId") UUID tenantId,
            @Param("companyId") UUID companyId);

    /**
     * Find specific company-contact assignment.
     */
    @Query("SELECT cc FROM CompanyContact cc WHERE cc.companyId = :companyId AND cc.contactId = :contactId")
    Optional<CompanyContact> findByCompanyIdAndContactId(
            @Param("companyId") UUID companyId,
            @Param("contactId") UUID contactId);

    /**
     * Find default contact for company.
     */
    @Query("SELECT cc FROM CompanyContact cc WHERE cc.companyId = :companyId AND cc.isDefault = true")
    Optional<CompanyContact> findDefaultByCompanyId(@Param("companyId") UUID companyId);

    /**
     * Find department-specific contacts for company.
     */
    @Query("SELECT cc FROM CompanyContact cc WHERE cc.companyId = :companyId AND cc.department = :department")
    List<CompanyContact> findByCompanyIdAndDepartment(
            @Param("companyId") UUID companyId,
            @Param("department") String department);
}

