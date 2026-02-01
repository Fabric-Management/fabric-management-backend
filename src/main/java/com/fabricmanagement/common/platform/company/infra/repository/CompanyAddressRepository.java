package com.fabricmanagement.common.platform.company.infra.repository;

import com.fabricmanagement.common.platform.company.domain.CompanyAddress;
import com.fabricmanagement.common.platform.company.domain.CompanyAddressId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Repository for CompanyAddress junction entity (Company module). */
@Repository
public interface CompanyAddressRepository extends JpaRepository<CompanyAddress, CompanyAddressId> {

  @Query(
      "SELECT ca FROM CompanyAddress ca WHERE ca.tenantId = :tenantId AND ca.companyId = :companyId")
  List<CompanyAddress> findByTenantIdAndCompanyId(
      @Param("tenantId") UUID tenantId, @Param("companyId") UUID companyId);

  @Query(
      "SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.addressId = :addressId")
  Optional<CompanyAddress> findByCompanyIdAndAddressId(
      @Param("companyId") UUID companyId, @Param("addressId") UUID addressId);

  @Query("SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.isPrimary = true")
  Optional<CompanyAddress> findPrimaryByCompanyId(@Param("companyId") UUID companyId);

  @Query(
      "SELECT ca FROM CompanyAddress ca WHERE ca.companyId = :companyId AND ca.isHeadquarters = true")
  Optional<CompanyAddress> findHeadquartersByCompanyId(@Param("companyId") UUID companyId);
}
