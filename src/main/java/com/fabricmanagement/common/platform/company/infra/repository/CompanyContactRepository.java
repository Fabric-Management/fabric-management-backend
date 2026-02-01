package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.CompanyContact;
import com.fabricmanagement.common.platform.company.domain.CompanyContactId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for CompanyContact junction entity (Company module). */
@Repository
public interface CompanyContactRepository extends JpaRepository<CompanyContact, CompanyContactId> {

  @Query(
      "SELECT cc FROM CompanyContact cc "
          + "LEFT JOIN FETCH cc.contact "
          + "WHERE cc.tenantId = :tenantId AND cc.companyId = :companyId")
  List<CompanyContact> findByTenantIdAndCompanyId(
      @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

  @Query(
      "SELECT cc FROM CompanyContact cc "
          + "LEFT JOIN FETCH cc.contact "
          + "WHERE cc.companyId = :companyId AND cc.contactId = :contactId")
  Optional<CompanyContact> findByCompanyIdAndContactId(
      @Param("companyId") UUID companyId, @Param("contactId") UUID contactId);

  @Query(
      "SELECT cc FROM CompanyContact cc "
          + "LEFT JOIN FETCH cc.contact "
          + "WHERE cc.companyId = :companyId AND cc.isDefault = true")
  Optional<CompanyContact> findDefaultByCompanyId(@Param("companyId") UUID companyId);

  @Query(
      "SELECT cc FROM CompanyContact cc "
          + "LEFT JOIN FETCH cc.contact "
          + "WHERE cc.companyId = :companyId AND cc.department = :department")
  List<CompanyContact> findByCompanyIdAndDepartment(
      @Param("companyId") UUID companyId, @Param("department") String department);
}
